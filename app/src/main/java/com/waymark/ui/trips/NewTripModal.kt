package com.waymark.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A new itinerary needs four facts. Dates are typed as ISO — unambiguous,
 * short, and the same in every locale — with two shortcuts for the common case.
 */
@Composable
fun NewTripModal(
    onDismiss: () -> Unit,
    onCreate: (name: String, destination: String, start: LocalDate, end: LocalDate) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now().plusWeeks(2)) }
    var end by remember { mutableStateOf(LocalDate.now().plusWeeks(2).plusDays(6)) }
    var startText by remember { mutableStateOf(start.toString()) }
    var endText by remember { mutableStateOf(end.toString()) }

    fun parse(text: String): LocalDate? = try {
        LocalDate.parse(text.trim())
    } catch (error: DateTimeParseException) {
        null
    }

    val startValid = parse(startText) != null
    val endValid = parse(endText)?.let { !it.isBefore(parse(startText) ?: it) } == true

    WaymarkModal(
        title = "New itinerary",
        eyebrow = "Compose",
        onDismiss = onDismiss,
    ) {
        WaymarkTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "London & Paris",
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = destination,
            onValueChange = { destination = it },
            label = "Destinations",
            placeholder = "London, then Paris",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small)) {
            WaymarkTextField(
                value = startText,
                onValueChange = {
                    startText = it
                    parse(it)?.let { date -> start = date }
                },
                label = "First day",
                placeholder = "2026-05-14",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            WaymarkTextField(
                value = endText,
                onValueChange = {
                    endText = it
                    parse(it)?.let { date -> end = date }
                },
                label = "Last day",
                placeholder = "2026-05-20",
                mono = true,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            OptionChip(
                text = "A week out",
                selected = false,
                onClick = {
                    start = LocalDate.now().plusWeeks(1)
                    end = start.plusDays(6)
                    startText = start.toString()
                    endText = end.toString()
                },
            )
            OptionChip(
                text = "A long weekend",
                selected = false,
                onClick = {
                    start = LocalDate.now().plusWeeks(2)
                    end = start.plusDays(3)
                    startText = start.toString()
                    endText = end.toString()
                },
            )
        }

        if (!startValid || !endValid) {
            Text(
                text = "Dates read as year-month-day, and the last day cannot precede the first.",
                style = Waymark.type.hint,
                color = Waymark.colors.danger,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            PrimaryButton(
                text = "Create",
                onClick = { onCreate(name, destination, start, end) },
                enabled = startValid && endValid && (name.isNotBlank() || destination.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            )
            MutedButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
