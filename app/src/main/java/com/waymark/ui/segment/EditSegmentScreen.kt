package com.waymark.ui.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.DateField
import com.waymark.ui.components.FieldLabel
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
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The editing half of a booking — every field, not only the times.
 *
 * Everything here is held in one [SegmentForm] and written back exactly once,
 * when Save is pressed. That is the whole point: on the read-only screen a
 * stray tap did real damage, and an edit that commits per keystroke has the
 * same problem with more steps. Cancel discards, and it is the default action
 * of the back control too.
 *
 * A booking entered in a hurry is usually entered wrong somewhere — the
 * flight number, the hotel's name, the station a train leaves from — so all of
 * it can be changed here, not just what the Add flight screen happened to
 * collect. Changing an airport or a place moves the booking into that place's
 * zone, with the clock times kept as typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSegmentScreen(
    original: Segment,
    party: List<Traveler>,
    onCancel: () -> Unit,
    onSave: (Segment) -> Unit,
) {
    // Keyed by the booking, so opening another one never shows this one's edits.
    var form by rememberSaveable(original.id) { mutableStateOf(SegmentForm.of(original, party)) }
    val outcome = remember(form, original, party) { form.apply(original, party) }
    val edited = (outcome as? SegmentForm.Outcome.Saved)?.segment
    val problem = (outcome as? SegmentForm.Outcome.Refused)?.problem
    val flight = original as? Segment.Flight

    ScreenScaffold(
        title = "Edit booking",
        onBack = onCancel,
        spacing = WaymarkSpacing.small,
    ) {
        SectionHeader(
            when (original) {
                is Segment.Flight -> "Flight"
                is Segment.Lodging -> "Stay"
                is Segment.Ground -> "Getting there"
                is Segment.Experience -> "Booking"
            }
        )
        WhatAndWhere(original, form) { form = it }

        SectionHeader(if (original is Segment.Lodging) "Check in" else "Starts")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = form.startDate,
                onValueChange = { form = form.copy(startDate = it) },
                label = "Date",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = form.startTime,
                onValueChange = { form = form.copy(startTime = it) },
                label = TimeText.zoneLabel((edited ?: original).start),
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(if (original is Segment.Lodging) "Check out" else "Ends")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = form.endDate,
                onValueChange = { form = form.copy(endDate = it) },
                label = "Date",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = form.endTime,
                onValueChange = { form = form.copy(endTime = it) },
                label = TimeText.zoneLabel((edited ?: original).end),
                modifier = Modifier.weight(1f),
            )
        }

        if (edited != null) {
            val rolled = edited.end.toLocalDate() != form.endDate
            Footnote(
                TimeText.durationBetween(edited.startEpochMillis, edited.endEpochMillis) +
                    if (rolled) ", ending the next day" else ""
            )
        }

        if (party.size > 1) {
            SectionHeader("Who is on it")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                party.forEach { traveler ->
                    val on = traveler.id in form.travelerIds
                    OptionChip(
                        text = traveler.displayName,
                        selected = on,
                        onClick = {
                            val ids = if (on) form.travelerIds - traveler.id else form.travelerIds + traveler.id
                            form = form.copy(travelerIds = ids)
                        },
                        leading = { PartyMark(initials = traveler.initials, active = on, size = 18.dp) },
                    )
                }
            }
        }

        when (original) {
            is Segment.Flight -> FlightDetail(form) { form = it }
            is Segment.Lodging -> StayDetail(form) { form = it }
            is Segment.Ground -> GroundDetail(form) { form = it }
            is Segment.Experience -> ExperienceDetail(form) { form = it }
        }

        SectionHeader("Reference")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            WaymarkTextField(
                value = form.bookedWith,
                onValueChange = { form = form.copy(bookedWith = it) },
                label = "Booked with",
                placeholder = "Direct booking",
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = form.confirmationCode,
                onValueChange = { form = form.copy(confirmationCode = it) },
                label = if (flight != null) "Record locator" else "Confirmation",
                placeholder = "K7QH2P",
                mono = true,
                modifier = Modifier.weight(1f),
            )
        }

        // Seat and ticket number are per person, so they are asked for per
        // person — and only for the people actually on this booking.
        if (flight != null) {
            party.filter { it.id in form.travelerIds }.forEach { traveler ->
                FieldLabel(traveler.displayName)
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    WaymarkTextField(
                        value = form.tickets[traveler.id].orEmpty(),
                        onValueChange = { form = form.copy(tickets = form.tickets + (traveler.id to it)) },
                        placeholder = "E-ticket",
                        mono = true,
                        modifier = Modifier.weight(2f),
                    )
                    WaymarkTextField(
                        value = form.seats[traveler.id].orEmpty(),
                        onValueChange = { form = form.copy(seats = form.seats + (traveler.id to it.uppercase())) },
                        placeholder = "21A",
                        mono = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SectionHeader("Note")
        WaymarkTextField(
            value = form.note,
            onValueChange = { form = form.copy(note = it) },
            placeholder = "Anything worth remembering about this booking",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        problem?.let {
            Text(text = describe(it), style = Waymark.type.hint, color = Waymark.colors.danger)
        }

        PrimaryButton(
            text = "Save changes",
            icon = WaymarkIcons.Check,
            onClick = { edited?.let(onSave) },
            enabled = edited != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WaymarkSpacing.small),
        )
        MutedButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/** The booking's name and its places — the fields that say what it is. */
@Composable
private fun WhatAndWhere(original: Segment, form: SegmentForm, onChange: (SegmentForm) -> Unit) {
    when (original) {
        is Segment.Flight -> {
            WaymarkTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it.uppercase())) },
                label = "Flight number",
                placeholder = "BA286",
                mono = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                WaymarkTextField(
                    value = form.where,
                    onValueChange = { onChange(form.copy(where = it.uppercase().take(3))) },
                    label = "From",
                    placeholder = "SFO",
                    mono = true,
                    modifier = Modifier.weight(1f),
                )
                WaymarkTextField(
                    value = form.destination,
                    onValueChange = { onChange(form.copy(destination = it.uppercase().take(3))) },
                    label = "To",
                    placeholder = "LHR",
                    mono = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        is Segment.Ground -> {
            WaymarkTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                label = "Operator",
                placeholder = "Eurostar",
                modifier = Modifier.fillMaxWidth(),
            )
            WaymarkTextField(
                value = form.where,
                onValueChange = { onChange(form.copy(where = it)) },
                label = "From",
                placeholder = "St Pancras, London",
                modifier = Modifier.fillMaxWidth(),
            )
            WaymarkTextField(
                value = form.destination,
                onValueChange = { onChange(form.copy(destination = it)) },
                label = "To",
                placeholder = "Gare du Nord, Paris",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            WaymarkTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                label = "Name",
                placeholder = if (original is Segment.Lodging) "The Hoxton" else "Louvre, timed entry",
                modifier = Modifier.fillMaxWidth(),
            )
            WaymarkTextField(
                value = form.where,
                onValueChange = { onChange(form.copy(where = it)) },
                label = "Where",
                placeholder = "Paris",
                hint = "A city, or a place in one — \"Holborn, London\"",
                modifier = Modifier.fillMaxWidth(),
            )
            WaymarkTextField(
                value = form.address,
                onValueChange = { onChange(form.copy(address = it)) },
                label = "Address",
                placeholder = "199-206 High Holborn",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FlightDetail(form: SegmentForm, onChange: (SegmentForm) -> Unit) {
    SectionHeader("Detail")
    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
        WaymarkTextField(
            value = form.departureTerminal,
            onValueChange = { onChange(form.copy(departureTerminal = it)) },
            label = "Terminal out",
            placeholder = "I",
            mono = true,
            modifier = Modifier.weight(1f),
        )
        WaymarkTextField(
            value = form.departureGate,
            onValueChange = { onChange(form.copy(departureGate = it.uppercase())) },
            label = "Gate",
            placeholder = "A6",
            mono = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
        WaymarkTextField(
            value = form.arrivalTerminal,
            onValueChange = { onChange(form.copy(arrivalTerminal = it)) },
            label = "Terminal in",
            placeholder = "5",
            mono = true,
            modifier = Modifier.weight(1f),
        )
        WaymarkTextField(
            value = form.arrivalGate,
            onValueChange = { onChange(form.copy(arrivalGate = it.uppercase())) },
            label = "Gate",
            placeholder = "B32",
            mono = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
        WaymarkTextField(
            value = form.aircraft,
            onValueChange = { onChange(form.copy(aircraft = it)) },
            label = "Aircraft",
            placeholder = "Boeing 777",
            modifier = Modifier.weight(1f),
        )
        WaymarkTextField(
            value = form.cabin,
            onValueChange = { onChange(form.copy(cabin = it)) },
            label = "Cabin",
            placeholder = "Economy",
            modifier = Modifier.weight(1f),
        )
    }
    WaymarkTextField(
        value = form.operatedBy,
        onValueChange = { onChange(form.copy(operatedBy = it)) },
        label = "Operated by",
        placeholder = "Only when another airline flies it",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StayDetail(form: SegmentForm, onChange: (SegmentForm) -> Unit) {
    SectionHeader("Detail")
    WaymarkTextField(
        value = form.room,
        onValueChange = { onChange(form.copy(room = it)) },
        label = "Room",
        placeholder = "Double, courtyard side",
        modifier = Modifier.fillMaxWidth(),
    )
    WaymarkTextField(
        value = form.checkInNote,
        onValueChange = { onChange(form.copy(checkInNote = it)) },
        label = "Check-in",
        placeholder = "Key code in the confirmation email",
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroundDetail(form: SegmentForm, onChange: (SegmentForm) -> Unit) {
    SectionHeader("How")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
    ) {
        GroundMode.entries.forEach { mode ->
            OptionChip(
                text = mode.label,
                selected = form.mode == mode,
                onClick = { onChange(form.copy(mode = mode)) },
            )
        }
    }
    WaymarkTextField(
        value = form.pickup,
        onValueChange = { onChange(form.copy(pickup = it)) },
        label = "Pick-up",
        placeholder = "Driver waits at arrivals with a sign",
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExperienceDetail(form: SegmentForm, onChange: (SegmentForm) -> Unit) {
    SectionHeader("Detail")
    WaymarkTextField(
        value = form.category,
        onValueChange = { onChange(form.copy(category = it)) },
        label = "Kind",
        placeholder = "Museum, dinner, tour",
        modifier = Modifier.fillMaxWidth(),
    )
    WaymarkTextField(
        value = form.tips,
        onValueChange = { onChange(form.copy(tips = it)) },
        label = "Tips",
        placeholder = "Go in by the Carrousel entrance",
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun describe(problem: SegmentForm.Problem): String = when (problem) {
    SegmentForm.Problem.NoName -> "Give it a name."
    SegmentForm.Problem.NoTraveler -> "Someone has to be on it."
    is SegmentForm.Problem.UnknownAirport ->
        if (problem.code.isEmpty()) "Both airports are needed." else "Waymark does not know an airport ${problem.code}."
    SegmentForm.Problem.NotAFlightNumber -> "That is not a flight number — it reads like BA286."
    SegmentForm.Problem.EndsBeforeStart -> "It ends before it starts."
    SegmentForm.Problem.TooLongForAFlight -> "Longer than any flight — check the times against each airport's clock."
}
