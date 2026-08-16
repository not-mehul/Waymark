package com.waymark.ui.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.DestinationInsights
import com.waymark.domain.logic.ConnectionRisk
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.TransitEstimator
import com.waymark.domain.model.Segment
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * One booking in full — and, until you say otherwise, **read only**.
 *
 * The traveler chips on this screen used to be live: a stray thumb while
 * scrolling could quietly add someone to a flight they were not on, and
 * nothing said it had happened. A booking is a record of something that is
 * already arranged, so it now behaves like one. Edit puts the whole screen
 * into a form with Save and Cancel; nothing is written until Save.
 */
@Composable
fun SegmentScreen(
    segmentId: String,
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val segment = state.dossier?.segments?.firstOrNull { it.id == segmentId }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editing by remember(segmentId) { mutableStateOf(false) }

    if (editing && segment != null) {
        EditSegmentScreen(
            original = segment,
            party = state.dossier?.party?.travelers.orEmpty(),
            onCancel = { editing = false },
            onSave = {
                viewModel.updateSegment(it)
                editing = false
            },
        )
        return
    }

    ScreenScaffold(
        title = segment?.title ?: "Booking",
        onBack = onBack,
        spacing = WaymarkSpacing.small,
        action = {
            if (segment != null) {
                Row {
                    GhostIconButton(
                        icon = WaymarkIcons.Pencil,
                        contentDescription = "Edit booking",
                        onClick = { editing = true },
                    )
                    GhostIconButton(
                        icon = WaymarkIcons.Trash,
                        contentDescription = "Remove booking",
                        onClick = { confirmingDelete = true },
                    )
                }
            }
        },
    ) {
        if (segment == null) {
            Footnote("This booking has been removed.")
            return@ScreenScaffold
        }

        Panel(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Departs")
                    Text(
                        text = TimeText.clock(segment.start),
                        style = Waymark.type.stat,
                        color = colors.textHeading,
                    )
                    Text(
                        text = "${TimeText.dayCompact(segment.start.toLocalDate())} · " +
                            TimeText.zoneLabel(segment.start),
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                    Text(
                        text = segment.origin.name,
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    FieldLabel("Arrives")
                    Text(
                        text = TimeText.clock(segment.end),
                        style = Waymark.type.stat,
                        color = colors.textHeading,
                    )
                    Text(
                        text = "${TimeText.dayCompact(segment.end.toLocalDate())} · " +
                            TimeText.zoneLabel(segment.end),
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                    Text(
                        text = segment.destination.name,
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.small))
            Hairline()
            Spacer(Modifier.height(WaymarkSpacing.small))

            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large)) {
                Stat(
                    value = TimeText.durationBetween(
                        segment.startEpochMillis,
                        segment.endEpochMillis,
                    ),
                    label = "Duration",
                )
                if (segment.origin.hasCoordinates && segment.destination.hasCoordinates &&
                    segment.origin.name != segment.destination.name
                ) {
                    Stat(
                        value = Geo.formatDistance(
                            Geo.distanceKm(segment.origin, segment.destination)
                        ),
                        label = "Distance",
                    )
                }
                TimeText.zoneShift(
                    segment.start.zone,
                    segment.end.zone,
                    segment.startEpochMillis,
                )?.let {
                    Stat(value = it, label = "Clock shift", emphasised = true)
                }
            }
        }

        SpecificsPanel(segment, state.dossier?.party?.travelers.orEmpty())

        val party = state.dossier?.party?.travelers.orEmpty()
        val travelling = party.filter {
            segment.travelerIds.isEmpty() || it.id in segment.travelerIds
        }
        if (party.size > 1) {
            SectionHeader("Travelling")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    travelling.forEach { PartyMark(initials = it.initials, size = 22.dp) }
                }
                Text(
                    text = if (travelling.size == party.size) {
                        "Everyone"
                    } else {
                        travelling.joinToString(", ") { it.displayName }
                    },
                    style = Waymark.type.bodySmall,
                    color = colors.textBody,
                )
            }
        }

        // The reference, in the open. It used to be a note saying that two
        // codes existed and lived in the vault, which is the one thing a person
        // standing at a desk does not need told.
        val flight = segment as? Segment.Flight
        val tickets = flight?.ticketNumbers
            ?.filterKeys { id -> party.any { it.id == id } }
            .orEmpty()

        if (!segment.confirmationCode.isNullOrBlank() || tickets.isNotEmpty()) {
            SectionHeader("Reference")
            Panel(modifier = Modifier.fillMaxWidth()) {
                segment.confirmationCode?.takeIf { it.isNotBlank() }?.let { code ->
                    FieldLabel(segment.bookedWith?.takeIf { it.isNotBlank() } ?: "Confirmation")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = code,
                        style = Waymark.type.dataLarge,
                        color = colors.textStrong,
                    )
                }
                tickets.forEach { (travelerId, number) ->
                    val owner = party.firstOrNull { it.id == travelerId }
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    FieldLabel("${owner?.displayName ?: "Ticket"} · ticket")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = number,
                        style = Waymark.type.data,
                        color = colors.textStrong,
                    )
                }
            }
        }

        ConnectionPanel(segment, state.dossier?.segments.orEmpty())

        val insight = DestinationInsights.forAirport(segment.destination.code)
            ?: DestinationInsights.forCity(segment.destination.city)
        insight?.let {
            Footnote(
                "${it.city}: ${it.currency} · ${it.plugTypes} · " +
                    "emergency ${it.emergencyNumber}"
            )
        }
    }

    if (confirmingDelete && segment != null) {
        WaymarkModal(
            title = "Remove this booking",
            eyebrow = "Confirm",
            onDismiss = { confirmingDelete = false },
        ) {
            Text(
                text = "${segment.title} will be removed from the itinerary, " +
                    "along with its reference.",
                style = Waymark.type.body,
                color = Waymark.colors.textBody,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                MutedButton(
                    text = "Keep",
                    onClick = { confirmingDelete = false },
                    modifier = Modifier.weight(1f),
                )
                MutedButton(
                    text = "Remove",
                    icon = WaymarkIcons.Trash,
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteSegment(segment.id)
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SpecificsPanel(
    segment: Segment,
    travelers: List<com.waymark.domain.model.Traveler>,
) {
    val colors = Waymark.colors
    val rows: List<Pair<String, String>> = when (segment) {
        is Segment.Flight -> buildList {
            add("Flight" to segment.designator)
            segment.operatedBy?.let { add("Operated by" to it) }
            segment.aircraft?.let { add("Aircraft" to it) }
            segment.cabin?.let { add("Cabin" to it) }
            segment.departureTerminal?.let { add("Departure terminal" to it) }
            segment.arrivalTerminal?.let { add("Arrival terminal" to it) }
            segment.seats.forEach { (travelerId, seat) ->
                val who = travelers.firstOrNull { it.id == travelerId }?.displayName ?: "Seat"
                add("$who's seat" to seat)
            }
        }

        is Segment.Lodging -> buildList {
            add("Nights" to segment.nights.toString())
            segment.roomDescription?.let { add("Room" to it) }
            segment.origin.address?.let { add("Address" to it) }
            segment.checkInNote?.let { add("Check-in" to it) }
        }

        is Segment.Ground -> buildList {
            add("Mode" to segment.mode.label)
            segment.provider?.let { add("Operator" to it) }
            segment.pickupInstruction?.let { add("Pick-up" to it) }
        }

        is Segment.Experience -> buildList {
            add("Category" to segment.category)
            segment.origin.address?.let { add("Address" to it) }
            segment.curatedBy?.let { add("Notes" to it) }
        }
    }
    if (rows.isEmpty()) return

    Panel(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(Modifier.height(WaymarkSpacing.small))
            FieldLabel(label)
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = Waymark.type.bodySmall, color = colors.textBody)
        }
        segment.note?.let {
            Spacer(Modifier.height(WaymarkSpacing.small))
            Text(text = it, style = Waymark.type.hint, color = colors.textDim)
        }
    }
}

/** What happens either side of this booking, and whether it actually works. */
@Composable
private fun ConnectionPanel(segment: Segment, all: List<Segment>) {
    val colors = Waymark.colors
    val ordered = all.sortedBy { it.startEpochMillis }
    val index = ordered.indexOfFirst { it.id == segment.id }
    if (index <= 0) return
    val previous = ordered[index - 1]
    if (previous is Segment.Lodging || segment is Segment.Lodging) return

    val gapMinutes = ((segment.startEpochMillis - previous.endEpochMillis) / 60_000L).toInt()
    val verdict = if (previous is Segment.Flight && segment is Segment.Flight) {
        ConnectionRisk.assess(inbound = previous, onward = segment)
    } else {
        ConnectionRisk.assessGap(previous.destination, segment.origin, gapMinutes)
    }

    SectionHeader("Getting here")
    NoticeBanner(
        icon = WaymarkIcons.Clock,
        headline = "${verdict.level.label} · ${TimeText.duration(verdict.availableMinutes)} " +
            "from ${previous.title}",
        detail = verdict.reason + " · needs ${TimeText.duration(verdict.requiredMinutes)}",
        critical = verdict.slackMinutes < 0,
    )

    if (previous.destination.hasCoordinates && segment.origin.hasCoordinates &&
        previous.destination.name != segment.origin.name
    ) {
        Spacer(Modifier.height(WaymarkSpacing.snug))
        Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Ways across")
            Spacer(Modifier.height(WaymarkSpacing.snug))
            TransitEstimator.options(previous.destination, segment.origin).forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = option.mode.label,
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                    )
                    Text(
                        text = "${TimeText.duration(option.totalMinutes)} · " +
                            Geo.formatDistance(option.distanceKm),
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
