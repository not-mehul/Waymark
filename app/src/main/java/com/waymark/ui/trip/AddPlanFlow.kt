package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.ChoiceCard
import com.waymark.ui.components.ChoiceChip
import com.waymark.ui.components.DateField
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.StepFlow
import com.waymark.ui.components.StepFooter
import com.waymark.ui.components.StepQuestion
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.LocalTime

/** Everything a plan needs, gathered a step at a time. */
data class PlanDraft(
    val kind: SegmentKind = SegmentKind.LODGING,
    val title: String = "",
    val where: String = "",
    val destination: String = "",
    val mode: GroundMode = GroundMode.TRAIN,
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(15, 0),
    val endDate: LocalDate = LocalDate.now().plusDays(1),
    val endTime: LocalTime = LocalTime.of(11, 0),
    val vendor: String = "",
    val confirmationCode: String = "",
    val note: String = "",
    val travelerIds: Set<String> = emptySet(),
) {
    val isStay: Boolean get() = kind == SegmentKind.LODGING
    val isGround: Boolean get() = kind == SegmentKind.GROUND
    val named: Boolean get() = title.isNotBlank()
}

/**
 * Adding a hotel, a train or a booked table — as four short questions rather
 * than one screen of eleven fields.
 *
 * The steps are the order the answers arrive in conversation: what sort of
 * thing, what it is called and where, when it happens, and then the reference
 * numbers that are usually in an email somebody has to go and find. Only the
 * first two are needed to save; the rest can be left and filled in later by
 * editing the booking.
 */
@Composable
fun AddPlanFlow(
    party: List<Traveler>,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (PlanDraft) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var draft by remember {
        mutableStateOf(
            PlanDraft(
                startDate = defaultDate,
                endDate = defaultDate.plusDays(1),
                travelerIds = party.map { it.id }.toSet(),
            )
        )
    }

    val steps = listOf("Kind", "What", "When", "Details")

    StepFlow(
        steps = steps,
        index = step,
        title = "Add a plan",
        onStep = { step = it },
        onDismiss = onDismiss,
    ) { current ->
        when (current) {
            0 -> {
                StepQuestion("What are you adding?")
                KINDS.forEach { option ->
                    ChoiceCard(
                        title = titleFor(option),
                        detail = detailFor(option),
                        icon = iconFor(option),
                        selected = draft.kind == option,
                        onClick = {
                            draft = draft.copy(
                                kind = option,
                                // Sensible defaults per kind: a hotel is a
                                // 15:00 check-in and a night long; everything
                                // else is a morning slot on one day.
                                startTime = if (option == SegmentKind.LODGING) {
                                    LocalTime.of(15, 0)
                                } else {
                                    LocalTime.of(9, 0)
                                },
                                endTime = if (option == SegmentKind.LODGING) {
                                    LocalTime.of(11, 0)
                                } else {
                                    LocalTime.of(11, 0)
                                },
                                endDate = if (option == SegmentKind.LODGING) {
                                    draft.startDate.plusDays(1)
                                } else {
                                    draft.startDate
                                },
                            )
                            step = 1
                        },
                    )
                }
                Footnote("Nothing is saved until the last step.")
            }

            1 -> {
                StepQuestion(
                    when (draft.kind) {
                        SegmentKind.LODGING -> "Where are you staying?"
                        SegmentKind.GROUND -> "What is the journey?"
                        else -> "What is booked?"
                    }
                )
                WaymarkTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = when (draft.kind) {
                        SegmentKind.LODGING -> "The Bloomsbury Rooms"
                        SegmentKind.GROUND -> "Eurostar 9014"
                        else -> "Musée de l'Orangerie"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (draft.isGround) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                        listOf(
                            GroundMode.TRAIN,
                            GroundMode.TRANSIT,
                            GroundMode.TAXI,
                            GroundMode.FERRY,
                        ).forEach { mode ->
                            ChoiceChip(
                                title = mode.label,
                                icon = if (mode == GroundMode.TRAIN || mode == GroundMode.TRANSIT) {
                                    WaymarkIcons.Train
                                } else {
                                    WaymarkIcons.Car
                                },
                                selected = draft.mode == mode,
                                onClick = { draft = draft.copy(mode = mode) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        WaymarkTextField(
                            value = draft.where,
                            onValueChange = { draft = draft.copy(where = it) },
                            label = "From",
                            placeholder = "LHR",
                            modifier = Modifier.weight(1f),
                        )
                        WaymarkTextField(
                            value = draft.destination,
                            onValueChange = { draft = draft.copy(destination = it) },
                            label = "To",
                            placeholder = "Gare du Nord",
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    WaymarkTextField(
                        value = draft.where,
                        onValueChange = { draft = draft.copy(where = it) },
                        label = "Where",
                        placeholder = "Bloomsbury, London",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                StepFooter(
                    onBack = { step = 0 },
                    forwardLabel = "Next",
                    forwardEnabled = draft.named,
                    onForward = { step = 2 },
                )
            }

            2 -> {
                StepQuestion(if (draft.isStay) "Which nights?" else "When is it?")
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    DateField(
                        value = draft.startDate,
                        onValueChange = {
                            draft = draft.copy(
                                startDate = it,
                                endDate = if (draft.endDate.isBefore(it)) it else draft.endDate,
                            )
                        },
                        label = if (draft.isStay) "Check in" else "Date",
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        value = draft.startTime,
                        onValueChange = { draft = draft.copy(startTime = it) },
                        label = "From",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    if (draft.isStay) {
                        DateField(
                            value = draft.endDate,
                            onValueChange = { draft = draft.copy(endDate = it) },
                            label = "Check out",
                            earliest = draft.startDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TimeField(
                        value = draft.endTime,
                        onValueChange = { draft = draft.copy(endTime = it) },
                        label = "Until",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (party.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        party.forEach { traveler ->
                            val on = traveler.id in draft.travelerIds
                            OptionChip(
                                text = traveler.displayName,
                                selected = on,
                                onClick = {
                                    draft = draft.copy(
                                        travelerIds = if (on) {
                                            draft.travelerIds - traveler.id
                                        } else {
                                            draft.travelerIds + traveler.id
                                        }
                                    )
                                },
                                leading = {
                                    PartyMark(
                                        initials = traveler.initials,
                                        active = on,
                                        size = 18.dp,
                                    )
                                },
                            )
                        }
                    }
                }

                StepFooter(
                    onBack = { step = 1 },
                    forwardLabel = "Next",
                    forwardEnabled = true,
                    onForward = { step = 3 },
                )
            }

            else -> {
                StepQuestion("Anything else?")
                WaymarkTextField(
                    value = draft.vendor,
                    onValueChange = { draft = draft.copy(vendor = it) },
                    label = "Booked with",
                    placeholder = "Direct booking",
                    modifier = Modifier.fillMaxWidth(),
                )
                WaymarkTextField(
                    value = draft.confirmationCode,
                    onValueChange = { draft = draft.copy(confirmationCode = it) },
                    label = "Confirmation code",
                    placeholder = "BLM-4471-QE",
                    mono = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                WaymarkTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    label = "Note",
                    placeholder = "Reception staffed from 07:00",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                StepFooter(
                    onBack = { step = 2 },
                    forwardLabel = "Add",
                    forwardEnabled = draft.named,
                    finishing = true,
                    onForward = { onSave(draft) },
                )
            }
        }
    }
}

private val KINDS = listOf(SegmentKind.LODGING, SegmentKind.GROUND, SegmentKind.EXPERIENCE)

private fun titleFor(kind: SegmentKind): String = when (kind) {
    SegmentKind.LODGING -> "Somewhere to stay"
    SegmentKind.GROUND -> "Getting around"
    else -> "Something booked"
}

private fun detailFor(kind: SegmentKind): String = when (kind) {
    SegmentKind.LODGING -> "Hotel, rental, a friend's spare room"
    SegmentKind.GROUND -> "Train, taxi, ferry, hire car"
    else -> "A tour, a table, a show, a ticket"
}

private fun iconFor(kind: SegmentKind): ImageVector = when (kind) {
    SegmentKind.LODGING -> WaymarkIcons.Bed
    SegmentKind.GROUND -> WaymarkIcons.Train
    else -> WaymarkIcons.Ticket
}
