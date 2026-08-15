package com.waymark.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.DestinationInsights
import com.waymark.data.repo.FlightRepository
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.SplitWindow
import com.waymark.domain.logic.TimelineBuilder
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.model.BoardingPass
import com.waymark.domain.model.DisruptionAlert
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
}

/**
 * One view model behind all four tabs: they are views of the same dossier, and
 * splitting them would mean four subscriptions to the same four tables.
 */
class TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val vault: VaultRepository,
    private val flights: FlightRepository,
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

    val state: StateFlow<TripUiState> = combine(
        trips.observeDossier(tripId),
        vault.observeReservations(tripId),
        vault.observePasses(tripId),
        flights.observeAlerts(),
        combine(travelerFilter, refreshing, clock) { filter, isRefreshing, now ->
            Triple(filter, isRefreshing, now)
        },
    ) { dossier, reservations, passes, alerts, (filter, isRefreshing, now) ->
        val instant = Instant.ofEpochMilli(now)
        TripUiState(
            dossier = dossier,
            timeline = dossier?.let {
                TimelineBuilder.build(it, travelerFilter = filter, now = instant)
            }.orEmpty(),
            splits = dossier?.let(PartySplitAnalyzer::splitWindows).orEmpty(),
            partyNotes = dossier?.let(PartySplitAnalyzer::warnings).orEmpty(),
            reservations = reservations,
            passes = passes,
            alerts = alerts.filter { alert ->
                dossier?.segments?.any { it.id == alert.segmentId } == true
            },
            travelerFilter = filter,
            refreshing = isRefreshing,
            nowMillis = now,
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

    fun reservationFor(segmentId: String?): Reservation? =
        state.value.reservations.firstOrNull { it.segmentId != null && it.segmentId == segmentId }

    fun passesFor(segmentId: String): List<BoardingPass> =
        state.value.passes.filter { it.segmentId == segmentId }
}
