package com.waymark.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.EventRole
import com.waymark.domain.logic.EventState
import com.waymark.domain.logic.LinkNature
import com.waymark.domain.logic.RiskLevel
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.model.Segment
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyFilterBar
import com.waymark.ui.components.settleIn
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.SecondaryButton
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The single column that the whole app exists to produce: days in order, the
 * time in the left gutter in mono, and the awkward gaps between bookings
 * named rather than left blank.
 */
@Composable
fun TimelineTab(
    state: TripUiState,
    onSelectSegment: (String) -> Unit,
    onFilterTraveler: (String?) -> Unit,
    onAddFlight: () -> Unit,
    onAddPlan: () -> Unit,
) {
    val colors = Waymark.colors
    val party = state.dossier?.party?.travelers.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = WaymarkSpacing.screenBottom),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        item {
            PartyFilterBar(
                names = party.associate { it.id to it.displayName },
                selected = state.travelerFilter,
                onSelect = onFilterTraveler,
                modifier = Modifier.padding(bottom = WaymarkSpacing.tight),
            )
        }

        if (state.timeline.isEmpty()) {
            item {
                Text(
                    text = "Nothing booked yet.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                    modifier = Modifier.padding(vertical = WaymarkSpacing.small),
                )
            }
        }

        itemsIndexed(state.timeline, key = { _, entry -> entryKey(entry) }) { index, entry ->
            // A short stagger down the column, capped inside settleIn so a
            // forty-entry itinerary does not take a second to appear.
            Column(modifier = Modifier.settleIn(order = index)) {
                when (entry) {
                    is TimelineEntry.DayBreak -> DayBreakRow(entry)
                    is TimelineEntry.Now -> NowRow(entry)
                    is TimelineEntry.Link -> LinkRow(entry)
                    is TimelineEntry.Event -> EventRow(
                        entry = entry,
                        dossierPartyInitials = state.dossier?.party?.travelers
                            ?.associate { it.id to it.initials }
                            .orEmpty(),
                        onClick = { onSelectSegment(entry.segment.id) },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(WaymarkSpacing.medium))
            // Two equal halves. They were a PrimaryButton and a MutedButton,
            // which have different vertical padding and different border
            // weights, so side by side one sat taller than the other.
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    text = "Add flight",
                    icon = WaymarkIcons.Plane,
                    onClick = onAddFlight,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Add plan",
                    icon = WaymarkIcons.Plus,
                    onClick = onAddPlan,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun entryKey(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.DayBreak -> "day-${entry.date}"
    is TimelineEntry.Event -> entry.id
    is TimelineEntry.Link -> entry.id
    is TimelineEntry.Now -> "now"
}

@Composable
private fun DayBreakRow(entry: TimelineEntry.DayBreak) {
    val colors = Waymark.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WaymarkSpacing.medium, bottom = WaymarkSpacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        Text(
            text = entry.headline,
            style = Waymark.type.sectionHeading,
            color = colors.textHeading,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.borderFaint)
        )
        Text(
            text = entry.zoneLabel,
            style = Waymark.type.fieldLabel,
            color = colors.textFaint,
        )
    }
}

@Composable
private fun NowRow(entry: TimelineEntry.Now) {
    val colors = Waymark.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WaymarkSpacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        Text(
            text = "NOW",
            style = Waymark.type.fieldLabel,
            color = colors.accentBright,
            modifier = Modifier.width(GUTTER),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.accentBright.copy(alpha = 0.55f))
        )
        Text(
            text = entry.label,
            style = Waymark.type.dataSmall,
            color = colors.accentBright,
        )
    }
}

/** The gap between two bookings: a connection, a transfer, or free time. */
@Composable
private fun LinkRow(entry: TimelineEntry.Link) {
    val colors = Waymark.colors
    val verdict = entry.verdict
    val tint = when (verdict?.level) {
        RiskLevel.BROKEN, RiskLevel.AT_RISK -> colors.danger
        RiskLevel.TIGHT -> colors.accentBright
        else -> colors.textDim
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(GUTTER)) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(1.dp)
                    .height(44.dp)
                    .background(colors.borderFaint)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = WaymarkSpacing.tight),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                WaymarkIcon(
                    icon = when (entry.nature) {
                        LinkNature.CONNECTION -> WaymarkIcons.Split
                        LinkNature.TRANSFER -> WaymarkIcons.Car
                        LinkNature.FREE_TIME -> WaymarkIcons.Coffee
                    },
                    tint = tint,
                    size = 14.dp,
                )
                Text(
                    text = when (entry.nature) {
                        LinkNature.CONNECTION -> "Connection · ${TimeText.duration(entry.gapMinutes)}"
                        LinkNature.TRANSFER -> "Transfer · ${TimeText.duration(entry.gapMinutes)}"
                        LinkNature.FREE_TIME -> "Free · ${TimeText.duration(entry.gapMinutes)}"
                    },
                    style = Waymark.type.dataSmall,
                    color = tint,
                )
                verdict?.let {
                    Text(
                        text = it.level.label.lowercase(),
                        style = Waymark.type.fieldLabel,
                        color = tint,
                    )
                }
            }
            val detail = verdict?.reason ?: entry.estimate?.summary
            detail?.let {
                Text(text = it, style = Waymark.type.hint, color = colors.textDim)
            }
        }
    }
}

@Composable
private fun EventRow(
    entry: TimelineEntry.Event,
    dossierPartyInitials: Map<String, String>,
    onClick: () -> Unit,
) {
    val colors = Waymark.colors
    val segment = entry.segment
    val past = entry.state == EventState.PAST

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(GUTTER)
                .padding(top = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = TimeText.clock(entry.localTime),
                style = Waymark.type.data,
                color = if (past) colors.textFaint else colors.textMuted,
            )
            entry.arrivalDayOffset?.let {
                Text(text = it, style = Waymark.type.fieldLabel, color = colors.textFaint)
            }
        }

        RailMark(
            icon = iconFor(segment),
            active = entry.state == EventState.ACTIVE,
            past = past,
        )

        Panel(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = WaymarkSpacing.snug),
            onClick = onClick,
            padding = WaymarkSpacing.small,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline(segment, entry.role),
                        style = Waymark.type.cardTitle,
                        color = if (past) colors.textMuted else colors.textHeading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle(segment, entry.role),
                        style = Waymark.type.hint,
                        color = colors.textDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (segment.travelerIds.isNotEmpty() &&
                    segment.travelerIds.size < dossierPartyInitials.size
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        segment.travelerIds.mapNotNull { dossierPartyInitials[it] }
                            .forEach { PartyMark(initials = it, size = 22.dp) }
                    }
                }
            }

            DetailLine(segment)
        }
    }
}

@Composable
private fun RailMark(icon: ImageVector, active: Boolean, past: Boolean) {
    val colors = Waymark.colors
    Column(
        modifier = Modifier
            .width(34.dp)
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(WaymarkShapes.chip)
                .background(
                    when {
                        active -> colors.amber(0.22f)
                        past -> colors.panelFaint
                        else -> colors.chip
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            WaymarkIcon(
                icon = icon,
                tint = when {
                    active -> colors.accentAmber
                    past -> colors.textFaint
                    else -> colors.textMuted
                },
                size = 14.dp,
            )
        }
    }
}

@Composable
private fun DetailLine(segment: Segment) {
    val colors = Waymark.colors
    val parts = buildList {
        when (segment) {
            is Segment.Flight -> {
                segment.departureTerminal?.let { add("T$it") }
                segment.departureGate?.let { add("Gate $it") }
                add(TimeText.durationBetween(segment.startEpochMillis, segment.endEpochMillis))
                segment.aircraft?.let { add(it) }
            }

            is Segment.Lodging -> {
                add("${segment.nights} night" + if (segment.nights == 1L) "" else "s")
                segment.roomDescription?.let { add(it) }
            }

            is Segment.Ground -> {
                add(TimeText.durationBetween(segment.startEpochMillis, segment.endEpochMillis))
                segment.provider?.let { add(it) }
            }

            is Segment.Experience -> {
                add(segment.category)
                add(TimeText.durationBetween(segment.startEpochMillis, segment.endEpochMillis))
            }
        }
    }
    if (parts.isEmpty()) return

    Spacer(Modifier.height(WaymarkSpacing.snug))
    Text(
        text = parts.joinToString("  ·  "),
        style = Waymark.type.dataSmall,
        color = colors.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun iconFor(segment: Segment): ImageVector = when (segment) {
    is Segment.Flight -> WaymarkIcons.Plane
    is Segment.Lodging -> WaymarkIcons.Bed
    is Segment.Ground -> when (segment.mode) {
        com.waymark.domain.model.GroundMode.TRAIN,
        com.waymark.domain.model.GroundMode.TRANSIT,
        -> WaymarkIcons.Train

        else -> WaymarkIcons.Car
    }

    is Segment.Experience -> WaymarkIcons.Compass
}

private fun headline(segment: Segment, role: EventRole): String = when (segment) {
    is Segment.Flight -> "${segment.origin.shortLabel} → ${segment.destination.shortLabel}"
    is Segment.Lodging -> when (role) {
        EventRole.CHECK_OUT -> "Check out · ${segment.propertyName}"
        else -> segment.propertyName
    }

    is Segment.Ground -> "${segment.origin.shortLabel} → ${segment.destination.shortLabel}"
    is Segment.Experience -> segment.name
}

private fun subtitle(segment: Segment, role: EventRole): String = when (segment) {
    is Segment.Flight -> "${segment.designator} · ${segment.operatedBy ?: segment.carrierCode}"
    is Segment.Lodging -> when (role) {
        EventRole.CHECK_OUT -> segment.origin.city.ifBlank { segment.origin.name }
        else -> segment.origin.address ?: segment.origin.city
    }

    is Segment.Ground -> segment.mode.label
    is Segment.Experience -> segment.origin.address ?: segment.origin.city.ifBlank {
        segment.category
    }
}

private val GUTTER = 52.dp
