package com.waymark.ui.share

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.repo.TripRepository
import com.waymark.data.share.BookingText
import com.waymark.data.share.TripArchive
import com.waymark.data.share.WaymarkArchive
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything that arrives from outside a trip and has to be looked at before
 * it is written: a trip file or a backup, a trip code, an email with flights
 * in it. Each becomes an [Incoming] for a sheet to show; nothing reaches the
 * database until the traveler confirms it there.
 */
class ShareViewModel(private val trips: TripRepository) : ViewModel() {

    sealed interface Incoming {
        /** Trips from a file or a code; [replacing] are the ones already on this phone. */
        data class Archive(val bundles: List<TripArchive.TripBundle>, val replacing: Set<String>) : Incoming

        /** Flights read out of shared text. */
        data class Flights(val reading: BookingText.Reading) : Incoming

        data class Problem(val headline: String, val detail: String) : Incoming
    }

    private val mutable = MutableStateFlow<Incoming?>(null)
    val incoming: StateFlow<Incoming?> = mutable.asStateFlow()

    private val working = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = working.asStateFlow()

    /** Trips a shared flight could be added to: the ones not yet over and not put away. */
    val openTrips: StateFlow<List<Trip>> = trips.observeTrips()
        .map { all ->
            val now = Instant.now()
            all.filter { !it.archived && it.status(now) != TripStatus.PAST }.sortedBy { it.startEpochMillis }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismiss() {
        mutable.value = null
    }

    /** A file opened with Waymark or picked from the restore sheet. */
    fun openFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { readCapped(context, uri) }.getOrNull() }
            if (text == null) {
                mutable.value = Incoming.Problem(
                    "That file could not be opened",
                    "It may be too large, or no longer there.",
                )
            } else {
                openArchive { TripArchive.decode(text) }
            }
        }
    }

    /** What a trip's QR code carries, from a scanned link or a picture of the code. */
    fun openCode(payload: String) {
        viewModelScope.launch { openArchive { TripArchive.fromCode(payload) } }
    }

    /**
     * Text shared into the app. A trip link is opened as a trip; anything
     * else is read for flights.
     */
    fun openText(text: String) {
        CODE_LINK.find(text)?.let { match ->
            openCode(match.groupValues[1])
            return
        }
        viewModelScope.launch {
            val reading = withContext(Dispatchers.Default) { BookingText.read(text, LocalDate.now()) }
            mutable.value = if (reading.flights.isEmpty()) {
                Incoming.Problem(
                    "No flights found in that",
                    "Waymark looks for a flight number with airport codes beside it — \"BA 286 SFO to LHR\".",
                )
            } else {
                Incoming.Flights(reading)
            }
        }
    }

    /** Write the trips being shown; [onDone] gets the id of the first. */
    fun importArchive(onDone: (String?) -> Unit) {
        val archive = mutable.value as? Incoming.Archive ?: return
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            trips.importBundles(archive.bundles)
            working.value = false
            mutable.value = null
            onDone(archive.bundles.firstOrNull()?.trip?.id)
        }
    }

    /** Save every complete flight on the sheet to [tripId] in one go. */
    fun addFlights(tripId: String, onDone: () -> Unit) {
        val flights = mutable.value as? Incoming.Flights ?: return
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            val segments = flights.reading.flights.mapNotNull { found ->
                BookingText.toSegment(found, tripId, TripRepository.newId("seg"), flights.reading.reference)
            }
            trips.saveSegments(segments)
            working.value = false
            mutable.value = null
            onDone()
        }
    }

    private suspend fun openArchive(decode: () -> WaymarkArchive) {
        mutable.value = try {
            val bundles = withContext(Dispatchers.Default) { TripArchive.bundlesOf(decode()) }
            if (bundles.isEmpty()) {
                Incoming.Problem("Nothing to open", "That file has no trips in it.")
            } else {
                Incoming.Archive(bundles, trips.existingTripIds(bundles.map { it.trip.id }))
            }
        } catch (error: TripArchive.InvalidArchive) {
            Incoming.Problem("That is not a trip Waymark can open", error.message.orEmpty())
        }
    }

    private fun readCapped(context: Context, uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                // A backup of years of trips is a few hundred kilobytes; a file
                // much bigger than that is not one.
                if (out.size() > MAX_FILE_BYTES) return null
            }
            out.toString(Charsets.UTF_8.name())
        }

    private companion object {
        const val MAX_FILE_BYTES = 16 * 1024 * 1024
        val CODE_LINK = Regex("waymark://import\\?d=([A-Za-z0-9_-]+)")
    }
}
