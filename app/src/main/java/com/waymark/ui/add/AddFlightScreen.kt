package com.waymark.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.Airports
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import com.waymark.ui.components.DateField
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.LocalReminderPermission
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
import java.time.Duration
import java.time.ZonedDateTime

/**
 * A flight, typed in.
 *
 * Waymark does not call a schedule service, so nothing here fills itself in.
 * What the app does contribute is what an airport code already implies —
 * coordinates and a time zone — which is enough to put the leg on the map and
 * to work out that a 22:20 departure lands the following afternoon.
 */
@Composable
fun AddFlightScreen(
    viewModel: AddFlightViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDetail by rememberSaveable { mutableStateOf(false) }

    // Saving a flight is the moment a reminder about a departure starts to
    // mean something, so it is the moment the app asks to be allowed to send
    // one. Nothing is asked for on the way in.
    val reminders = LocalReminderPermission.current

    ScreenScaffold(title = "Add flight", onBack = onDone, spacing = WaymarkSpacing.small) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            verticalAlignment = Alignment.Bottom,
        ) {
            WaymarkTextField(
                value = state.designator,
                onValueChange = viewModel::setDesignator,
                label = "Flight",
                placeholder = "BA286",
                mono = true,
                modifier = Modifier.weight(1f),
            )
            DateField(
                value = state.date,
                onValueChange = viewModel::setDate,
                label = "Date",
                modifier = Modifier.weight(1f),
            )
        }

        StationField(
            value = state.origin,
            onValueChange = viewModel::setOrigin,
            label = "From",
            placeholder = "SFO",
            suggestions = state.suggestionsFor(AddFlightUiState.Field.ORIGIN),
            onPick = viewModel::setOrigin,
        )
        StationField(
            value = state.destination,
            onValueChange = viewModel::setDestination,
            label = "To",
            placeholder = "LHR",
            suggestions = state.suggestionsFor(AddFlightUiState.Field.DESTINATION),
            onPick = viewModel::setDestination,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            TimeField(
                value = state.departTime,
                onValueChange = viewModel::setDepartTime,
                label = "Departs",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = state.arriveTime,
                onValueChange = viewModel::setArriveTime,
                label = "Arrives",
                modifier = Modifier.weight(1f),
            )
        }

        state.blockSummary()?.let { summary ->
            Text(
                text = summary,
                style = Waymark.type.data,
                color = Waymark.colors.accentAmber,
                modifier = Modifier.padding(top = WaymarkSpacing.tight),
            )
        }

        // Everything past this point is optional, and stays folded away until
        // someone wants it.
        SectionHeader(
            label = "Detail",
            trailing = {
                Text(
                    text = if (showDetail) "Hide" else "Show",
                    style = Waymark.type.control,
                    color = Waymark.colors.textDim,
                    modifier = Modifier.clickable { showDetail = !showDetail },
                )
            },
        )

        if (showDetail) {
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                WaymarkTextField(
                    value = state.departureTerminal,
                    onValueChange = viewModel::setDepartureTerminal,
                    label = "Terminal out",
                    placeholder = "I",
                    mono = true,
                    modifier = Modifier.weight(1f),
                )
                WaymarkTextField(
                    value = state.arrivalTerminal,
                    onValueChange = viewModel::setArrivalTerminal,
                    label = "Terminal in",
                    placeholder = "5",
                    mono = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                WaymarkTextField(
                    value = state.aircraft,
                    onValueChange = viewModel::setAircraft,
                    label = "Aircraft",
                    placeholder = "Boeing 777",
                    modifier = Modifier.weight(1f),
                )
                WaymarkTextField(
                    value = state.cabin,
                    onValueChange = viewModel::setCabin,
                    label = "Cabin",
                    placeholder = "Economy",
                    modifier = Modifier.weight(1f),
                )
            }

            WaymarkTextField(
                value = state.confirmationCode,
                onValueChange = viewModel::setConfirmationCode,
                label = "Record locator",
                placeholder = "K7QH2P",
                mono = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.party.filter { it.id in state.selectedTravelers }.forEach { traveler ->
                FieldLabel(traveler.displayName)
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    WaymarkTextField(
                        value = state.eTickets[traveler.id].orEmpty(),
                        onValueChange = { viewModel.setETicket(traveler.id, it) },
                        placeholder = "E-ticket",
                        mono = true,
                        modifier = Modifier.weight(2f),
                    )
                    WaymarkTextField(
                        value = state.seats[traveler.id].orEmpty(),
                        onValueChange = { viewModel.setSeat(traveler.id, it) },
                        placeholder = "21A",
                        mono = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (state.party.size > 1) {
            SectionHeader("Who is on it")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                state.party.forEach { traveler ->
                    OptionChip(
                        text = traveler.displayName,
                        selected = traveler.id in state.selectedTravelers,
                        onClick = { viewModel.toggleTraveler(traveler.id) },
                        leading = {
                            PartyMark(
                                initials = traveler.initials,
                                active = traveler.id in state.selectedTravelers,
                                size = 18.dp,
                            )
                        },
                    )
                }
            }
        }

        PrimaryButton(
            text = "Add to itinerary",
            icon = WaymarkIcons.Check,
            onClick = {
                viewModel.save {
                    if (!reminders.granted) reminders.request()
                    onDone()
                }
            },
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WaymarkSpacing.small),
        )
    }
}

/**
 * A station field that resolves as you type. The suggestions are the bundled
 * airport table — a reference list, not a lookup service — and picking one only
 * fills in the code the traveler was already typing.
 */
@Composable
private fun StationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suggestions: List<Airports.Airport>,
    onPick: (String) -> Unit,
) {
    val resolved = Airports.find(value)

    Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
        WaymarkTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            // The resolved station goes in the hint line rather than a trailing
            // slot: at three characters the field is mostly empty anyway, and
            // "San Francisco International" needs the width.
            hint = resolved?.let { "${it.city} · ${it.name}" },
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                suggestions.forEach { airport ->
                    OptionChip(
                        text = "${airport.code} · ${airport.city}",
                        selected = false,
                        onClick = { onPick(airport.code) },
                    )
                }
            }
        } else if (value.length >= 3 && resolved == null) {
            EmptyLine("No station called \"$value\" in the bundled list.")
        }
    }
}

/**
 * Block time, computed from what has been entered so far — the one number the
 * app can derive rather than ask for, and the quickest way to catch a time set
 * against the wrong airport.
 */
private fun AddFlightUiState.blockSummary(): String? {
    if (!canSave) return null
    val from = originAirport ?: return null
    val to = destinationAirport ?: return null
    val leaves =
        ZonedDateTime.of(date, departTime ?: return null, Segment.zoneOrUtc(from.timeZoneId))
    var lands =
        ZonedDateTime.of(date, arriveTime ?: return null, Segment.zoneOrUtc(to.timeZoneId))
    if (!lands.toInstant().isAfter(leaves.toInstant())) lands = lands.plusDays(1)

    val minutes = Duration.between(leaves, lands).toMinutes().toInt()
    val dayShift = TimeText.dayOffsetSuffix(leaves, lands)
    return buildString {
        append(TimeText.duration(minutes))
        append(" in the air")
        dayShift?.let { append(", lands $it") }
    }
}
