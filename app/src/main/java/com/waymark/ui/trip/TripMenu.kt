package com.waymark.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.rememberReminderPermission
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * Everything that is not one of the three tabs.
 *
 * The header used to carry five ghost icons and a theme toggle in a row, which
 * meant six unlabelled glyphs competing with the trip's name for the top of
 * the screen and no way to tell the compass from the check. They live here
 * now, named, one tap further away — which is the right distance for things
 * you reach for once a trip rather than once a minute.
 */
@Composable
fun TripMenu(
    onDismiss: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMap: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    WaymarkModal(title = "This trip", onDismiss = onDismiss) {
        MenuRow(WaymarkIcons.Map, "The map", "Every leg, on a chart or a globe") {
            onDismiss(); onOpenMap()
        }
        MenuRow(WaymarkIcons.Chart, "The numbers", "Distance, days, legs, carbon") {
            onDismiss(); onOpenAnalytics()
        }
        MenuRow(WaymarkIcons.Compass, "Destination notes", "Local knowledge, bundled") {
            onDismiss(); onOpenInsights()
        }

        Hairline()

        MenuRow(WaymarkIcons.Share, "Export as markdown", "The whole trip, as text") {
            onDismiss(); onExport()
        }

        Hairline()

        // The only switch in the app that lives outside it: whether Android
        // will let Waymark say "this leaves in three hours". Shown here so a
        // traveler who declined once has somewhere to go back to.
        val reminders = rememberReminderPermission()
        MenuRow(
            icon = WaymarkIcons.Bell,
            title = "Departure reminders",
            detail = if (reminders.granted) {
                "On · a few hours before a flight"
            } else {
                "Off · tap to allow notifications"
            },
            onClick = { reminders.request() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Appearance",
                style = Waymark.type.bodySmall,
                color = Waymark.colors.textBody,
            )
            ThemeToggle()
        }

        Hairline()

        MenuRow(
            icon = WaymarkIcons.Pencil,
            title = "Edit this trip",
            detail = "Name, destinations, first and last day",
        ) {
            onDismiss(); onEdit()
        }

        // Last, and the only row that speaks in the danger token — a
        // destructive action should not sit in the same visual rank as
        // "The numbers".
        MenuRow(
            icon = WaymarkIcons.Trash,
            title = "Delete this trip",
            detail = "Everything on it goes with it",
            tint = Waymark.colors.danger,
        ) {
            onDismiss(); onDelete()
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val colors = Waymark.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        WaymarkIcon(icon, tint = tint ?: colors.accentAmber, size = 17.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Waymark.type.bodySmall, color = tint ?: colors.textBody)
            Text(text = detail, style = Waymark.type.hint, color = colors.textDim)
        }
        WaymarkIcon(WaymarkIcons.ArrowRight, tint = colors.textFaint, size = 14.dp)
    }
}
