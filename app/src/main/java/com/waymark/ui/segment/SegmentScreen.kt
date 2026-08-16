package com.waymark.ui.segment

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.DestinationInsights
import com.waymark.domain.logic.ConnectionRisk
import com.waymark.domain.logic.FlightUpdate
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.TransitEstimator
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel
import java.time.Instant

/**
 * One booking in full: the times in both local clocks, who is on it, the codes
 * that belong to it, and — for a flight — whatever the departure board last
 * said, as reported by whoever was standing in front of it.
 */
@Composable
fun SegmentScreen(
    segmentId: String,
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onOpenPass: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val segment = state.dossier?.segments?.firstOrNull { it.id == segmentId }
    val status = segment?.let { state.dossier?.statusFor(it) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = segment?.title ?: "Booking",
        onBack = onBack,
        spacing = WaymarkSpacing.small,
        action = {
            if (segment != null) {
                GhostIconButton(
                    icon = WaymarkIcons.Trash,
                    contentDescription = "Remove booking",
                    onClick = { confirmingDelete = true },
                )
            }
        },
    ) {
        if (segment == null) {
            Footnote("This booking has been removed.")
            return@ScreenScaffold
        }

        if (segment is Segment.Flight) {
            StatusPanel(
                status = status,
                segment = segment,
                onReport = { reporting = true },
                onClear = { viewModel.clearFlightReport(segment.id) },
            )
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

        SectionHeader("Travelling")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            state.dossier?.party?.travelers?.forEach { traveler ->
                val on = segment.travelerIds.isEmpty() || traveler.id in segment.travelerIds
                OptionChip(
                    text = traveler.displayName,
                    selected = on,
                    onClick = {
                        val current = segment.travelerIds.ifEmpty {
                            state.dossier?.party?.travelers?.map { it.id }?.toSet().orEmpty()
                        }
                        viewModel.setTravelers(
                            segment.id,
                            if (on) current - traveler.id else current + traveler.id,
                        )
                    },
                    leading = { PartyMark(initials = traveler.initials, active = on, size = 18.dp) },
                )
            }
        }

        viewModel.reservationFor(segment.id)?.let { reservation ->
            SectionHeader("Vault")
            Panel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = reservation.label,
                    style = Waymark.type.cardTitle,
                    color = colors.textHeading,
                )
                Text(
                    text = "${reservation.secrets.size} stored " +
                        if (reservation.secrets.size == 1) "code" else "codes",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        }

        val passes = state.passes.filter { it.segmentId == segment.id }
        if (passes.isNotEmpty()) {
            SectionHeader("Passes")
            passes.forEach { pass ->
                Panel(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenPass(pass.id) },
                ) {
                    Text(
                        text = pass.passengerName,
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                    )
                    Text(
                        text = listOfNotNull(
                            pass.seat?.let { "Seat $it" },
                            pass.boardingGroup?.let { "Group $it" },
                        ).joinToString(" · ").ifBlank { "Open pass" },
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                }
            }
        }

        ConnectionPanel(segment, state.dossier?.segments.orEmpty(), state.dossier?.statuses.orEmpty())

        val insight = DestinationInsights.forAirport(segment.destination.code)
            ?: DestinationInsights.forCity(segment.destination.city)
        insight?.let {
            Footnote(
                "${it.city}: ${it.currency} · ${it.plugTypes} · " +
                    "emergency ${it.emergencyNumber}"
            )
        }
    }

    if (reporting && segment is Segment.Flight) {
        ReportStatusModal(
            flight = segment,
            current = status,
            onDismiss = { reporting = false },
            onReport = { update ->
                viewModel.reportFlight(segment.id, update)
                reporting = false
            },
        )
    }

    if (confirmingDelete && segment != null) {
        WaymarkModal(
            title = "Remove this booking",
            eyebrow = "Confirm",
            onDismiss = { confirmingDelete = false },
        ) {
            Text(
                text = "${segment.title} will be removed from the itinerary, along with its " +
                    "tracking. Stored codes are kept in the vault.",
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

/**
 * What the flight is doing, and the control that changes it.
 *
 * Waymark has no feed, so this panel is only ever as current as the last
 * person who looked at a board and typed what it said. It says so, with a
 * timestamp, rather than presenting a hand-entered delay as a live one.
 */
@Composable
private fun StatusPanel(
    status: FlightStatus?,
    segment: Segment.Flight,
    onReport: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = Waymark.colors

    if (status == null) {
        Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "On its booked times",
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                    )
                    Text(
                        text = "Nothing reported yet.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                MutedButton(text = "Report", onClick = onReport)
            }
        }
        return
    }

    val delay = status.departureDelayMinutes
    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status.state.label,
                style = Waymark.type.sectionHeading,
                color = if (status.state.isDisrupted) colors.danger else colors.textHeading,
            )
            MutedButton(text = "Update", onClick = onReport)
        }

        Spacer(Modifier.height(WaymarkSpacing.snug))
        Text(
            text = buildString {
                if (delay != 0) {
                    append("Departure ${TimeText.duration(delay)} ")
                    append(if (delay > 0) "late" else "early")
                    append(" · now ")
                    append(
                        TimeText.clock(
                            Instant.ofEpochMilli(status.estimatedDepartureMillis)
                                .atZone(segment.start.zone)
                        )
                    )
                } else {
                    append("Running to schedule")
                }
                status.departureGate?.let { append(" · gate $it") }
                status.departureTerminal?.let { append(" · terminal $it") }
                status.baggageBelt?.let { append(" · belt $it") }
            },
            style = Waymark.type.bodySmall,
            color = colors.textMuted,
        )

        Spacer(Modifier.height(WaymarkSpacing.snug))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reported ${TimeText.relative(status.observedAtMillis)}",
                style = Waymark.type.hint,
                color = colors.textFaint,
            )
            Text(
                text = "Reset",
                style = Waymark.type.hint,
                color = colors.textDim,
                modifier = Modifier.clickable(onClick = onClear),
            )
        }
    }
}

/**
 * The report itself. Deliberately five fields and no more: a traveler at a
 * gate with a bag on their shoulder is not going to fill in a form.
 */
@Composable
private fun ReportStatusModal(
    flight: Segment.Flight,
    current: FlightStatus?,
    onDismiss: () -> Unit,
    onReport: (FlightUpdate) -> Unit,
) {
    var state by remember { mutableStateOf(current?.state) }
    var delay by remember { mutableStateOf(current?.departureDelayMinutes?.toString().orEmpty()) }
    var gate by remember { mutableStateOf(current?.departureGate.orEmpty()) }
    var terminal by remember { mutableStateOf(current?.departureTerminal.orEmpty()) }
    var belt by remember { mutableStateOf(current?.baggageBelt.orEmpty()) }

    WaymarkModal(
        title = flight.designator,
        eyebrow = "What the board says",
        onDismiss = onDismiss,
    ) {
        FieldLabel("Status")
        // The six states a departure board actually shows. Anything finer is
        // detail nobody standing in a queue is going to enter.
        listOf(
            listOf(FlightState.ON_TIME, FlightState.DELAYED, FlightState.BOARDING),
            listOf(FlightState.DEPARTED, FlightState.LANDED, FlightState.CANCELLED),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                row.forEach { option ->
                    OptionChip(
                        text = option.label,
                        selected = state == option,
                        onClick = { state = if (state == option) null else option },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            WaymarkTextField(
                value = delay,
                onValueChange = { delay = it.filter { c -> c.isDigit() || c == '-' } },
                label = "Delay (min)",
                placeholder = "40",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = gate,
                onValueChange = { gate = it.uppercase() },
                label = "Gate",
                placeholder = "A12",
                mono = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            WaymarkTextField(
                value = terminal,
                onValueChange = { terminal = it.uppercase() },
                label = "Terminal",
                placeholder = "5",
                mono = true,
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = belt,
                onValueChange = { belt = it.uppercase() },
                label = "Baggage belt",
                placeholder = "7",
                mono = true,
                modifier = Modifier.weight(1f),
            )
        }

        PrimaryButton(
            text = "Save",
            icon = WaymarkIcons.Check,
            onClick = {
                onReport(
                    FlightUpdate(
                        state = state,
                        departureDelayMinutes = delay.toIntOrNull(),
                        departureGate = gate,
                        departureTerminal = terminal,
                        baggageBelt = belt,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun ConnectionPanel(
    segment: Segment,
    all: List<Segment>,
    statuses: Map<String, FlightStatus>,
) {
    val colors = Waymark.colors
    val ordered = all.sortedBy { it.startEpochMillis }
    val index = ordered.indexOfFirst { it.id == segment.id }
    if (index <= 0) return
    val previous = ordered[index - 1]
    if (previous is Segment.Lodging || segment is Segment.Lodging) return

    val gapMinutes = ((segment.startEpochMillis - previous.endEpochMillis) / 60_000L).toInt()
    val verdict = if (previous is Segment.Flight && segment is Segment.Flight) {
        ConnectionRisk.assess(
            inbound = previous,
            onward = segment,
            inboundStatus = statuses[previous.id],
            onwardStatus = statuses[segment.id],
        )
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
