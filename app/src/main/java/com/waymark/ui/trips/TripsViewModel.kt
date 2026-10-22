package com.waymark.ui.trips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.repo.SampleSeeder
import com.waymark.data.repo.TripRepository
import com.waymark.data.share.TripArchive
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TripsUiState(
    val upcoming: List<Trip> = emptyList(),
    val active: List<Trip> = emptyList(),
    val past: List<Trip> = emptyList(),
    /** Put away: off the shelf and quiet, but kept. */
    val archived: List<Trip> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean
        get() = loaded && upcoming.isEmpty() && active.isEmpty() && past.isEmpty() && archived.isEmpty()
}

class TripsViewModel(
    private val trips: TripRepository,
    private val seeder: SampleSeeder,
) : ViewModel() {

    val state: StateFlow<TripsUiState> = trips.observeTrips()
        .map { everything ->
            val now = Instant.now()
            val all = everything.filterNot { it.archived }
            TripsUiState(
                upcoming = all.filter { it.status(now) == TripStatus.UPCOMING }
                    .sortedBy { it.startEpochMillis },
                active = all.filter { it.status(now) == TripStatus.ACTIVE }
                    .sortedBy { it.startEpochMillis },
                past = all.filter { it.status(now) == TripStatus.PAST }
                    .sortedByDescending { it.endEpochMillis },
                archived = everything.filter { it.archived }.sortedByDescending { it.endEpochMillis },
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

    /**
     * Every trip, written to a file the traveler chose — their own copy, to
     * keep wherever they keep things, and the way to a new phone. [onDone]
     * hears how many trips went in, or null when the file could not be written.
     */
    fun backUp(context: Context, uri: Uri, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val bundles = trips.bundles()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = TripArchive.encode(TripArchive.archiveOf(bundles))
                    // "wt": truncate, so a backup written over an older, longer one is not left with its tail.
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                        ?: error("No stream")
                }.isSuccess
            }
            onDone(if (written) bundles.size else null)
        }
    }

    fun setArchived(tripId: String, archived: Boolean) {
        viewModelScope.launch { trips.setArchived(tripId, archived) }
    }

    /** Writes the worked example and opens it. Offered only on an empty shelf. */
    fun loadExample(onLoaded: (String) -> Unit) {
        viewModelScope.launch { onLoaded(seeder.seed()) }
    }
}
