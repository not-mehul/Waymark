package com.waymark.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.waymark.ui.components.DateField
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A new itinerary needs four facts, and nothing more than four fields to
 * collect them.
 *
 * There were two preset chips here — "A week out" and "A long weekend" — which
 * were a guess at the trip somebody was about to plan, offered above the two
 * date fields that already answer the question in two taps. The length of the
 * trip is now the eyebrow above the title rather than a line of its own: it
 * updates as the dates move, and it is a fact about what has been entered, not
 * a field.
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

    val days = ChronoUnit.DAYS.between(start, end) + 1

    WaymarkModal(
        title = "New itinerary",
        eyebrow = if (days == 1L) "One day" else "$days days",
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

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
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
            MutedButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Create",
                icon = WaymarkIcons.Check,
                onClick = { onCreate(name, destination, start, end) },
                enabled = name.isNotBlank() || destination.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
