package com.waymark.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.ui.theme.Waymark

/**
 * The trip on a globe.
 *
 * Orthographic projection — the view of a sphere from infinitely far away — so
 * a long-haul route reads as what it is: a curve over the top of the world
 * rather than a diagonal across a rectangle. Drag to turn it.
 *
 * Everything behind the horizon is culled rather than drawn flat, which is the
 * whole reason to use a globe instead of a map: the far side is genuinely out
 * of sight, and a route that disappears over the edge tells the truth about
 * the distance involved.
 *
 * The idle spin is the one animation in the app that exists for pleasure. It
 * is off by default, as the style reference asks.
 */
@Composable
fun Globe(
    places: List<ChartPlace>,
    routes: List<ChartRoute>,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
) {
    val colors = Waymark.colors
    val measurer = rememberTextMeasurer()

    val focus = remember(places, routes) {
        Geo.centroid(places.map { it.position } + routes.map { it.from } + routes.map { it.to })
            ?: LatLon(20.0, 0.0)
    }

    var centreLat by remember(focus) { mutableFloatStateOf(focus.latitude.toFloat()) }
    var centreLon by remember(focus) { mutableFloatStateOf(focus.longitude.toFloat()) }

    val spin by rememberInfiniteTransition(label = "globe-spin").animateFloat(
        initialValue = 0f,
        targetValue = if (spinning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 72_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "globe-longitude",
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(places) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // A drag of the globe's width turns it half a turn:
                        // direct manipulation, no inertia, no overshoot.
                        centreLon -= (drag.x / size.width) * 180f
                        centreLat = (centreLat + (drag.y / size.height) * 180f)
                            .coerceIn(-85f, 85f)
                    }
                }
        ) {
            val radius = size.minDimension / 2f * 0.86f
            val centre = Offset(size.width / 2f, size.height / 2f)
            val lon = (centreLon + spin).toDouble()
            val lat = centreLat.toDouble()

            drawSphere(centre, radius, colors.panelFaint, colors.amber(0.16f), colors.borderSoft)
            drawGraticule(centre, radius, lat, lon, colors.borderFaint)

            routes.forEach { route ->
                drawGlobeRoute(
                    route = route,
                    centre = centre,
                    radius = radius,
                    centreLat = lat,
                    centreLon = lon,
                    near = if (route.emphasis) colors.accentBright else colors.accentAmber,
                    far = colors.amber(0.14f),
                )
            }

            places.forEach { place ->
                drawGlobePlace(
                    place = place,
                    centre = centre,
                    radius = radius,
                    centreLat = lat,
                    centreLon = lon,
                    measurer = measurer,
                    mark = when (place.kind) {
                        ChartPlace.Kind.STATION -> colors.accentAmber
                        ChartPlace.Kind.STAY -> colors.accentSage
                        ChartPlace.Kind.STOP -> colors.textDim
                    },
                    label = colors.textMuted,
                    halo = colors.backgroundMid,
                )
            }

            routes.mapNotNull { it.aircraft }.forEach { position ->
                val point = Geo.orthographic(position, lat, lon)
                if (!point.visible) return@forEach
                val screen = toScreen(point, centre, radius)
                drawCircle(colors.accentBright.copy(alpha = 0.25f), 12.dp.toPx(), screen)
                drawCircle(colors.accentBright, 4.dp.toPx(), screen)
            }
        }
    }
}

/** The body of the world: a lit sphere, not a flat disc. */
private fun DrawScope.drawSphere(
    centre: Offset,
    radius: Float,
    fill: Color,
    rimLight: Color,
    edge: Color,
) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to fill,
            0.75f to fill,
            1f to rimLight,
            center = Offset(centre.x - radius * 0.25f, centre.y - radius * 0.3f),
            radius = radius * 1.35f,
        ),
        radius = radius,
        center = centre,
    )
    drawCircle(edge, radius, centre, style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawGraticule(
    centre: Offset,
    radius: Float,
    centreLat: Double,
    centreLon: Double,
    color: Color,
) {
    val stroke = 1f

    // Parallels every 30°, meridians every 30°: enough to read rotation by.
    (-60..60 step 30).forEach { lat ->
        val path = Path()
        var started = false
        (0..360 step 4).forEach { lon ->
            val point = Geo.orthographic(
                LatLon(lat.toDouble(), lon.toDouble() - 180),
                centreLat,
                centreLon,
            )
            if (point.visible) {
                val screen = toScreen(point, centre, radius)
                if (!started) {
                    path.moveTo(screen.x, screen.y)
                    started = true
                } else {
                    path.lineTo(screen.x, screen.y)
                }
            } else {
                started = false
            }
        }
        drawPath(path, color, style = Stroke(width = stroke))
    }

    (0..330 step 30).forEach { lon ->
        val path = Path()
        var started = false
        (-90..90 step 3).forEach { lat ->
            val point = Geo.orthographic(
                LatLon(lat.toDouble(), lon.toDouble() - 180),
                centreLat,
                centreLon,
            )
            if (point.visible) {
                val screen = toScreen(point, centre, radius)
                if (!started) {
                    path.moveTo(screen.x, screen.y)
                    started = true
                } else {
                    path.lineTo(screen.x, screen.y)
                }
            } else {
                started = false
            }
        }
        drawPath(path, color, style = Stroke(width = stroke))
    }
}

/**
 * A leg over the sphere. The visible half is drawn solid; the half behind the
 * world is drawn faintly, so a route that wraps the planet stays readable as
 * one line rather than two unrelated strokes.
 */
private fun DrawScope.drawGlobeRoute(
    route: ChartRoute,
    centre: Offset,
    radius: Float,
    centreLat: Double,
    centreLon: Double,
    near: Color,
    far: Color,
) {
    val samples = Geo.globeArc(route.from, route.to, centreLat, centreLon, samples = 72)
    val width = if (route.emphasis) 2.4.dp.toPx() else 1.6.dp.toPx()

    var currentVisible: Boolean? = null
    var path = Path()

    fun flush() {
        if (currentVisible != null) {
            drawPath(path, if (currentVisible == true) near else far, style = Stroke(width = width))
        }
    }

    samples.forEach { point ->
        val screen = toScreen(point, centre, radius)
        if (point.visible != currentVisible) {
            flush()
            path = Path()
            path.moveTo(screen.x, screen.y)
            currentVisible = point.visible
        } else {
            path.lineTo(screen.x, screen.y)
        }
    }
    flush()
}

private fun DrawScope.drawGlobePlace(
    place: ChartPlace,
    centre: Offset,
    radius: Float,
    centreLat: Double,
    centreLon: Double,
    measurer: TextMeasurer,
    mark: Color,
    label: Color,
    halo: Color,
) {
    val point = Geo.orthographic(place.position, centreLat, centreLon)
    if (!point.visible) return
    val screen = toScreen(point, centre, radius)
    val dot = 4.dp.toPx()

    drawCircle(halo.copy(alpha = 0.8f), dot + 2f, screen)
    drawCircle(mark, dot, screen, style = Stroke(width = 1.6.dp.toPx()))

    val layout = measurer.measure(
        text = place.label,
        style = TextStyle(fontSize = 10.sp, letterSpacing = 0.6.sp),
    )
    drawText(
        textLayoutResult = layout,
        color = label,
        topLeft = Offset(screen.x + dot + 5f, screen.y - layout.size.height / 2f),
    )
}

/** Unit-sphere coordinates to canvas pixels. Screen y grows downward. */
private fun toScreen(point: Geo.GlobePoint, centre: Offset, radius: Float): Offset = Offset(
    x = centre.x + (point.x * radius).toFloat(),
    y = centre.y - (point.y * radius).toFloat(),
)
