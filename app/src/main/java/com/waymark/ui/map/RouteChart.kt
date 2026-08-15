package com.waymark.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.MapPoint
import com.waymark.ui.theme.Waymark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A place worth drawing: an airport, a hotel, a restaurant on a hill. */
data class ChartPlace(
    val id: String,
    val label: String,
    val position: LatLon,
    val kind: Kind,
) {
    enum class Kind { STATION, STAY, STOP }
}

/** A leg between two places. Flights arc; ground legs run straight and dashed. */
data class ChartRoute(
    val id: String,
    val from: LatLon,
    val to: LatLon,
    val flying: Boolean,
    val aircraft: LatLon? = null,
    val emphasis: Boolean = false,
)

/**
 * An offline vector chart of the trip.
 *
 * There is no tile server and no basemap download: the chart draws the
 * graticule, the great-circle legs, and the places whose coordinates the app
 * actually holds. That is the honest set — a coastline traced from memory
 * would look like data and be nothing of the sort — and it answers the
 * questions a traveler asks of a trip map: what order, how far, which way.
 *
 * Pinch to zoom, drag to pan, tap a mark to open it.
 */
@Composable
fun RouteChart(
    places: List<ChartPlace>,
    routes: List<ChartRoute>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
) {
    val colors = Waymark.colors
    val measurer = rememberTextMeasurer()

    val projected = remember(places, routes) {
        places.map { Geo.project(it.position) } +
            routes.flatMap { listOf(Geo.project(it.from), Geo.project(it.to)) }
    }

    var zoom by remember(projected) { mutableFloatStateOf(0f) }
    var centre by remember(projected) { mutableStateOf(MapPoint(0.5, 0.5)) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { measured ->
                // Fit once, on the first real layout; after that the traveler
                // is in charge of the viewport.
                if (zoom == 0f && measured.width > 0) {
                    val fitted = fitViewport(projected, measured.toSize())
                    zoom = fitted.second
                    centre = fitted.first
                }
            }
            .pointerInput(projected) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val scale = size.width * zoom
                    if (scale > 0f) {
                        centre = MapPoint(
                            (centre.x - pan.x / scale).coerceIn(0.0, 1.0),
                            (centre.y - pan.y / scale).coerceIn(0.0, 1.0),
                        )
                    }
                    zoom = (zoom * gestureZoom).coerceIn(0.6f, 400f)
                }
            }
            .pointerInput(places, projected) {
                detectTapGestures { tap ->
                    val canvas = size.toSize()
                    val threshold = 40.dp.toPx()
                    val hit = places
                        .map { it to project(Geo.project(it.position), centre, zoom, canvas) }
                        .filter { (_, point) -> (point - tap).getDistance() < threshold }
                        .minByOrNull { (_, point) -> (point - tap).getDistance() }
                    hit?.let { onSelect(it.first.id) }
                }
            }
    ) {
        if (zoom <= 0f) return@Canvas

        drawGraticule(centre, zoom, colors.borderFaint, colors.borderSoft)

        routes.forEach { route ->
            drawRoute(
                route = route,
                centre = centre,
                zoom = zoom,
                flightColor = if (route.emphasis) colors.accentBright else colors.accentAmber,
                groundColor = colors.accentSage,
            )
        }

        places.forEach { place ->
            drawPlace(
                place = place,
                centre = centre,
                zoom = zoom,
                measurer = measurer,
                markColor = when (place.kind) {
                    ChartPlace.Kind.STATION -> colors.accentAmber
                    ChartPlace.Kind.STAY -> colors.accentSage
                    ChartPlace.Kind.STOP -> colors.textDim
                },
                labelColor = colors.textMuted,
                haloColor = colors.backgroundMid,
            )
        }

        // The aircraft goes on last: it is the one thing that moves.
        routes.mapNotNull { it.aircraft }.forEach { position ->
            val point = project(Geo.project(position), centre, zoom, size)
            drawCircle(colors.accentBright.copy(alpha = 0.22f), radius = 13.dp.toPx(), center = point)
            drawCircle(colors.accentBright, radius = 4.dp.toPx(), center = point)
        }
    }
}

/** Centre and zoom that put every known point comfortably inside the canvas. */
private fun fitViewport(points: List<MapPoint>, size: Size): Pair<MapPoint, Float> {
    if (points.isEmpty() || size.width <= 0f) return MapPoint(0.5, 0.5) to 1f
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanX = max(maxX - minX, 0.004)
    val spanY = max(maxY - minY, 0.004)
    val fitX = 1.0 / (spanX * 1.5)
    val fitY = (size.height / size.width).toDouble() / (spanY * 1.6)
    val zoom = min(fitX, fitY).coerceIn(0.6, 260.0).toFloat()
    return MapPoint((minX + maxX) / 2, (minY + maxY) / 2) to zoom
}

/** Projected 0..1 point to canvas pixels, under the current pan and zoom. */
private fun project(point: MapPoint, centre: MapPoint, zoom: Float, size: Size): Offset {
    val scale = size.width * zoom
    return Offset(
        x = ((point.x - centre.x) * scale + size.width / 2).toFloat(),
        y = ((point.y - centre.y) * scale + size.height / 2).toFloat(),
    )
}

/**
 * Meridians and parallels, spaced by zoom so the chart stays a chart rather
 * than becoming graph paper.
 */
private fun DrawScope.drawGraticule(
    centre: MapPoint,
    zoom: Float,
    line: Color,
    accentLine: Color,
) {
    val step = when {
        zoom > 90f -> 1
        zoom > 30f -> 5
        zoom > 12f -> 10
        zoom > 4f -> 15
        else -> 30
    }

    var lon = -180
    while (lon <= 180) {
        val x = project(Geo.project(LatLon(0.0, lon.toDouble())), centre, zoom, size).x
        if (x >= -10f && x <= size.width + 10f) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        lon += step
    }

    var lat = -75
    while (lat <= 75) {
        val y = project(Geo.project(LatLon(lat.toDouble(), 0.0)), centre, zoom, size).y
        if (y >= -10f && y <= size.height + 10f) {
            val emphasised = lat == 0 || abs(lat) == 60
            drawLine(
                if (emphasised) accentLine else line,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        lat += step
    }
}

private fun DrawScope.drawRoute(
    route: ChartRoute,
    centre: MapPoint,
    zoom: Float,
    flightColor: Color,
    groundColor: Color,
) {
    val samples = if (route.flying) {
        Geo.arc(route.from, route.to, 64)
    } else {
        listOf(route.from, route.to)
    }

    val path = Path()
    samples.forEachIndexed { index, point ->
        val screen = project(Geo.project(point), centre, zoom, size)
        // A leg over the antimeridian is drawn in two pieces rather than as a
        // line back across the whole world.
        val jumped = index > 0 && Geo.crossesDateLine(samples[index - 1], point)
        if (index == 0 || jumped) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }

    drawPath(
        path = path,
        color = if (route.flying) flightColor else groundColor,
        style = Stroke(
            width = if (route.emphasis) 2.4.dp.toPx() else 1.6.dp.toPx(),
            pathEffect = if (route.flying) {
                null
            } else {
                PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
            },
        ),
    )
}

private fun DrawScope.drawPlace(
    place: ChartPlace,
    centre: MapPoint,
    zoom: Float,
    measurer: TextMeasurer,
    markColor: Color,
    labelColor: Color,
    haloColor: Color,
) {
    val point = project(Geo.project(place.position), centre, zoom, size)
    if (point.x < -80f || point.x > size.width + 80f) return
    if (point.y < -60f || point.y > size.height + 60f) return

    val radius = when (place.kind) {
        ChartPlace.Kind.STATION -> 5.dp.toPx()
        ChartPlace.Kind.STAY -> 4.5.dp.toPx()
        ChartPlace.Kind.STOP -> 3.5.dp.toPx()
    }

    // A ring, not a blob: the mark should not hide the place it marks.
    drawCircle(haloColor.copy(alpha = 0.85f), radius = radius + 2f, center = point)
    drawCircle(markColor, radius = radius, center = point, style = Stroke(width = 1.6.dp.toPx()))
    if (place.kind == ChartPlace.Kind.STATION) {
        drawCircle(markColor, radius = 1.6.dp.toPx(), center = point)
    }

    val layout = measurer.measure(
        text = place.label,
        style = TextStyle(fontSize = 10.sp, letterSpacing = 0.6.sp),
    )
    drawText(
        textLayoutResult = layout,
        color = labelColor,
        topLeft = Offset(point.x + radius + 6f, point.y - layout.size.height / 2f),
    )
}
