package com.waymark.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import kotlin.coroutines.cancellation.CancellationException

/**
 * The modal card: densest surface in the system, a translucent amber hairline,
 * decorative corner brackets, and an entrance that fades and settles rather
 * than bouncing. Dismissible by the close control, the backdrop, or back —
 * and the system's predictive back gesture shrinks it away under the finger,
 * so the traveler sees what letting go will do before they let go.
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
    // Starts at 0 and settles to 1 once, on first composition. It used to
    // animate *towards* 1 from 1, which is no animation at all.
    val appearance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appearance.animateTo(1f, tween(durationMillis = Motion.SETTLE, easing = Motion.easing))
    }
    val appear = appearance.value

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // How far a back gesture has gone, 0 to 1; back to 0 if it is let go of.
        var back by remember { mutableFloatStateOf(0f) }
        PredictiveBackHandler { gesture ->
            try {
                gesture.collect { back = it.progress }
                onDismiss()
            } catch (cancelled: CancellationException) {
                back = 0f
                throw cancelled
            }
        }

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
                        alpha = appear * (1f - BACK_FADE * back)
                        translationY = (1f - appear) * 16.dp.toPx()
                    }
                    .scale((0.96f + 0.04f * appear) * (1f - BACK_SHRINK * back))
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
                    .padding(WaymarkSpacing.large)
                    // A modal grows with its content until it would run off the
                    // screen, and then scrolls. The reminder sheet is the one
                    // that made this necessary — four categories of lead time
                    // is taller than a small phone in landscape — but a card
                    // that silently loses its bottom edge is a bug waiting for
                    // any of them.
                    .verticalScroll(rememberScrollState()),
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

/**
 * A question with two answers, the destructive one on the right in the danger
 * colour. Used where a slip of the thumb would cost something that cannot be
 * typed back in a few seconds.
 */
@Composable
fun ConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
    cancelLabel: String = "Keep",
) {
    WaymarkModal(title = title, eyebrow = "Confirm", onDismiss = onDismiss) {
        Text(text = message, style = Waymark.type.body, color = Waymark.colors.textBody)
        detail?.let { Text(text = it, style = Waymark.type.hint, color = Waymark.colors.textDim) }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = cancelLabel, onClick = onDismiss, modifier = Modifier.weight(1f))
            DangerButton(
                text = confirmLabel,
                icon = WaymarkIcons.Trash,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** How much of itself a modal gives up to a back gesture taken all the way. */
private const val BACK_SHRINK = 0.1f
private const val BACK_FADE = 0.3f
