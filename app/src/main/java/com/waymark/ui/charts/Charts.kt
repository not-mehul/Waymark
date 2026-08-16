package com.waymark.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.Measure
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.animatedValue
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import kotlin.math.max

/**
 * The chart kit.
 *
 * **Why nothing here encodes identity with hue.** The palette has two accents,
 * amber and sage, and they are deliberately close in lightness and chroma —
 * that closeness is what makes the theme calm. Run through a colour-vision
 * check they separate by ΔE 5.8 under protanopia and 8.6 under normal vision,
 * both below the floor at which a reader can tell two marks apart. So they are
 * never used as a categorical pair. Every chart here is single-hue: magnitude
 * is carried by length, identity by position and a direct label, and the one
 * mark that matters is lifted by *emphasis* rather than by a different colour.
 *
 * Where a mark does need two parts, they are two shades of one hue (ΔE 35 in
 * Dusk, 21 in Dawn) and both parts are always labelled.
 */

/** Horizontal bars, sorted by the caller. Longest bar sets the scale. */
@Composable
fun BarSeries(
    measures: List<Measure>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    if (measures.isEmpty()) return
    val colors = Waymark.colors
    val peak = max(measures.maxOf { it.value }, 1e-9)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        measures.forEach { measure ->
            Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = measure.label,
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The value is always written out: a bar the reader has to
                    // measure against a gridline is a bar they will misread.
                    Text(
                        text = measure.display,
                        style = Waymark.type.dataSmall,
                        color = if (measure.emphasis) colors.textHeading else colors.textDim,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                ) {
                    val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    drawRoundRect(
                        color = colors.borderFaint,
                        size = Size(size.width, size.height),
                        cornerRadius = corner,
                    )
                    val width = (size.width * (measure.value / peak)).toFloat()
                    if (width > 0.5f) {
                        drawRoundRect(
                            color = if (measure.emphasis) {
                                colors.accentAmber
                            } else {
                                colors.amber(0.45f)
                            },
                            size = Size(width, size.height),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Days along the bottom, hours up the side. Two shades of one hue: moving and
 * booked, both named in the legend beneath — never colour alone.
 */
@Composable
fun DayLoadChart(
    days: List<DayColumn>,
    modifier: Modifier = Modifier,
    height: Dp = 128.dp,
) {
    if (days.isEmpty()) return
    val colors = Waymark.colors
    val peak = max(days.maxOf { it.moving + it.booked }, 4.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val gap = 3.dp.toPx()
            val columnWidth = ((size.width - gap * (days.size - 1)) / days.size).coerceAtLeast(2f)
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

            // A recessive baseline and one gridline at the halfway mark: enough
            // to read against, not enough to compete with the data.
            drawLine(
                colors.borderFaint,
                Offset(0f, size.height * 0.5f),
                Offset(size.width, size.height * 0.5f),
                strokeWidth = 1f,
            )
            drawLine(
                colors.borderSoft,
                Offset(0f, size.height),
                Offset(size.width, size.height),
                strokeWidth = 1f,
            )

            days.forEachIndexed { index, day ->
                val x = index * (columnWidth + gap)
                val movingHeight = (size.height * (day.moving / peak)).toFloat()
                val bookedHeight = (size.height * (day.booked / peak)).toFloat()

                if (bookedHeight > 0.5f) {
                    drawRoundRect(
                        color = colors.amber(0.4f),
                        topLeft = Offset(x, size.height - movingHeight - bookedHeight),
                        size = Size(columnWidth, bookedHeight),
                        cornerRadius = corner,
                    )
                }
                if (movingHeight > 0.5f) {
                    drawRoundRect(
                        color = colors.accentAmber,
                        topLeft = Offset(x, size.height - movingHeight),
                        size = Size(columnWidth, movingHeight),
                        cornerRadius = corner,
                    )
                }
                if (day.today) {
                    drawLine(
                        color = colors.accentBright,
                        start = Offset(x + columnWidth / 2, 0f),
                        end = Offset(x + columnWidth / 2, size.height),
                        strokeWidth = 1f,
                    )
                }
            }
        }

        Spacer(Modifier.height(WaymarkSpacing.snug))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = days.first().label,
                style = Waymark.type.fieldLabel,
                color = colors.textFaint,
            )
            Text(
                text = days.last().label,
                style = Waymark.type.fieldLabel,
                color = colors.textFaint,
            )
        }
        Spacer(Modifier.height(WaymarkSpacing.snug))
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium)) {
            LegendSwatch("Moving", colors.accentAmber)
            LegendSwatch("Booked", colors.amber(0.4f))
        }
    }
}

data class DayColumn(
    val label: String,
    val moving: Double,
    val booked: Double,
    val today: Boolean = false,
)

/** A ratio against a limit — packing done, ideas ticked off. */
@Composable
fun Meter(
    fraction: Float,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = Waymark.colors
    // The bar grows to its value rather than appearing at it: ticking an item
    // off a packing list should visibly move the needle.
    val filled by animatedValue(fraction.coerceIn(0f, 1f))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = Waymark.type.bodySmall, color = colors.textMuted)
            Text(text = value, style = Waymark.type.dataSmall, color = colors.textDim)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            drawRoundRect(
                color = colors.borderFaint,
                size = Size(size.width, size.height),
                cornerRadius = corner,
            )
            val width = size.width * filled
            if (width > 0.5f) {
                drawRoundRect(
                    color = tint ?: colors.accentSage,
                    size = Size(width, size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/**
 * A ring with a figure in the middle of it.
 *
 * The figure is drawn to fit. It used to be one `stat`-sized line centred in a
 * 92dp ring, which was fine for "7" and wrong for "18452 km": eight characters
 * at 30sp are wider than the ring is, so the number ran out over its own stroke
 * on both sides. The type now steps down as the figure gets longer, and the
 * text is bounded by the ring's inner width so it can never reach the stroke
 * even if a figure arrives longer than anything anticipated here.
 */
@Composable
fun RingFigure(
    fraction: Float,
    figure: String,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 92.dp,
) {
    val colors = Waymark.colors
    val stroke = 6.dp

    // The ring's stroke, plus a margin either side so the glyphs are inside the
    // circle rather than touching it.
    val inner = diameter - stroke * 2 - WaymarkSpacing.small * 2

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(diameter)) {
                val width = stroke.toPx()
                val inset = width / 2
                drawArc(
                    color = colors.borderFaint,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - width, size.height - width),
                    style = Stroke(width = width),
                )
                drawArc(
                    color = colors.accentAmber,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - width, size.height - width),
                    style = Stroke(width = width),
                )
            }
            Text(
                text = figure,
                style = when {
                    figure.length <= 4 -> Waymark.type.stat
                    figure.length <= 6 -> Waymark.type.sectionHeading
                    figure.length <= 9 -> Waymark.type.dataLarge
                    else -> Waymark.type.data
                },
                color = colors.textHeading,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = inner),
            )
        }
        FieldLabel(caption)
    }
}

/**
 * One row of a part-to-whole, drawn as a single divided bar with every part
 * labelled beneath. Two shades of one hue plus a neutral remainder.
 */
@Composable
fun SplitBar(
    parts: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
) {
    val total = parts.sumOf { it.second }
    if (total <= 0.0) return
    val colors = Waymark.colors
    val shades = listOf(colors.accentAmber, colors.amber(0.45f), colors.amber(0.2f))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            var x = 0f
            val gap = 2.dp.toPx()
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            parts.forEachIndexed { index, (_, value) ->
                val width = ((size.width - gap * (parts.size - 1)) * (value / total)).toFloat()
                if (width > 0.5f) {
                    drawRoundRect(
                        color = shades[index % shades.size],
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                        cornerRadius = corner,
                    )
                }
                x += width + gap
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium)) {
            parts.forEachIndexed { index, (label, _) ->
                LegendSwatch(label, shades[index % shades.size])
            }
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
        // Legend text wears a text token, never the series colour.
        Text(text = label, style = Waymark.type.fieldLabel, color = Waymark.colors.textDim)
    }
}

/** A tiny line for a trend beside a figure. No axes, no grid, no labels. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
) {
    if (values.size < 2) return
    val colors = Waymark.colors
    val peak = max(values.max(), 1e-9)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val step = size.width / (values.size - 1)
        var previous: Offset? = null
        values.forEachIndexed { index, value ->
            val point = Offset(
                x = index * step,
                y = size.height - (size.height * (value / peak)).toFloat(),
            )
            previous?.let {
                drawLine(colors.accentAmber, it, point, strokeWidth = 2.dp.toPx())
            }
            previous = point
        }
    }
}
