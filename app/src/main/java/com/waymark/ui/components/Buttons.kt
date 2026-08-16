package com.waymark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * Primary call to action: bright accent fill, near-black text, uppercase and
 * tracked, with an amber glow beneath it.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .pressGive(pressed && enabled)
            .glow(
                color = colors.accentAmber,
                alpha = if (!enabled) 0f else if (pressed) 0.38f else 0.25f,
            )
            .clip(WaymarkShapes.control)
            .background(
                when {
                    !enabled -> colors.disabled
                    pressed -> colors.accentBrightHover
                    else -> colors.accentBright
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = WaymarkSpacing.large, vertical = WaymarkSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            icon?.let {
                WaymarkIcon(
                    it,
                    tint = if (enabled) colors.onAccent else colors.textFaint,
                    size = 15.dp,
                )
            }
            Text(
                text = text.uppercase(),
                style = Waymark.type.buttonLabel,
                color = if (enabled) colors.onAccent else colors.textFaint,
            )
        }
    }
}

/** Outlined. Hover — press, on a touch screen — inverts it. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inverted = pressed && enabled

    Box(
        modifier = modifier
            .clip(WaymarkShapes.control)
            .background(if (inverted) colors.textStrong else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (enabled) colors.textStrong else colors.borderFaint),
                WaymarkShapes.control,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = WaymarkSpacing.medium, vertical = WaymarkSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            val tint = when {
                !enabled -> colors.textFaint
                inverted -> colors.onAccent
                else -> colors.textStrong
            }
            icon?.let { WaymarkIcon(it, tint = tint, size = 15.dp) }
            Text(text = text.uppercase(), style = Waymark.type.buttonLabel, color = tint)
        }
    }
}

/** Quietest of the three. Border only, dim text. */
@Composable
fun MutedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = when {
        !enabled -> colors.textFaint
        pressed -> colors.textHeading
        else -> colors.textDim
    }

    Box(
        modifier = modifier
            .clip(WaymarkShapes.control)
            .border(
                BorderStroke(1.dp, if (pressed) colors.textDim else colors.border),
                WaymarkShapes.control,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = WaymarkSpacing.medium, vertical = WaymarkSpacing.snug),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            icon?.let { WaymarkIcon(it, tint = tint, size = 14.dp) }
            Text(text = text.uppercase(), style = Waymark.type.buttonLabel, color = tint)
        }
    }
}

/**
 * The destructive action. Outlined in the danger token rather than filled:
 * a filled red button is the loudest thing on a screen, and deleting a trip
 * should be findable, not tempting.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (enabled) colors.danger else colors.textFaint

    Box(
        modifier = modifier
            .clip(WaymarkShapes.control)
            .background(if (pressed && enabled) colors.dangerWash else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (enabled) colors.danger.copy(alpha = 0.6f) else colors.borderFaint),
                WaymarkShapes.control,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = WaymarkSpacing.medium, vertical = WaymarkSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            icon?.let { WaymarkIcon(it, tint = tint, size = 15.dp) }
            Text(text = text.uppercase(), style = Waymark.type.buttonLabel, color = tint)
        }
    }
}

/** Transparent icon button: no fill, no border. */
@Composable
fun GhostIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .clip(WaymarkShapes.control)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(WaymarkSpacing.snug),
        contentAlignment = Alignment.Center,
    ) {
        WaymarkIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint ?: if (pressed) colors.textHeading else colors.textDim,
            size = 18.dp,
        )
    }
}

/** A tappable option chip — the quiet, repeatable control. */
@Composable
fun OptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = Waymark.colors
    Row(
        modifier = modifier
            .clip(WaymarkShapes.chip)
            .background(if (selected) colors.amber(0.16f) else colors.chip)
            .border(
                1.dp,
                if (selected) colors.amber(0.5f) else colors.borderFaint,
                WaymarkShapes.chip,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = WaymarkSpacing.small, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = Waymark.type.control,
            color = if (selected) colors.textHeading else colors.textMuted,
        )
    }
}

/** Icon at a fixed size, tinted by token. Stroke weight comes from the path. */
@Composable
fun WaymarkIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint ?: Waymark.colors.textDim,
        modifier = modifier.size(size),
    )
}
