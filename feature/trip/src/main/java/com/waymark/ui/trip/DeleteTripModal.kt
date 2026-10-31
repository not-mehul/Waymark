package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Trip
import com.waymark.ui.components.DangerButton
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * Deleting a trip is the only irreversible thing the app can do.
 *
 * There is no undo — the rows are gone — so this asks for the trip's name to
 * be typed rather than offering a second button to tap by mistake, and offers
 * the gentler thing first: putting the trip away. That is a deliberate piece
 * of friction, and the only one in the app.
 *
 * The confirmation is generous about how the name is typed: case and
 * surrounding space do not matter. It is a check that the reader stopped and
 * read, not a spelling test.
 */
@Composable
fun DeleteTripModal(
    trip: Trip?,
    bookings: Int,
    onDismiss: () -> Unit,
    onArchiveInstead: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (trip == null) return
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(trip.name.trim(), ignoreCase = true)

    WaymarkModal(title = "Delete this trip", eyebrow = "No undo", onDismiss = onDismiss) {
        Text(
            text = buildString {
                append(trip.name)
                append(" · ")
                append(TimeText.dateRange(trip.startDate(), trip.endDate()))
            },
            style = Waymark.type.bodySmall,
            color = Waymark.colors.textBody,
        )
        Text(
            text = buildString {
                append(bookings)
                append(if (bookings == 1) " booking" else " bookings")
                append(", every idea and every note ")
                append("will be deleted from this device.")
            },
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        WaymarkTextField(
            value = typed,
            onValueChange = { typed = it },
            label = "Type the trip's name to confirm",
            placeholder = trip.name,
            modifier = Modifier.fillMaxWidth(),
        )

        // Most trips people delete are trips that are over. Putting one away
        // keeps it — off the shelf, quiet, back in one tap — at no cost.
        if (!trip.archived) {
            MutedButton(
                text = "Put it away instead",
                icon = WaymarkIcons.Archive,
                onClick = onArchiveInstead,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = "Keep it", onClick = onDismiss, modifier = Modifier.weight(1f))
            DangerButton(
                text = "Delete",
                icon = WaymarkIcons.Trash,
                enabled = matches,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
