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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * Everything that is not one of the five tabs.
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
    onOpenPacking: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMap: () -> Unit,
    onExport: () -> Unit,
) {
    WaymarkModal(title = "This trip", onDismiss = onDismiss) {
        MenuRow(WaymarkIcons.Map, "The map", "Every leg, on a chart or a globe") {
            onDismiss(); onOpenMap()
        }
        MenuRow(WaymarkIcons.Chart, "The numbers", "Distance, days, legs, carbon") {
            onDismiss(); onOpenAnalytics()
        }
        MenuRow(WaymarkIcons.Bag, "Packing", "Lists, drafted from the itinerary") {
            onDismiss(); onOpenPacking()
        }
        MenuRow(WaymarkIcons.Compass, "Destination notes", "Local knowledge, bundled") {
            onDismiss(); onOpenInsights()
        }

        Hairline()

        MenuRow(WaymarkIcons.Share, "Export as markdown", "Everything but the secrets") {
            onDismiss(); onExport()
        }

        Hairline()

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
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    detail: String,
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
        WaymarkIcon(icon, tint = colors.accentAmber, size = 17.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Waymark.type.bodySmall, color = colors.textBody)
            Text(text = detail, style = Waymark.type.hint, color = colors.textDim)
        }
        WaymarkIcon(WaymarkIcons.ArrowRight, tint = colors.textFaint, size = 14.dp)
    }
}
