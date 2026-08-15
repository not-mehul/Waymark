package com.waymark.ui.add

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.TimeText
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The headline feature, and deliberately the shortest screen in the app: a
 * flight number and a date produce a fully populated segment — route,
 * terminals, aircraft, block time — with no network and no account.
 */
@Composable
fun AddFlightScreen(
    viewModel: AddFlightViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors

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
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onDone,
                )
                Spacer(Modifier.weight(1f))
                SectionLabel("Add flight")
            }

            Text(
                text = "Flight number",
                style = Waymark.type.screenTitle,
                color = colors.textHeading,
            )
            Text(
                text = "Type it as printed on the ticket. Two or three letters, then digits.",
                style = Waymark.type.tagline,
                color = colors.textDim,
            )

            Spacer(Modifier.height(WaymarkSpacing.snug))

            Row(
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                verticalAlignment = Alignment.Bottom,
            ) {
                WaymarkTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    label = "Designator",
                    placeholder = "BA286",
                    mono = true,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    date = state.date,
                    onDateChange = viewModel::onDateChange,
                    modifier = Modifier.weight(1f),
                )
            }

            PrimaryButton(
                text = if (state.searching) "Looking up" else "Look up",
                icon = WaymarkIcons.Search,
                onClick = viewModel::lookUp,
                enabled = state.canLookUp && !state.searching,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.parseError && state.input.isNotBlank()) {
                Text(
                    text = "That does not read as a flight number.",
                    style = Waymark.type.hint,
                    color = colors.danger,
                )
            }

            state.plan?.let { plan ->
                Spacer(Modifier.height(WaymarkSpacing.snug))
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column {
                            Text(
                                text = plan.designator,
                                style = Waymark.type.sectionHeading,
                                color = colors.accentAmber,
                            )
                            Text(
                                text = plan.carrierName,
                                style = Waymark.type.hint,
                                color = colors.textDim,
                            )
                        }
                        Text(
                            text = plan.source,
                            style = Waymark.type.fieldLabel,
                            color = colors.textFaint,
                        )
                    }

                    Spacer(Modifier.height(WaymarkSpacing.small))
                    Hairline()
                    Spacer(Modifier.height(WaymarkSpacing.small))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plan.origin.code.orEmpty(),
                                style = Waymark.type.verdict,
                                color = colors.textHeading,
                            )
                            Text(
                                text = TimeText.clock(plan.departure),
                                style = Waymark.type.data,
                                color = colors.textMuted,
                            )
                            Text(
                                text = plan.origin.city +
                                    (plan.departureTerminal?.let { " · T$it" } ?: ""),
                                style = Waymark.type.dataSmall,
                                color = colors.textDim,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = plan.destination.code.orEmpty(),
                                style = Waymark.type.verdict,
                                color = colors.textHeading,
                            )
                            Text(
                                text = TimeText.clock(plan.arrival) +
                                    (TimeText.dayOffsetSuffix(plan.departure, plan.arrival)
                                        ?.let { " $it" } ?: ""),
                                style = Waymark.type.data,
                                color = colors.textMuted,
                            )
                            Text(
                                text = plan.destination.city +
                                    (plan.arrivalTerminal?.let { " · T$it" } ?: ""),
                                style = Waymark.type.dataSmall,
                                color = colors.textDim,
                            )
                        }
                    }

                    Spacer(Modifier.height(WaymarkSpacing.medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large)) {
                        Stat(value = TimeText.duration(plan.blockMinutes), label = "Block time")
                        Stat(value = plan.aircraft, label = "Aircraft")
                    }
                }
            }

            state.unknownDesignator?.let { designator ->
                Spacer(Modifier.height(WaymarkSpacing.snug))
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${designator.normalised} is not in the bundled schedule.",
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                    )
                    Text(
                        text = "Enter it once and it behaves like any other flight.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                    Spacer(Modifier.height(WaymarkSpacing.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        WaymarkTextField(
                            value = state.manualOrigin,
                            onValueChange = { viewModel.setManual(origin = it) },
                            label = "From",
                            placeholder = "SFO",
                            mono = true,
                            modifier = Modifier.weight(1f),
                        )
                        WaymarkTextField(
                            value = state.manualDestination,
                            onValueChange = { viewModel.setManual(destination = it) },
                            label = "To",
                            placeholder = "LHR",
                            mono = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        WaymarkTextField(
                            value = state.manualDepartTime,
                            onValueChange = { viewModel.setManual(departTime = it) },
                            label = "Departs (local)",
                            placeholder = "16:20",
                            mono = true,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                        WaymarkTextField(
                            value = state.manualArriveTime,
                            onValueChange = { viewModel.setManual(arriveTime = it) },
                            label = "Arrives (local)",
                            placeholder = "10:55",
                            mono = true,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    Text(
                        text = "Times are local to each airport. An arrival earlier than the " +
                            "departure is taken as the next day.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
            }

            if (state.plan != null || state.canSaveManually) {
                SectionHeader("Who is on it")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                ) {
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

                SectionHeader("Vault")
                WaymarkTextField(
                    value = state.confirmationCode,
                    onValueChange = viewModel::setConfirmationCode,
                    label = "Record locator",
                    placeholder = "K7QH2P",
                    mono = true,
                    hint = "Stored encrypted; masked until you ask for it.",
                    modifier = Modifier.fillMaxWidth(),
                )

                state.party.filter { it.id in state.selectedTravelers }.forEach { traveler ->
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    FieldLabel(traveler.displayName)
                    Spacer(Modifier.height(WaymarkSpacing.tight))
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        WaymarkTextField(
                            value = state.eTickets[traveler.id].orEmpty(),
                            onValueChange = { viewModel.setETicket(traveler.id, it) },
                            placeholder = "E-ticket 125-2364119807",
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

                Spacer(Modifier.height(WaymarkSpacing.medium))
                PrimaryButton(
                    text = "Add to itinerary",
                    icon = WaymarkIcons.Check,
                    onClick = { viewModel.save(onDone) },
                    modifier = Modifier.fillMaxWidth(),
                )
                MutedButton(
                    text = "Cancel",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorialNote(
                term = "Bundled",
                body = "The schedule ships with the app. A live feed, when one is " +
                    "configured, refines the result; it is never required.",
                modifier = Modifier.padding(top = WaymarkSpacing.small),
            )
            Spacer(Modifier.height(WaymarkSpacing.section))
        }
    }
}

@Composable
private fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(date) { mutableStateOf(date.toString()) }
    WaymarkTextField(
        value = text,
        onValueChange = { entered ->
            text = entered
            try {
                onDateChange(LocalDate.parse(entered.trim()))
            } catch (error: DateTimeParseException) {
                // Left as typed until it parses; no shouting mid-entry.
            }
        },
        label = "Date",
        placeholder = "2026-05-14",
        mono = true,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}
