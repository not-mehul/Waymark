package com.waymark.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.SegmentKind
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val KINDS = listOf(SegmentKind.LODGING, SegmentKind.GROUND, SegmentKind.EXPERIENCE)

/** Everything that is not a flight: a stay, a transfer, something booked. */
@Composable
fun AddPlanScreen(
    viewModel: AddPlanViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors

    ScreenScaffold(
        title = when (state.kind) {
            SegmentKind.LODGING -> "Somewhere to stay"
            SegmentKind.GROUND -> "Getting across"
            else -> "Something booked"
        },
        onBack = onDone,
        spacing = WaymarkSpacing.small,
    ) {
        SegmentedToggle(
            options = KINDS.map { label(it) },
            selectedIndex = KINDS.indexOf(state.kind).coerceAtLeast(0),
            onSelect = { viewModel.setKind(KINDS[it]) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(WaymarkSpacing.snug))

        WaymarkTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = when (state.kind) {
                SegmentKind.LODGING -> "Property"
                SegmentKind.GROUND -> "Service"
                else -> "What"
            },
            placeholder = when (state.kind) {
                SegmentKind.LODGING -> "The Bloomsbury Rooms"
                SegmentKind.GROUND -> "Eurostar 9014"
                else -> "Musée de l'Orangerie"
            },
            modifier = Modifier.fillMaxWidth(),
        )

        WaymarkTextField(
            value = state.originQuery,
            onValueChange = viewModel::setOrigin,
            label = if (state.needsDestination) "From" else "Where",
            placeholder = if (state.needsDestination) "LHR" else "Bloomsbury, London",
            hint = "A station code resolves to an airport with coordinates; " +
                "anything else is stored as typed.",
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.needsDestination) {
            WaymarkTextField(
                value = state.destinationQuery,
                onValueChange = viewModel::setDestination,
                label = "To",
                placeholder = "Gare du Nord",
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader("Mode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                listOf(
                    GroundMode.TRAIN,
                    GroundMode.TRANSIT,
                    GroundMode.TAXI,
                    GroundMode.FERRY,
                ).forEach { mode ->
                    OptionChip(
                        text = mode.label,
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                    )
                }
            }
        }

        SectionHeader("When")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateEntry(
                value = state.startDate,
                label = if (state.kind == SegmentKind.LODGING) "Check in" else "Date",
                onChange = viewModel::setStartDate,
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = state.startTime,
                onValueChange = viewModel::setStartTime,
                label = "From",
                placeholder = "15:00",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            if (state.kind == SegmentKind.LODGING) {
                DateEntry(
                    value = state.endDate,
                    label = "Check out",
                    onChange = viewModel::setEndDate,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            WaymarkTextField(
                value = state.endTime,
                onValueChange = viewModel::setEndTime,
                label = "Until",
                placeholder = "11:00",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader("Who")
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

        SectionHeader("Details")
        WaymarkTextField(
            value = state.vendor,
            onValueChange = viewModel::setVendor,
            label = "Booked with",
            placeholder = "Direct booking",
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = state.confirmationCode,
            onValueChange = viewModel::setConfirmationCode,
            label = "Confirmation code",
            placeholder = "BLM-4471-QE",
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            label = "Note",
            placeholder = "Reception staffed from 07:00",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(WaymarkSpacing.medium))
        PrimaryButton(
            text = "Add to itinerary",
            icon = WaymarkIcons.Check,
            onClick = { viewModel.save(onDone) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        MutedButton(text = "Cancel", onClick = onDone, modifier = Modifier.fillMaxWidth())

        Footnote("Times are the local clock of the place they happen.")
    }
}

@Composable
private fun DateEntry(
    value: LocalDate,
    label: String,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    WaymarkTextField(
        value = text,
        onValueChange = { entered ->
            text = entered
            try {
                onChange(LocalDate.parse(entered.trim()))
            } catch (error: DateTimeParseException) {
                // Wait for a complete date rather than complaining per keystroke.
            }
        },
        label = label,
        placeholder = "2026-05-14",
        mono = true,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}

private fun label(kind: SegmentKind): String = when (kind) {
    SegmentKind.LODGING -> "Stay"
    SegmentKind.GROUND -> "Ground"
    SegmentKind.EXPERIENCE -> "Booking"
    SegmentKind.FLIGHT -> "Flight"
}
