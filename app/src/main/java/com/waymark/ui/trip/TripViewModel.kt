package com.waymark.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.DestinationInsights
import com.waymark.data.repo.FlightRepository
import com.waymark.data.catalog.TripFacts
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.PreparationRepository
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository
import com.waymark.domain.logic.DayPlan
import com.waymark.domain.logic.DocumentVerdict
import com.waymark.domain.logic.DocumentWatch
import com.waymark.domain.logic.CityGroup
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.logic.IdeaSection
import com.waymark.domain.logic.IdeaTally
import com.waymark.domain.logic.PackingPlanner
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.SplitWindow
import com.waymark.domain.logic.TimelineBuilder
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.logic.TripAnalytics
import com.waymark.domain.logic.TripAnalyticsReport
import com.waymark.domain.model.BoardingPass
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.PackingItem
import com.waymark.domain.model.PackingProgress
import com.waymark.domain.model.Reservation
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TravelDocument
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
import java.time.LocalDate

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
    val ideasByCity: List<CityGroup> = emptyList(),
    val dayPlan: DayPlan = DayPlan(emptyList(), emptyList()),
    val documents: List<DocumentVerdict> = emptyList(),
    val missingPassportFor: List<String> = emptyList(),
    val packing: List<PackingItem> = emptyList(),
    val packingProgress: List<PackingProgress> = emptyList(),
    val analytics: TripAnalyticsReport? = null,
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
    private val preparations: PreparationRepository,
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
        val documents: List<TravelDocument>,
        val packing: List<PackingItem>,
    )

    private val preparation = combine(
        preparations.observeDocuments(),
        preparations.observePacking(tripId),
    ) { documents, packing -> documents to packing }

    private val ambient = combine(
        travelerFilter,
        refreshing,
        clock,
        ideas.observe(tripId),
        preparation,
    ) { filter, isRefreshing, now, ideaList, (documents, packing) ->
        Ambient(filter, isRefreshing, now, ideaList, documents, packing)
    }

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
            ideasByCity = IdeaBoard.byCity(current.ideas, cityOrder = cities),
            dayPlan = IdeaBoard.byDay(current.ideas),
            documents = partyDocuments(dossier, current.documents, instant),
            missingPassportFor = dossier?.party?.travelers
                ?.map { it.id }
                ?.let { DocumentWatch.travelersMissingPassport(it, current.documents) }
                .orEmpty(),
            packing = current.packing,
            packingProgress = packingProgress(dossier, current.packing),
            analytics = dossier?.let { TripAnalytics.report(it, current.ideas) },
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

    // — Documents and packing —————————————————————————————————————————————

    fun addDocument(
        travelerId: String,
        kind: com.waymark.domain.model.DocumentKind,
        label: String,
        number: String,
        issuer: String?,
        expiresOn: LocalDate?,
        note: String?,
    ) {
        if (number.isBlank()) return
        viewModelScope.launch {
            preparations.addDocument(
                travelerId = travelerId,
                kind = kind,
                label = label,
                number = number,
                issuer = issuer,
                issuedOn = null,
                expiresOn = expiresOn,
                note = note,
            )
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch { preparations.deleteDocument(id) }
    }

    fun setPacked(itemId: String, packed: Boolean) {
        viewModelScope.launch { preparations.setPacked(itemId, packed) }
    }

    fun addPackingItem(
        travelerId: String?,
        title: String,
        category: PackingCategory,
        essential: Boolean,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            preparations.addPackingItem(
                tripId = tripId,
                travelerId = travelerId,
                title = title,
                category = category,
                essential = essential,
            )
        }
    }

    fun deletePackingItem(itemId: String) {
        viewModelScope.launch { preparations.deletePackingItem(itemId) }
    }

    fun unpackEverything() {
        viewModelScope.launch { preparations.unpackAll(tripId) }
    }

    /**
     * Draft a list from the itinerary for one traveler: counts scaled to the
     * nights, an adaptor only where the sockets differ, a swimsuit only where
     * something involves water.
     */
    fun suggestPacking(travelerId: String?) {
        viewModelScope.launch {
            val current = state.value
            val dossier = current.dossier ?: return@launch
            preparations.applySuggestions(
                context = TripFacts.packingContext(dossier, current.ideas),
                travelerId = travelerId,
                existing = current.packing,
            )
        }
    }

    fun pencilIdeaFor(ideaId: String, date: LocalDate?) {
        viewModelScope.launch { ideas.setPlannedDay(ideaId, date) }
    }

    /** The trip's own days, for the day-planner chips. */
    fun tripDays(): List<LocalDate> {
        val trip = state.value.dossier?.trip ?: return emptyList()
        val days = mutableListOf<LocalDate>()
        var day = trip.startDate()
        while (!day.isAfter(trip.endDate()) && days.size < 60) {
            days += day
            day = day.plusDays(1)
        }
        return days
    }

    private fun partyDocuments(
        dossier: TripDossier?,
        documents: List<TravelDocument>,
        now: Instant,
    ): List<DocumentVerdict> {
        val partyIds = dossier?.party?.travelers?.map { it.id }?.toSet() ?: return emptyList()
        val zone = com.waymark.domain.model.Segment.zoneOrUtc(dossier.trip.homeZoneId)
        return DocumentWatch.assessAll(
            documents = documents.filter { it.travelerId in partyIds },
            tripEnd = dossier.trip.endDate(),
            today = now.atZone(zone).toLocalDate(),
        )
    }

    private fun packingProgress(
        dossier: TripDossier?,
        items: List<PackingItem>,
    ): List<PackingProgress> {
        val travelers = dossier?.party?.travelers.orEmpty()
        return buildList {
            add(PackingPlanner.progress(items, null, "Shared"))
            travelers.forEach { traveler ->
                add(PackingPlanner.progress(items, traveler.id, traveler.displayName))
            }
        }.filter { it.total > 0 || it.travelerId != null }
    }

    fun reservationFor(segmentId: String?): Reservation? =
        state.value.reservations.firstOrNull { it.segmentId != null && it.segmentId == segmentId }

    fun passesFor(segmentId: String): List<BoardingPass> =
        state.value.passes.filter { it.segmentId == segmentId }
}
