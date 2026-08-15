package com.waymark.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.DestinationInsights
import com.waymark.data.repo.FlightRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.logic.IdeaSection
import com.waymark.domain.logic.IdeaTally
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.SplitWindow
import com.waymark.domain.logic.TimelineBuilder
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.model.BoardingPass
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Reservation
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

enum class TripTab(val label: String) {
    TIMELINE("Timeline"),
    IDEAS("Ideas"),
    MAP("Map"),
    PARTY("Party"),
    VAULT("Vault"),
}

data class TripUiState(
    val dossier: TripDossier? = null,
    val timeline: List<TimelineEntry> = emptyList(),
    val splits: List<SplitWindow> = emptyList(),
    val partyNotes: List<String> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val passes: List<BoardingPass> = emptyList(),
    val alerts: List<DisruptionAlert> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val ideaSections: List<IdeaSection> = emptyList(),
    val ideaTally: IdeaTally = IdeaTally(0, 0, 0, 0),
    val suggestions: List<Idea> = emptyList(),
    val travelerFilter: String? = null,
    val refreshing: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val insight: DestinationInsights.Insight?
        get() = dossier?.segments
            ?.asSequence()
            ?.mapNotNull { segment ->
                DestinationInsights.forCity(segment.destination.city)
                    ?: DestinationInsights.forAirport(segment.destination.code)
            }
            ?.lastOrNull()

    val flights: List<Segment.Flight>
        get() = dossier?.segments?.filterIsInstance<Segment.Flight>().orEmpty()

    /** Every city this trip touches, in visiting order — the guide's key. */
    val cities: List<String>
        get() = dossier?.segments
            ?.sortedBy { it.startEpochMillis }
            ?.flatMap { listOf(it.destination.city, it.origin.city) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
}

/**
 * One view model behind all five tabs: they are views of the same dossier, and
 * splitting them would mean five subscriptions to the same tables.
 */
class TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val vault: VaultRepository,
    private val flights: FlightRepository,
    private val ideas: IdeaRepository,
) : ViewModel() {

    private val travelerFilter = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val tabState = MutableStateFlow(TripTab.TIMELINE)

    val tab: StateFlow<TripTab> = tabState.asStateFlow()

    /** Drives the "now" line and the relative times; one tick a minute is plenty. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60_000)
        }
    }

    /**
     * Filter, refresh flag, clock and the idea list, folded into one flow so
     * the state combine stays within the five-source arity.
     */
    private data class Ambient(
        val filter: String?,
        val refreshing: Boolean,
        val now: Long,
        val ideas: List<Idea>,
    )

    private val ambient = combine(
        travelerFilter,
        refreshing,
        clock,
        ideas.observe(tripId),
    ) { filter, isRefreshing, now, ideaList -> Ambient(filter, isRefreshing, now, ideaList) }

    val state: StateFlow<TripUiState> = combine(
        trips.observeDossier(tripId),
        vault.observeReservations(tripId),
        vault.observePasses(tripId),
        flights.observeAlerts(),
        ambient,
    ) { dossier, reservations, passes, alerts, current ->
        val instant = Instant.ofEpochMilli(current.now)
        val cities = dossier?.segments
            ?.sortedBy { it.startEpochMillis }
            ?.flatMap { listOf(it.destination.city, it.origin.city) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        TripUiState(
            dossier = dossier,
            timeline = dossier?.let {
                TimelineBuilder.build(it, travelerFilter = current.filter, now = instant)
            }.orEmpty(),
            splits = dossier?.let(PartySplitAnalyzer::splitWindows).orEmpty(),
            partyNotes = dossier?.let(PartySplitAnalyzer::warnings).orEmpty(),
            reservations = reservations,
            passes = passes,
            alerts = alerts.filter { alert ->
                dossier?.segments?.any { it.id == alert.segmentId } == true
            },
            ideas = current.ideas,
            ideaSections = IdeaBoard.sections(
                ideas = current.ideas,
                travelerFilter = current.filter,
            ),
            ideaTally = IdeaBoard.tally(current.ideas),
            suggestions = ideas.suggestionsFor(tripId, cities, current.ideas),
            travelerFilter = current.filter,
            refreshing = current.refreshing,
            nowMillis = current.now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    init {
        refresh()
    }

    fun selectTab(next: TripTab) {
        tabState.value = next
    }

    fun filterBy(travelerId: String?) {
        travelerFilter.update { current -> if (current == travelerId) null else travelerId }
    }

    /** Pull live state for this trip's flights. Safe to call often. */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                val segments = state.value.dossier?.segments
                    ?: trips.observeSegments(tripId).first()
                flights.refresh(segments.filterIsInstance<Segment.Flight>())
            } finally {
                refreshing.value = false
            }
        }
    }

    fun acknowledgeAlerts(segmentId: String) {
        viewModelScope.launch { flights.acknowledge(segmentId) }
    }

    fun setTravelers(segmentId: String, travelerIds: Set<String>) {
        viewModelScope.launch { trips.setTravelers(segmentId, travelerIds) }
    }

    fun deleteSegment(segmentId: String) {
        viewModelScope.launch { trips.deleteSegment(segmentId) }
    }

    fun addTraveler(fullName: String, nickname: String?) {
        if (fullName.isBlank()) return
        viewModelScope.launch {
            trips.addTraveler(
                tripId = tripId,
                traveler = com.waymark.domain.model.Traveler(
                    id = TripRepository.newId("trav"),
                    fullName = fullName.trim(),
                    nickname = nickname?.trim()?.ifBlank { null },
                ),
            )
        }
    }

    fun removeTraveler(travelerId: String) {
        viewModelScope.launch { trips.removeTraveler(tripId, travelerId) }
    }

    // — Ideas ————————————————————————————————————————————————————————————

    /** Take a bundled suggestion onto the trip's own list. */
    fun adopt(suggestion: Idea) {
        viewModelScope.launch { ideas.adopt(suggestion) }
    }

    fun addIdea(title: String, kind: IdeaKind, city: String, note: String?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            ideas.add(
                tripId = tripId,
                title = title,
                kind = kind,
                city = city.ifBlank { state.value.cities.firstOrNull().orEmpty() },
                note = note,
            )
        }
    }

    fun setIdeaStatus(ideaId: String, status: IdeaStatus) {
        viewModelScope.launch { ideas.setStatus(ideaId, status) }
    }

    fun toggleInterest(ideaId: String, travelerId: String) {
        viewModelScope.launch { ideas.setInterest(ideaId, travelerId) }
    }

    fun deleteIdea(ideaId: String) {
        viewModelScope.launch { ideas.delete(ideaId) }
    }

    /**
     * Put an idea on the timeline. The trip's own zone is used unless the idea
     * carries a place with one of its own — a museum keeps its city's clock.
     */
    fun scheduleIdea(
        idea: Idea,
        date: java.time.LocalDate,
        startTime: java.time.LocalTime,
        minutesOverride: Int? = null,
    ) {
        viewModelScope.launch {
            val zone = Segment.zoneOrUtc(
                idea.place?.timeZoneId
                    ?: state.value.dossier?.trip?.homeZoneId
                    ?: java.time.ZoneId.systemDefault().id
            )
            ideas.schedule(
                idea = idea,
                date = date,
                startTime = startTime,
                zone = zone,
                travelerIds = idea.interestedTravelerIds,
                minutesOverride = minutesOverride,
            )
        }
    }

    fun reservationFor(segmentId: String?): Reservation? =
        state.value.reservations.firstOrNull { it.segmentId != null && it.segmentId == segmentId }

    fun passesFor(segmentId: String): List<BoardingPass> =
        state.value.passes.filter { it.segmentId == segmentId }
}
