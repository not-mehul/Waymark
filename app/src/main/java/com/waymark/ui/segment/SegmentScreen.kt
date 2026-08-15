package com.waymark.ui.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel
import java.time.Instant

/**
 * One booking in full: the times in both local clocks, whatever the tracker
 * knows, who is on it, and the codes that belong to it.
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

    WaymarkBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                GhostIconButton(
                    icon = WaymarkIcons.Trash,
                    contentDescription = "Remove booking",
                    onClick = { confirmingDelete = true },
                )
            }

            if (segment == null) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This booking has been removed.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                return@Column
            }

            SectionLabel(kindLabel(segment))
            Text(
                text = segment.title,
                style = Waymark.type.screenTitle,
                color = colors.textHeading,
            )

            status?.let { StatusPanel(it, segment) }

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
                EditorialNote(
                    term = it.city,
                    body = "${it.currency} · ${it.plugTypes} · emergency ${it.emergencyNumber}",
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.section))
        }
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

@Composable
private fun StatusPanel(status: FlightStatus, segment: Segment) {
    val colors = Waymark.colors
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
            Text(
                text = status.source,
                style = Waymark.type.fieldLabel,
                color = colors.textFaint,
            )
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
                status.baggageBelt?.let { append(" · belt $it") }
            },
            style = Waymark.type.bodySmall,
            color = colors.textMuted,
        )
        status.position?.let { position ->
            Spacer(Modifier.height(WaymarkSpacing.snug))
            Text(
                text = "${status.progressPercent}% flown · " +
                    "${position.altitudeFeet ?: 0} ft · ${position.groundSpeedKnots ?: 0} kt · " +
                    "heading ${position.headingDegrees ?: 0}°",
                style = Waymark.type.dataSmall,
                color = colors.accentSage,
            )
        }
        if (status.isStale) {
            Spacer(Modifier.height(WaymarkSpacing.snug))
            Text(
                text = "Last checked ${TimeText.relative(status.observedAtMillis)}",
                style = Waymark.type.hint,
                color = colors.textFaint,
            )
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

private fun kindLabel(segment: Segment): String = when (segment) {
    is Segment.Flight -> "Flight"
    is Segment.Lodging -> "Lodging"
    is Segment.Ground -> "Ground"
    is Segment.Experience -> "Experience"
}
