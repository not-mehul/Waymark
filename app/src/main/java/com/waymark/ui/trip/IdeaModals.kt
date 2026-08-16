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
import com.waymark.ui.components.DateField
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.LocalTime

/** Add something the traveler heard about, rather than something the guide offered. */
@Composable
fun AddIdeaModal(
    cities: List<String>,
    onDismiss: () -> Unit,
    onAdd: (title: String, kind: IdeaKind, city: String, note: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(IdeaKind.SEE) }
    var city by remember { mutableStateOf(cities.firstOrNull().orEmpty()) }
    var note by remember { mutableStateOf("") }

    WaymarkModal(title = "Add an idea", eyebrow = "The list", onDismiss = onDismiss) {
        WaymarkTextField(
            value = title,
            onValueChange = { title = it },
            label = "What",
            placeholder = "The bookshop off Charing Cross Road",
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("What kind")
        // Four cards, each carrying the sentence that makes the word obvious.
        // The old six chips read "See · Eat · Table · Walk · Do · Shop", which
        // required already knowing what the app meant by "Table".
        IdeaKind.entries.forEach { option ->
            ChoiceCard(
                title = option.label,
                detail = option.hint,
                icon = iconFor(option),
                selected = kind == option,
                onClick = { kind = option },
            )
        }

        if (cities.size > 1) {
            FieldLabel("Where")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                cities.take(4).forEach { option ->
                    OptionChip(
                        text = option,
                        selected = city.equals(option, ignoreCase = true),
                        onClick = { city = option },
                    )
                }
            }
        }

        WaymarkTextField(
            value = note,
            onValueChange = { note = it },
            label = "Why",
            placeholder = "Who recommended it, what to order",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = "Add to the list",
            onClick = { onAdd(title, kind, city, note.ifBlank { null }) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
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
