package com.waymark.ui.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.DateField
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.WaymarkSpacing
import java.time.ZonedDateTime

/**
 * The editing half of a booking.
 *
 * Everything here is held in local state and written back exactly once, when
 * Save is pressed. That is the whole point: on the read-only screen a stray
 * tap did real damage, and an edit that commits per keystroke has the same
 * problem with more steps. Cancel discards, and it is the default action of
 * the back control too.
 *
 * Times are edited as a date plus a clock time **in the segment's own zone**,
 * which is how they were entered and how they read on the timeline. Editing a
 * departure out of San Francisco does not silently reinterpret 16:20 in the
 * phone's current zone.
 */
@Composable
fun EditSegmentScreen(
    original: Segment,
    party: List<Traveler>,
    onCancel: () -> Unit,
    onSave: (Segment) -> Unit,
) {
    val startZone = Segment.zoneOrUtc(original.startZoneId)
    val endZone = Segment.zoneOrUtc(original.endZoneId)

    var startDate by remember(original) { mutableStateOf(original.start.toLocalDate()) }
    var startTime by remember(original) { mutableStateOf(original.start.toLocalTime()) }
    var endDate by remember(original) { mutableStateOf(original.end.toLocalDate()) }
    var endTime by remember(original) { mutableStateOf(original.end.toLocalTime()) }
    var note by remember(original) { mutableStateOf(original.note.orEmpty()) }
    var travelers by remember(original) {
        mutableStateOf(
            // An empty set has always meant "everyone"; make that explicit
            // while editing so the chips show what is actually true.
            original.travelerIds.ifEmpty { party.map { it.id }.toSet() }
        )
    }

    val start = ZonedDateTime.of(startDate, startTime, startZone)
    var end = ZonedDateTime.of(endDate, endTime, endZone)
    // A segment that ends before it starts is almost always an overnight one
    // whose end date was not moved; say so rather than refusing to save.
    val overnight = !end.toInstant().isAfter(start.toInstant())
    if (overnight) end = end.plusDays(1)

    ScreenScaffold(
        title = "Edit booking",
        onBack = onCancel,
        spacing = WaymarkSpacing.small,
    ) {
        SectionHeader(if (original is Segment.Lodging) "Check in" else "Departs")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = startDate,
                onValueChange = { startDate = it },
                label = "Date",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = startTime,
                onValueChange = { startTime = it },
                label = TimeText.zoneLabel(original.start),
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(if (original is Segment.Lodging) "Check out" else "Arrives")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = endDate,
                onValueChange = { endDate = it },
                label = "Date",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = endTime,
                onValueChange = { endTime = it },
                label = TimeText.zoneLabel(original.end),
                modifier = Modifier.weight(1f),
            )
        }

        Footnote(
            TimeText.durationBetween(
                start.toInstant().toEpochMilli(),
                end.toInstant().toEpochMilli(),
            ) + if (overnight) ", landing the next day" else ""
        )

        if (party.size > 1) {
            SectionHeader("Who is on it")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                party.forEach { traveler ->
                    val on = traveler.id in travelers
                    OptionChip(
                        text = traveler.displayName,
                        selected = on,
                        onClick = {
                            travelers = if (on) {
                                travelers - traveler.id
                            } else {
                                travelers + traveler.id
                            }
                        },
                        leading = {
                            PartyMark(initials = traveler.initials, active = on, size = 18.dp)
                        },
                    )
                }
            }
        }

        SectionHeader("Note")
        WaymarkTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Anything worth remembering about this booking",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = "Save changes",
            icon = WaymarkIcons.Check,
            onClick = {
                onSave(
                    original.withTimes(
                        startMillis = start.toInstant().toEpochMilli(),
                        endMillis = end.toInstant().toEpochMilli(),
                        travelerIds = travelers,
                        note = note.trim().ifBlank { null },
                    )
                )
            },
            // Deselecting everybody would produce a booking with no passenger.
            enabled = travelers.isNotEmpty() || party.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WaymarkSpacing.small),
        )
        MutedButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Copy a segment with new times, party and note.
 *
 * `Segment` is a sealed class of four data classes with no common `copy`, so
 * this is the one place that has to know all four. Keeping it here rather than
 * on the model keeps the domain free of a method that exists only because a
 * screen needs it.
 */
private fun Segment.withTimes(
    startMillis: Long,
    endMillis: Long,
    travelerIds: Set<String>,
    note: String?,
): Segment = when (this) {
    is Segment.Flight -> copy(
        startEpochMillis = startMillis,
        endEpochMillis = endMillis,
        travelerIds = travelerIds,
        note = note,
    )

    is Segment.Lodging -> copy(
        startEpochMillis = startMillis,
        endEpochMillis = endMillis,
        travelerIds = travelerIds,
        note = note,
    )

    is Segment.Ground -> copy(
        startEpochMillis = startMillis,
        endEpochMillis = endMillis,
        travelerIds = travelerIds,
        note = note,
    )

    is Segment.Experience -> copy(
        startEpochMillis = startMillis,
        endEpochMillis = endMillis,
        travelerIds = travelerIds,
        note = note,
    )
}
