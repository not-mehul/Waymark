package com.waymark.ui.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.WaymarkShapes

/**
 * The amber halo under a primary action. Elevation is used as the carrier
 * because a coloured spot shadow is the one glow Android composites for free;
 * on API 27 and below the platform ignores the tint and the button simply
 * sits flat, which is a fair degradation.
 */
fun Modifier.glow(
    color: Color,
    alpha: Float,
    shape: Shape = WaymarkShapes.control,
): Modifier = if (alpha <= 0f) {
    this
} else {
    shadow(
        elevation = (alpha * 46f).dp,
        shape = shape,
        ambientColor = color.copy(alpha = alpha),
        spotColor = color.copy(alpha = alpha),
    )
}

/** Focus ring, per the accessibility rule: never a bare removal of the outline. */
fun Modifier.focusRing(color: Color, visible: Boolean, shape: Shape = WaymarkShapes.control): Modifier =
    if (visible) border(2.dp, color, shape) else this

/**
 * The modal's decorative corner brackets: four one-pixel angles in translucent
 * amber, drawn over the card rather than around it so they never affect layout.
 */
fun Modifier.cornerBrackets(color: Color, armLength: Float = 26f): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.dp.toPx()
    val arm = armLength * density
    val w = size.width
    val h = size.height
    fun line(from: Offset, to: Offset) = drawLine(color, from, to, strokeWidth = stroke)

    line(Offset(0f, 0f), Offset(arm, 0f))
    line(Offset(0f, 0f), Offset(0f, arm))
    line(Offset(w - arm, 0f), Offset(w, 0f))
    line(Offset(w, 0f), Offset(w, arm))
    line(Offset(0f, h - arm), Offset(0f, h))
    line(Offset(0f, h), Offset(arm, h))
    line(Offset(w - arm, h), Offset(w, h))
    line(Offset(w, h - arm), Offset(w, h))
}

/** A one-pixel top highlight, the inset rule from the modal shadow stack. */
fun Modifier.innerHighlight(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        brush = Brush.horizontalGradient(
            0f to color.copy(alpha = 0f),
            0.5f to color,
            1f to color.copy(alpha = 0f),
        ),
        start = Offset(0f, 0.5f),
        end = Offset(size.width, 0.5f),
        strokeWidth = 1.dp.toPx(),
    )
}
