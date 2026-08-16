package com.waymark.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.repo.SampleSeeder
import com.waymark.data.repo.TripRepository
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TripsUiState(
    val upcoming: List<Trip> = emptyList(),
    val active: List<Trip> = emptyList(),
    val past: List<Trip> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && upcoming.isEmpty() && active.isEmpty() && past.isEmpty()
    val total: Int get() = upcoming.size + active.size + past.size
}

class TripsViewModel(
    private val trips: TripRepository,
    private val seeder: SampleSeeder,
) : ViewModel() {

    val state: StateFlow<TripsUiState> = trips.observeTrips()
        .map { all ->
            val now = Instant.now()
            TripsUiState(
                upcoming = all.filter { it.status(now) == TripStatus.UPCOMING }
                    .sortedBy { it.startEpochMillis },
                active = all.filter { it.status(now) == TripStatus.ACTIVE }
                    .sortedBy { it.startEpochMillis },
                past = all.filter { it.status(now) == TripStatus.PAST }
                    .sortedByDescending { it.endEpochMillis },
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun createTrip(
        name: String,
        destination: String,
        start: LocalDate,
        end: LocalDate,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val id = trips.createTrip(
                name = name.trim().ifBlank { destination.trim().ifBlank { "Untitled trip" } },
                destinationSummary = destination.trim(),
                startEpochMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                endEpochMillis = end.atTime(LocalTime.of(23, 59)).atZone(zone).toInstant()
                    .toEpochMilli(),
                homeZoneId = zone.id,
            )
            onCreated(id)
        }
    }

    /** Writes the worked example and opens it. Offered only on an empty shelf. */
    fun loadExample(onLoaded: (String) -> Unit) {
        viewModelScope.launch { onLoaded(seeder.seed()) }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch { trips.deleteTrip(tripId) }
    }
}
