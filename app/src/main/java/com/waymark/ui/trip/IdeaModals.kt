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
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
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
    var kind by remember { mutableStateOf(IdeaKind.SIGHT) }
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

        FieldLabel("Kind")
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            listOf(IdeaKind.SIGHT, IdeaKind.EATERY, IdeaKind.DISH).forEach { option ->
                OptionChip(
                    text = option.label,
                    selected = kind == option,
                    onClick = { kind = option },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            listOf(IdeaKind.WALK, IdeaKind.ACTIVITY, IdeaKind.SHOP).forEach { option ->
                OptionChip(
                    text = option.label,
                    selected = kind == option,
                    onClick = { kind = option },
                )
            }
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
    var dateText by remember { mutableStateOf(defaultDate.toString()) }
    var timeText by remember { mutableStateOf("10:00") }
    var minutesText by remember { mutableStateOf((idea.typicalMinutes ?: 60).toString()) }

    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val time = parseClock(timeText)
    val minutes = minutesText.filter { it.isDigit() }.toIntOrNull()

    WaymarkModal(title = idea.title, eyebrow = "Put it on the day", onDismiss = onDismiss) {
        Text(
            text = idea.note ?: "It moves to the timeline and off the list.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            WaymarkTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = "Date",
                placeholder = "2026-05-16",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1.4f),
            )
            WaymarkTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = "From",
                placeholder = "10:00",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                label = "Minutes",
                placeholder = "90",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        if (date == null || time == null) {
            Text(
                text = "Dates read as year-month-day; times as 24-hour.",
                style = Waymark.type.hint,
                color = Waymark.colors.danger,
            )
        }

        Spacer(Modifier.height(WaymarkSpacing.tight))
        PrimaryButton(
            text = "Schedule it",
            onClick = {
                if (date != null && time != null) onSchedule(date, time, minutes)
            },
            enabled = date != null && time != null,
            modifier = Modifier.fillMaxWidth(),
        )
        MutedButton(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** "0930", "09:30" and "9:30" all mean the same thing. */
private fun parseClock(text: String): LocalTime? {
    val digits = text.filter { it.isDigit() }
    if (digits.length !in 3..4) return null
    val padded = digits.padStart(4, '0')
    val hour = padded.substring(0, 2).toIntOrNull() ?: return null
    val minute = padded.substring(2, 4).toIntOrNull() ?: return null
    if (hour > 23 || minute > 59) return null
    return runCatching { LocalTime.of(hour, minute) }.getOrNull()
}
