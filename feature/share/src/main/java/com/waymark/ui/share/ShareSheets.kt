package com.waymark.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import com.waymark.data.share.BookingText
import com.waymark.data.share.TripArchive
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Trip
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.export.TripShare
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/** Trips from a file or a code, shown before anything is written. */
@Composable
fun ImportModal(
    archive: ShareViewModel.Incoming.Archive,
    busy: Boolean,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Waymark.colors
    val count = archive.bundles.size
    WaymarkModal(
        title = if (count == 1) archive.bundles.single().trip.name else "$count trips",
        eyebrow = "Open in Waymark",
        onDismiss = onDismiss,
    ) {
        archive.bundles.forEachIndexed { index, bundle ->
            if (index > 0) Hairline()
            Text(text = bundle.trip.name, style = Waymark.type.bodySmall, color = colors.textBody)
            Text(
                text = listOf(
                    TimeText.dateRange(bundle.trip.startDate(), bundle.trip.endDate()),
                    plural(bundle.segments.size, "booking"),
                    plural(bundle.travelers.size, "traveler"),
                ).joinToString(" · "),
                style = Waymark.type.hint,
                color = colors.textDim,
            )
            if (bundle.trip.id in archive.replacing) {
                Text(
                    text = "Replaces the copy already on this phone",
                    style = Waymark.type.hint,
                    color = colors.accentAmber,
                )
            }
        }
        Spacer(Modifier.height(WaymarkSpacing.snug))
        Footnote("Opening a trip that is already here replaces it with this copy, bookings and all.")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = if (archive.replacing.isEmpty()) "Add" else "Replace",
                icon = WaymarkIcons.Download,
                onClick = onImport,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Flights found in a shared email. Complete ones can be added in one go —
 * this sheet is where they are checked — and any one can be opened in Add
 * flight instead, to look it over or fill in what the email left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedFlightsModal(
    flights: ShareViewModel.Incoming.Flights,
    trips: List<Trip>,
    busy: Boolean,
    onAddAll: (tripId: String) -> Unit,
    onCheck: (tripId: String, BookingText.FoundFlight) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Waymark.colors
    val found = flights.reading.flights
    var tripId by rememberSaveable { mutableStateOf(trips.firstOrNull()?.id) }
    LaunchedEffect(trips) { if (tripId == null || trips.none { it.id == tripId }) tripId = trips.firstOrNull()?.id }
    val complete = found.count { it.complete }

    WaymarkModal(
        title = if (found.size == 1) "One flight" else "${found.size} flights",
        eyebrow = flights.reading.reference?.let { "Booking $it" } ?: "From the text",
        onDismiss = onDismiss,
    ) {
        found.forEachIndexed { index, flight ->
            if (index > 0) Hairline()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = describe(flight),
                    style = Waymark.type.bodySmall,
                    color = colors.textBody,
                    modifier = Modifier.weight(1f),
                )
                tripId?.let { id ->
                    OptionChip(
                        text = if (flight.complete) "Check" else "Finish",
                        selected = false,
                        onClick = { onCheck(id, flight) },
                    )
                }
            }
            if (!flight.complete) {
                Text(text = "Some of it is missing", style = Waymark.type.hint, color = colors.textDim)
            }
        }

        Spacer(Modifier.height(WaymarkSpacing.snug))
        if (trips.isEmpty()) {
            Footnote("Make a trip for these flights first; they are added to a trip, not on their own.")
        } else {
            Text(text = "Add to", style = Waymark.type.hint, color = colors.textDim)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            ) {
                trips.forEach { trip ->
                    OptionChip(text = trip.name, selected = trip.id == tripId, onClick = { tripId = trip.id })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            if (complete > 0) {
                PrimaryButton(
                    text = if (complete == 1) "Add it" else "Add $complete",
                    icon = WaymarkIcons.Plus,
                    onClick = { tripId?.let(onAddAll) },
                    enabled = tripId != null && !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun ProblemModal(problem: ShareViewModel.Incoming.Problem, onDismiss: () -> Unit) {
    WaymarkModal(title = problem.headline, onDismiss = onDismiss) {
        Text(text = problem.detail, style = Waymark.type.body, color = Waymark.colors.textBody)
        MutedButton(text = "Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The trip as a QR code, for Waymark on a phone across the table.
 *
 * The code carries as much of the trip as fits — everything if it can, then
 * without the ideas, then without long notes — and says what it left out.
 * Too big even for that, it says to send the file instead.
 */
@Composable
fun TripCodeModal(state: TripShare, onShareFile: () -> Unit, onDismiss: () -> Unit) {
    val dossier = state.dossier ?: return
    val code = remember(dossier, state.ideas) {
        val bundle = TripArchive.TripBundle(dossier.trip, dossier.party.travelers, dossier.segments, state.ideas)
        TripArchive.toCode(TripArchive.archiveOf(listOf(bundle)))
    }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(code) {
        image = code?.let { Barcodes.qr("$CODE_PREFIX${it.payload}", size = 900) }
    }

    WaymarkModal(title = dossier.trip.name, eyebrow = "Trip code", onDismiss = onDismiss) {
        if (code == null) {
            Text(
                text = "This trip is too big for a code. Send the trip file instead — it carries everything.",
                style = Waymark.type.body,
                color = Waymark.colors.textBody,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(WaymarkShapes.panel)
                    .background(Color.White)
                    .padding(WaymarkSpacing.small),
                contentAlignment = Alignment.Center,
            ) {
                image?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "QR code for ${dossier.trip.name}",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
            }
            Footnote(
                "Scan it with the other phone's camera, or save a picture of it and open that from " +
                    "Waymark's trip list." + (code.detail.omits?.let { " Leaves out $it to fit." } ?: "")
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = "Close", onClick = onDismiss, modifier = Modifier.weight(1f))
            MutedButton(
                text = "Send the file",
                icon = WaymarkIcons.Share,
                onClick = onShareFile,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** `BA286 · SFO → LHR · Thu 14 May · 16:20–10:55`, with gaps where the email was silent. */
private fun describe(flight: BookingText.FoundFlight): String = listOfNotNull(
    flight.designator,
    "${flight.origin ?: "?"} → ${flight.destination ?: "?"}",
    flight.date?.let(TimeText::dayCompact),
    flight.departs?.let { departs ->
        TimeText.clock(departs) + (flight.arrives?.let { "–" + TimeText.clock(it) } ?: "")
    },
).joinToString(" · ")

private fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

/** What a trip code says: a link the phone's camera hands to Waymark. */
const val CODE_PREFIX = "waymark://import?d="
