package com.waymark.ui.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.Airports
import com.waymark.data.share.Bcbp
import com.waymark.data.share.BookingText
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.logic.TimeText
import com.waymark.ui.components.DateField
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.LocalReminderPermission
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.share.Barcodes
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import kotlinx.coroutines.launch

/**
 * A flight, typed in.
 *
 * Waymark does not call a schedule service, so nothing here fills itself in.
 * What the app does contribute is what an airport code already implies —
 * coordinates and a time zone — which is enough to put the leg on the map and
 * to work out that a 22:20 departure lands the following afternoon.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    // A boarding pass's barcode carries the flight, the airports, the date and
    // the seat; everything but the clock times. Read from a screenshot or a
    // photo — no camera permission, nothing sent anywhere.
    val boardingPass = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val legs = Barcodes.read(context, uri)?.let { Bcbp.parse(it) }.orEmpty()
            val leg = legs.firstOrNull()
            notice = if (leg == null) {
                "No boarding pass code could be read in that picture."
            } else {
                viewModel.prefill(
                    FlightDraft(
                        designator = leg.designator,
                        date = leg.date,
                        origin = leg.origin,
                        destination = leg.destination,
                        confirmationCode = leg.reference,
                        cabin = leg.cabin,
                        seat = leg.seat,
                    )
                )
                "Read from the pass. Add the departure and arrival times; a pass does not carry them." +
                    if (legs.size > 1) " It has ${legs.size} flights — this is the first." else ""
            }
        }
    }

    ScreenScaffold(title = "Add flight", onBack = onDone, spacing = WaymarkSpacing.small) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
        ) {
            OptionChip(
                text = "From a boarding pass",
                selected = false,
                onClick = {
                    boardingPass.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                leading = { WaymarkIcon(WaymarkIcons.Scan, tint = Waymark.colors.textDim, size = 15.dp) },
            )
            OptionChip(
                text = "Paste an email",
                selected = false,
                onClick = {
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData
                            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        val reading = BookingText.read(text)
                        val found = reading.flights.firstOrNull()
                        notice = if (found == null) {
                            "Nothing on the clipboard reads as a flight. " +
                                "Copy the part of the email with the flight number in it."
                        } else {
                            viewModel.prefill(
                                FlightDraft(
                                    designator = found.designator,
                                    date = found.date,
                                    origin = found.origin,
                                    destination = found.destination,
                                    departs = found.departs,
                                    arrives = found.arrives,
                                    confirmationCode = reading.reference,
                                )
                            )
                            "Filled in from the clipboard — check it before saving." +
                                if (reading.flights.size > 1) {
                                    " It mentions ${reading.flights.size} flights; this is the first."
                                } else {
                                    ""
                                }
                        }
                    }
                },
                leading = { WaymarkIcon(WaymarkIcons.Clipboard, tint = Waymark.colors.textDim, size = 15.dp) },
            )
        }
        notice?.let { message ->
            NoticeBanner(icon = WaymarkIcons.Info, headline = message, onDismiss = { notice = null })
        }

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
            suggestions = state.originSuggestions,
            onPick = viewModel::setOrigin,
        )
        StationField(
            value = state.destination,
            onValueChange = viewModel::setDestination,
            label = "To",
            placeholder = "LHR",
            suggestions = state.destinationSuggestions,
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

        state.leg?.let { leg ->
            Text(
                text = blockSummary(leg),
                style = Waymark.type.data,
                color = Waymark.colors.accentAmber,
                modifier = Modifier.padding(top = WaymarkSpacing.tight),
            )
        }
        state.timeProblem?.let { problem ->
            Text(
                text = problem,
                style = Waymark.type.hint,
                color = Waymark.colors.danger,
            )
        }

        // The arrival day is worked out from the two clocks, and can be said
        // outright when the schedule says otherwise.
        if (state.departTime != null && state.arriveTime != null) {
            val shown = state.arrivalDayOffset ?: state.leg?.dayOffset
            FieldLabel("Lands")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                LegTimes.DAY_OFFSETS.forEach { offset ->
                    OptionChip(
                        text = when {
                            offset == 0 -> "Same day"
                            offset > 0 -> "+$offset"
                            else -> "−${-offset}"
                        },
                        selected = shown == offset,
                        onClick = { viewModel.setArrivalDayOffset(offset) },
                    )
                }
            }
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
                value = state.operatedBy,
                onValueChange = viewModel::setOperatedBy,
                label = "Operated by",
                placeholder = "Only if another airline flies it",
                modifier = Modifier.fillMaxWidth(),
            )

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
                    reminders.requestOnce()
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
 * Block time — the one number the app can derive rather than ask for, and the
 * quickest way to catch a time set against the wrong airport.
 */
private fun blockSummary(leg: LegTimes.Leg): String = buildString {
    append(TimeText.duration(leg.minutes.toInt()))
    append(" in the air")
    TimeText.dayOffsetSuffix(leg.departure, leg.arrival)?.let { append(", lands $it") }
}
