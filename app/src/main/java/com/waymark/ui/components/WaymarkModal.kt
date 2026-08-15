package com.waymark.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The modal card: densest surface in the system, a translucent amber hairline,
 * decorative corner brackets, and an entrance that fades and settles rather
 * than bouncing. Dismissible by the close control, the backdrop, or back.
 */
@Composable
fun WaymarkModal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Waymark.colors
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 260),
        label = "modal-appear",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.modalBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(WaymarkSpacing.large),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = appear
                        translationY = (1f - appear) * 16.dp.toPx()
                    }
                    .scale(0.96f + 0.04f * appear)
                    .glow(colors.accentAmber, 0.18f, WaymarkShapes.modal)
                    .clip(WaymarkShapes.modal)
                    .background(colors.modal)
                    .border(1.dp, colors.amber(0.3f), WaymarkShapes.modal)
                    .cornerBrackets(colors.amber(0.5f))
                    // Swallow taps so a click inside the card does not dismiss it.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(WaymarkSpacing.large),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                        eyebrow?.let { SectionLabel(it) }
                        Text(
                            text = title,
                            style = Waymark.type.sectionHeading,
                            color = colors.textHeading,
                        )
                    }
                    GhostIconButton(
                        icon = WaymarkIcons.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                    )
                }
                content()
            }
        }
    }
}
