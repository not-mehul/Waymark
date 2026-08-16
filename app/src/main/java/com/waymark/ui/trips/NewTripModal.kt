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
import com.waymark.ui.components.DateField
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
            DateField(
                value = start,
                onValueChange = { picked ->
                    // Dragging the first day past the last drags the trip with
                    // it rather than producing a negative-length itinerary.
                    val span = ChronoUnit.DAYS.between(start, end)
                    start = picked
                    if (end.isBefore(picked)) end = picked.plusDays(span.coerceAtLeast(0))
                },
                label = "First day",
                modifier = Modifier.weight(1f),
            )
            DateField(
                value = end,
                onValueChange = { end = it },
                label = "Last day",
                earliest = start,
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
                },
            )
            OptionChip(
                text = "A long weekend",
                selected = false,
                onClick = {
                    start = LocalDate.now().plusWeeks(2)
                    end = start.plusDays(3)
                },
            )
        }

        Text(
            text = "${ChronoUnit.DAYS.between(start, end) + 1} days",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            PrimaryButton(
                text = "Create",
                onClick = { onCreate(name, destination, start, end) },
                enabled = name.isNotBlank() || destination.isNotBlank(),
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
