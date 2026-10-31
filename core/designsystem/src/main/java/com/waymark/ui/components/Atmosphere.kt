package com.waymark.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.waymark.ui.theme.Waymark

/**
 * The trademark background: a three-stop vertical gradient with three large,
 * very soft radial glows over it — amber top-right, sage bottom-left, a cream
 * lift through the middle.
 *
 * The glows are drawn as wide radial gradients rather than blurred layers:
 * a gradient with a long transparent tail is already soft, costs one draw
 * call, and — unlike a render-effect blur — works below API 31 and never
 * touches text contrast.
 *
 * No noise, no grain, no animation. The style reference is explicit that these
 * fight the reading.
 */
@Composable
fun WaymarkBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Waymark.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to colors.backgroundTop,
                        0.5f to colors.backgroundMid,
                        1f to colors.backgroundBottom,
                    )
                )

                // Warm amber, top-right, oversized and mostly off-canvas.
                drawGlow(
                    centre = Offset(size.width * 1.05f, -size.height * 0.06f),
                    radius = size.maxDimension * 0.95f,
                    color = colors.accentAmber,
                    peakAlpha = if (colors.isDusk) 0.13f else 0.16f,
                )

                // Sage, bottom-left.
                drawGlow(
                    centre = Offset(-size.width * 0.18f, size.height * 1.08f),
                    radius = size.maxDimension * 1.0f,
                    color = colors.accentSage,
                    peakAlpha = if (colors.isDusk) 0.10f else 0.13f,
                )

                // Cream lift through the middle, barely there.
                drawGlow(
                    centre = Offset(size.width * 0.34f, size.height * 0.46f),
                    radius = size.minDimension * 0.85f,
                    color = colors.textHeading,
                    peakAlpha = if (colors.isDusk) 0.045f else 0.07f,
                )
            },
        content = content,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(
    centre: Offset,
    radius: Float,
    color: Color,
    peakAlpha: Float,
) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = peakAlpha),
            0.32f to color.copy(alpha = peakAlpha * 0.28f),
            1f to color.copy(alpha = 0f),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}
