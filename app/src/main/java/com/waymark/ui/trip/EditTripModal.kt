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
import com.waymark.domain.model.Trip
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
 * The four facts a trip was created with, editable afterwards.
 *
 * Dates in particular: a trip entered before the flights were booked is a
 * guess, and it was previously a guess you were stuck with — the only way to
 * correct a date was to delete the trip and re-enter everything on it. Moving
 * the first day drags the last one with it, the same way the compose form does,
 * so shifting a trip by a week is one field rather than two.
 *
 * Bookings keep their own dates. They are absolute instants and a trip's window
 * is only the frame they are read against; the timeline will simply show a
 * booking sitting outside it, which is a truer thing to show than a booking
 * silently moved by a week.
 */
@Composable
fun EditTripModal(
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (name: String, destination: String, start: LocalDate, end: LocalDate) -> Unit,
) {
    var name by remember(trip.id) { mutableStateOf(trip.name) }
    var destination by remember(trip.id) { mutableStateOf(trip.destinationSummary) }
    var start by remember(trip.id) { mutableStateOf(trip.startDate()) }
    var end by remember(trip.id) { mutableStateOf(trip.endDate()) }

    val days = ChronoUnit.DAYS.between(start, end) + 1

    WaymarkModal(
        title = "Edit this trip",
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
                text = "Save",
                icon = WaymarkIcons.Check,
                onClick = { onSave(name, destination, start, end) },
                enabled = name.isNotBlank() || destination.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
