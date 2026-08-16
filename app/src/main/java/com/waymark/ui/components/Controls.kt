package com.waymark.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * Segmented control. The active fill is the only divider between options —
 * and it is the bright accent with near-black text, never `textStrong` as a
 * background, which would collapse to invisible in one of the two themes.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    // Five segments on a phone need a tighter setting than three; the label
    // stays legible rather than being truncated to initials.
    val dense = options.size > 4
    Row(
        modifier = modifier
            .clip(WaymarkShapes.control)
            .background(colors.panelFaint)
            .border(1.dp, colors.border, WaymarkShapes.control)
            .padding(1.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(WaymarkShapes.control)
                    .background(if (active) colors.accentBright else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = if (dense) 8.dp else 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    maxLines = 1,
                    style = (if (dense) Waymark.type.bodySmall else Waymark.type.control).copy(
                        fontWeight = if (active) {
                            androidx.compose.ui.text.font.FontWeight.Medium
                        } else {
                            androidx.compose.ui.text.font.FontWeight.Normal
                        }
                    ),
                    color = if (active) colors.onAccent else colors.textMuted,
                )
            }
        }
    }
}

/**
 * Text input. Recessed fill, hairline border, focus moves the border to
 * `textDim`. Placeholders are serif italic in `textFaint`; technical fields
 * take the mono face.
 */
@Composable
fun WaymarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    hint: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = Waymark.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle: TextStyle = (if (mono) Waymark.type.data else Waymark.type.body)
        .copy(color = if (enabled) colors.textStrong else colors.textFaint)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        label?.let { FieldLabel(it) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WaymarkShapes.control)
                .background(if (enabled) colors.input else colors.disabled)
                .border(
                    1.dp,
                    when {
                        !enabled -> colors.borderFaint
                        focused -> colors.textDim
                        else -> colors.border
                    },
                    WaymarkShapes.control,
                )
                .padding(horizontal = WaymarkSpacing.small, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = Waymark.type.hint,
                        color = colors.textFaint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.accentAmber),
                    interactionSource = interaction,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
        hint?.let {
            Text(text = it, style = Waymark.type.hint, color = colors.textDim)
        }
    }
}

/**
 * Theme switch: a track holding a moon and a sun, with an amber knob that
 * slides between them. Operable by keyboard, announces its state.
 *
 * The geometry is derived rather than typed, because it had drifted: the knob
 * rested 2dp from the left edge but 4dp from the right, and the icons were
 * spaced by `SpaceBetween` against a padding that put the sun 2.5dp off the
 * centre of its own knob. Both halves now come from the same three numbers, so
 * the moon and the sun sit exactly under the knob in either theme.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val colors = Waymark.colors
    val controller = Waymark.theme

    val track = 60.dp
    val inset = 3.dp
    val knob = 26.dp
    // Two rest positions, equidistant from their own edge.
    val restLeft = inset
    val restRight = track - inset - knob
    // …and therefore two centres, symmetric about the middle of the track.
    val centreLeft = restLeft + knob / 2
    val centreRight = restRight + knob / 2

    val knobOffset by animateDpAsState(
        targetValue = if (controller.isDusk) restLeft else restRight,
        animationSpec = tween(durationMillis = 220),
        label = "theme-knob",
    )

    Box(
        modifier = modifier
            .width(track)
            .height(knob + inset * 2)
            .clip(WaymarkShapes.control)
            .background(colors.input)
            .border(1.dp, colors.border, WaymarkShapes.control)
            .clickable(role = Role.Switch) { controller.toggle() }
            .semantics {
                contentDescription = "Theme"
                stateDescription = if (controller.isDusk) "Dusk" else "Dawn"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knob)
                .clip(WaymarkShapes.control)
                .background(colors.accentAmber)
        )
        ToggleGlyph(
            icon = WaymarkIcons.Moon,
            centre = centreLeft,
            tint = if (controller.isDusk) colors.onAccent else colors.textFaint,
        )
        ToggleGlyph(
            icon = WaymarkIcons.Sun,
            centre = centreRight,
            tint = if (controller.isDusk) colors.textFaint else colors.onAccent,
        )
    }
}

/** One glyph, centred on a given x within the track rather than laid out by flow. */
@Composable
private fun ToggleGlyph(icon: ImageVector, centre: Dp, tint: Color) {
    val size = 15.dp
    Box(
        modifier = Modifier.offset(x = centre - size / 2).size(size),
        contentAlignment = Alignment.Center,
    ) {
        WaymarkIcon(icon = icon, tint = tint, size = size)
    }
}

/**
 * A quiet status line: icon, headline, detail.
 *
 * [onClick] is for the case where the notice is also the fix — "Android is not
 * letting Waymark notify you" is worth tapping. Without one the banner is inert
 * and carries no button role, so a reader is not invited to press a statement.
 */
@Composable
fun NoticeBanner(
    icon: ImageVector,
    headline: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    critical: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Waymark.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaymarkShapes.panel)
            .background(if (critical) colors.dangerWash else colors.noticeWash)
            .border(
                1.dp,
                if (critical) colors.danger.copy(alpha = 0.4f) else colors.amber(0.35f),
                WaymarkShapes.panel,
            )
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                }
            )
            .padding(WaymarkSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        WaymarkIcon(
            icon = icon,
            tint = if (critical) colors.danger else colors.accentBright,
            size = 17.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = headline,
                style = Waymark.type.bodySmall,
                color = if (critical) colors.danger else colors.textHeading,
            )
            detail?.let {
                Text(text = it, style = Waymark.type.hint, color = colors.textDim)
            }
        }
    }
}
