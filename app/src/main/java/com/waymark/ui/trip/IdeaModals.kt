package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.ui.components.ChoiceCard
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.DateField
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.StepFlow
import com.waymark.ui.components.StepFooter
import com.waymark.ui.components.StepQuestion
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.LocalTime

/**
 * Add something the traveler heard about, one question at a time.
 *
 * Three steps: what kind, what it is, and — only when a trip touches more than
 * one city — where. The note rides along with the name because "the bookshop
 * off Charing Cross Road" and "Anna said the upstairs room" are one thought.
 */
@Composable
fun AddIdeaModal(
    cities: List<String>,
    onDismiss: () -> Unit,
    onAdd: (title: String, kind: IdeaKind, city: String, note: String?) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf<IdeaKind?>(null) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var city by remember { mutableStateOf(cities.firstOrNull().orEmpty()) }

    val needsCity = cities.size > 1
    val steps = if (needsCity) listOf("Kind", "What", "Where") else listOf("Kind", "What")
    val last = steps.lastIndex

    StepFlow(
        steps = steps,
        index = step,
        title = "Add to the list",
        onStep = { step = it },
        onDismiss = onDismiss,
    ) { current ->
        when (current) {
            0 -> {
                StepQuestion("What sort of thing is it?")
                IdeaKind.entries.forEach { option ->
                    ChoiceCard(
                        title = option.label,
                        detail = option.hint,
                        icon = iconFor(option),
                        selected = kind == option,
                        // Choosing is the answer; there is nothing else on
                        // this step to confirm.
                        onClick = { kind = option; step = 1 },
                    )
                }
            }

            1 -> {
                StepQuestion(
                    when (kind) {
                        IdeaKind.EAT -> "What is it, and where did you hear about it?"
                        IdeaKind.SHOP -> "What are you looking for?"
                        else -> "What is it?"
                    }
                )
                WaymarkTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = when (kind) {
                        IdeaKind.EAT -> "Cacio e pepe at Da Enzo"
                        IdeaKind.SHOP -> "The paper shop near the Pantheon"
                        IdeaKind.DO -> "Cooking class in Trastevere"
                        else -> "Sir John Soane's Museum"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                WaymarkTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Anything worth remembering",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                StepFooter(
                    onBack = { step = 0 },
                    forwardLabel = if (needsCity) "Next" else "Add",
                    forwardEnabled = title.isNotBlank(),
                    finishing = !needsCity,
                    onForward = {
                        if (needsCity) {
                            step = 2
                        } else {
                            onAdd(title, kind ?: IdeaKind.SEE, city, note.ifBlank { null })
                        }
                    },
                )
            }

            else -> {
                StepQuestion("Which city?")
                cities.forEach { option ->
                    ChoiceCard(
                        title = option,
                        detail = "",
                        icon = WaymarkIcons.MapPin,
                        selected = city.equals(option, ignoreCase = true),
                        onClick = { city = option },
                    )
                }
                StepFooter(
                    onBack = { step = 1 },
                    forwardLabel = "Add",
                    forwardEnabled = city.isNotBlank(),
                    finishing = true,
                    onForward = {
                        onAdd(title, kind ?: IdeaKind.SEE, city, note.ifBlank { null })
                    },
                )
            }
        }
        // The first step has no footer at all: picking a card moves on, and
        // there is nowhere behind it to go.
        if (current == 0) {
            Footnote("Nothing is saved until the last step.")
        }
    }
}

/**
 * Promote an idea onto the timeline. Duration is pre-filled from the idea's own
 * estimate, because a museum is not a coffee.
 */
@Composable
fun ScheduleIdeaModal(
    idea: Idea,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSchedule: (LocalDate, LocalTime, Int?) -> Unit,
) {
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var minutesText by remember { mutableStateOf((idea.typicalMinutes ?: 60).toString()) }

    val minutes = minutesText.filter { it.isDigit() }.toIntOrNull()

    WaymarkModal(title = idea.title, eyebrow = "Put it on the day", onDismiss = onDismiss) {
        Text(
            text = idea.note ?: "It moves to the timeline and off the list.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = date,
                onValueChange = { date = it },
                label = "Date",
                modifier = Modifier.weight(1.3f),
            )
            TimeField(
                value = time,
                onValueChange = { time = it },
                label = "From",
                modifier = Modifier.weight(1f),
            )
        }
        WaymarkTextField(
            value = minutesText,
            onValueChange = { minutesText = it },
            label = "How long, in minutes",
            placeholder = "90",
            mono = true,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(WaymarkSpacing.tight))
        PrimaryButton(
            text = "Schedule it",
            onClick = { onSchedule(date, time, minutes) },
            modifier = Modifier.fillMaxWidth(),
        )
        MutedButton(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** The glyph for each kind, shared by the picker and the board. */
fun iconFor(kind: IdeaKind): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    IdeaKind.SEE -> WaymarkIcons.Sights
    IdeaKind.EAT -> WaymarkIcons.Fork
    IdeaKind.DO -> WaymarkIcons.Activity
    IdeaKind.SHOP -> WaymarkIcons.Shop
}
