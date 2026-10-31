package com.waymark.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.waymark.data.catalog.Coastline
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LabelPlacer
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.MapPoint
import com.waymark.ui.theme.Waymark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The trip, drawn on the world.
 *
 * Two projections share everything but the maths that turns a latitude into a
 * pixel: a flat chart for reading a region, and a globe for the long hauls
 * where a flat map lies about the route. Both draw the same coastline, the
 * same great-circle legs, and the same marks, and both are pinch-zoomable and
 * drag-movable — on the globe, dragging turns the planet.
 *
 * Labels are placed by [LabelPlacer] rather than pinned blindly beside their
 * marks, which is what keeps a cluster of hotels in one city from printing on
 * top of itself.
 */
@Composable
fun WorldMap(
    places: List<ChartPlace>,
    routes: List<ChartRoute>,
    projection: MapProjection,
    modifier: Modifier = Modifier,
    /** What the map shows, for a screen reader — the canvas itself says nothing. */
    description: String? = null,
    onSelect: (String) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = Waymark.colors
    val measurer = rememberTextMeasurer()

    // Everything that does not depend on where the map is looking is worked
    // out once, not on every frame of a pinch: the labels' text layouts, and
    // each leg's great circle in degrees.
    val labels = remember(places, measurer) {
        places.associate { it.id to measurer.measure(it.label, LABEL_STYLE) }
    }
    val legs = remember(routes) {
        routes.map { route ->
            route to if (route.flying) Geo.arc(route.from, route.to, ROUTE_SAMPLES) else listOf(route.from, route.to)
        }
    }
    // One path, reset and refilled for every landmass and grid line.
    val scratch = remember { Path() }

    val focus = remember(places, routes) {
        Geo.centroid(
            places.map { it.position } + routes.flatMap { listOf(it.from, it.to) }
        ) ?: LatLon(20.0, 0.0)
    }

    // Flat state: a centre in projected 0..1 space, and a scale in screen
    // widths per world width. Globe state: the point facing the viewer, and how
    // much bigger than the viewport the sphere is drawn.
    var flatCentre by remember(focus) { mutableStateOf(Geo.project(focus)) }
    var flatZoom by remember(focus) { mutableFloatStateOf(0f) }
    var spinLat by remember(focus) { mutableFloatStateOf(focus.latitude.toFloat()) }
    var spinLon by remember(focus) { mutableFloatStateOf(focus.longitude.toFloat()) }
    var globeZoom by remember(focus) { mutableFloatStateOf(1f) }

    // Recomputed every frame from the live gesture state and reused by the tap
    // handler, so what you touch is what you just saw. Deliberately not
    // snapshot state: this is assigned during the draw phase.
    val projector = remember { Projector() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (description != null) Modifier.semantics { contentDescription = description } else Modifier
                )
                // Fit the trip once, on the first real layout — in the layout
                // phase, not the draw phase, so the viewport is settled before
                // anything is painted.
                .onSizeChanged { measured ->
                    if (flatZoom == 0f && measured.width > 0) {
                        val fitted = fitViewport(
                            places.map { Geo.project(it.position) } +
                                routes.flatMap {
                                    listOf(Geo.project(it.from), Geo.project(it.to))
                                },
                            measured.toSize(),
                        )
                        flatCentre = fitted.first
                        flatZoom = fitted.second
                    }
                }
                .pointerInput(projection, places) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        when (projection) {
                            MapProjection.FLAT -> {
                                val scale = size.width * flatZoom
                                if (scale > 0f) {
                                    flatCentre = MapPoint(
                                        (flatCentre.x - pan.x / scale).coerceIn(0.0, 1.0),
                                        (flatCentre.y - pan.y / scale).coerceIn(0.0, 1.0),
                                    )
                                }
                                flatZoom = (flatZoom * gestureZoom).coerceIn(FLAT_MIN, FLAT_MAX)
                            }

                            MapProjection.GLOBE -> {
                                // A drag of the globe's width turns it half a
                                // turn at rest, less as it is zoomed in, so the
                                // gesture always moves the same amount of land.
                                val sensitivity = 180f / globeZoom
                                spinLon -= (pan.x / size.width) * sensitivity
                                spinLat = (spinLat + (pan.y / size.height) * sensitivity)
                                    .coerceIn(-85f, 85f)
                                globeZoom = (globeZoom * gestureZoom)
                                    .coerceIn(GLOBE_MIN, GLOBE_MAX)
                            }
                        }
                    }
                }
                .pointerInput(places, projection) {
                    detectTapGestures { tap ->
                        val threshold = 36.dp.toPx()
                        places
                            .mapNotNull { place ->
                                projector.screenOf(place.position)?.let { place to it }
                            }
                            .filter { (_, point) -> (point - tap).getDistance() < threshold }
                            .minByOrNull { (_, point) -> (point - tap).getDistance() }
                            ?.first?.segmentId?.let(onSelect)
                    }
                }
        ) {
            // Nothing to draw until the fit above has run.
            if (projection == MapProjection.FLAT && flatZoom <= 0f) return@Canvas

            val view: MapView = when (projection) {
                MapProjection.FLAT -> FlatView(flatCentre, flatZoom, size)

                MapProjection.GLOBE -> GlobeView(
                    centreLat = spinLat.toDouble(),
                    centreLon = spinLon.toDouble(),
                    canvas = size,
                    zoom = globeZoom,
                )
            }
            projector.screenOf = { position -> view.screen(position) }

            view.drawWater(this, colors.mapWater, colors.mapLand)
            view.clip(this) {
                drawLand(view, colors.mapLand, colors.mapCoast, scratch)
                drawGraticule(view, colors.mapGrid, scratch)
                legs.forEach { (route, samples) ->
                    drawRoute(
                        route = route,
                        samples = samples,
                        view = view,
                        flight = colors.accentAmber,
                        ground = colors.accentSage,
                        behind = colors.mapCoast,
                    )
                }
                drawMarks(
                    places = places,
                    view = view,
                    layouts = labels,
                    ink = MarkInk(
                        station = colors.accentAmber,
                        stay = colors.accentSage,
                        stop = colors.textDim,
                        label = colors.textMuted,
                        halo = colors.mapWater,
                    ),
                )
            }
        }

        overlay()
    }
}

/** Holds the current frame's projection for the gesture handlers to hit-test with. */
private class Projector {
    var screenOf: (LatLon) -> Offset? = { null }
}

// — Projections ————————————————————————————————————————————————————————————

/**
 * What every drawing routine needs from a projection: where a coordinate
 * lands, whether it is on screen at all, and how the water behind it is drawn.
 */
private interface MapView {

    /** Null when the point is not currently visible — behind the globe, say. */
    fun screen(point: LatLon): Offset?

    /** Screen position ignoring visibility, for stroking a path that fades. */
    fun raw(point: LatLon): Offset

    fun visible(point: LatLon): Boolean

    /** True when consecutive points are far enough apart to need a break. */
    fun breaks(a: LatLon, b: LatLon): Boolean

    fun drawWater(scope: DrawScope, water: Color, rim: Color)

    fun clip(scope: DrawScope, block: DrawScope.() -> Unit)

    /** Degrees between graticule lines at the current scale. */
    val graticuleStep: Int

    /** How much of the world is on screen, 1.0 being all of it. */
    val worldFraction: Float
}

private class FlatView(
    private val centre: MapPoint,
    private val zoom: Float,
    private val canvas: Size,
) : MapView {

    private val scale = canvas.width * zoom

    override fun screen(point: LatLon): Offset? = raw(point)

    override fun raw(point: LatLon): Offset {
        val projected = Geo.project(point)
        return Offset(
            x = ((projected.x - centre.x) * scale + canvas.width / 2).toFloat(),
            y = ((projected.y - centre.y) * scale + canvas.height / 2).toFloat(),
        )
    }

    override fun visible(point: LatLon): Boolean = true

    override fun breaks(a: LatLon, b: LatLon): Boolean = Geo.crossesDateLine(a, b)

    override fun drawWater(scope: DrawScope, water: Color, rim: Color) {
        scope.drawRect(water)
    }

    override fun clip(scope: DrawScope, block: DrawScope.() -> Unit) {
        scope.clipRect { block() }
    }

    override val graticuleStep: Int
        get() = when {
            zoom > 90f -> 5
            zoom > 30f -> 10
            zoom > 12f -> 15
            else -> 30
        }

    override val worldFraction: Float get() = if (zoom <= 0f) 1f else (1f / zoom).coerceAtMost(1f)
}

private class GlobeView(
    private val centreLat: Double,
    private val centreLon: Double,
    private val canvas: Size,
    private val zoom: Float,
) : MapView {

    /** The viewport disc — fixed — and the sphere, which grows as you zoom. */
    val discRadius = canvas.minDimension / 2f * 0.94f
    private val radius = discRadius * zoom
    private val centre = Offset(canvas.width / 2f, canvas.height / 2f)

    override fun screen(point: LatLon): Offset? =
        if (visible(point)) raw(point) else null

    override fun raw(point: LatLon): Offset {
        val projected = Geo.orthographic(point, centreLat, centreLon)
        return Offset(
            x = centre.x + (projected.x * radius).toFloat(),
            y = centre.y - (projected.y * radius).toFloat(),
        )
    }

    override fun visible(point: LatLon): Boolean =
        Geo.orthographic(point, centreLat, centreLon).visible

    /** A leg only breaks on the globe where it passes behind the horizon. */
    override fun breaks(a: LatLon, b: LatLon): Boolean = visible(a) != visible(b)

    override fun drawWater(scope: DrawScope, water: Color, rim: Color) {
        with(scope) {
            // The lit sphere: the ocean, with the light coming from over the
            // viewer's left shoulder. This is the only shading in the app.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to water,
                    0.72f to water,
                    1f to rim,
                    center = Offset(
                        centre.x - discRadius * 0.3f,
                        centre.y - discRadius * 0.35f,
                    ),
                    radius = discRadius * 1.45f,
                ),
                radius = discRadius,
                center = centre,
            )
        }
    }

    override fun clip(scope: DrawScope, block: DrawScope.() -> Unit) {
        val disc = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    left = centre.x - discRadius,
                    top = centre.y - discRadius,
                    right = centre.x + discRadius,
                    bottom = centre.y + discRadius,
                )
            )
        }
        scope.clipPath(disc) { block() }
    }

    override val graticuleStep: Int
        get() = when {
            zoom > 6f -> 10
            zoom > 2.5f -> 15
            else -> 30
        }

    override val worldFraction: Float get() = (1f / zoom).coerceAtMost(1f)

    /**
     * Where a point sits around the limb, for closing a landmass that runs off
     * the edge of the world.
     */
    fun angleOf(point: Offset): Float =
        atan2(point.y - centre.y, point.x - centre.x)

    fun onLimb(angle: Float): Offset = Offset(
        x = centre.x + radius * cos(angle),
        y = centre.y + radius * sin(angle),
    )
}

// — Drawing ————————————————————————————————————————————————————————————————

/**
 * The land. Filled, because a coastline drawn as a line reads as a route, and
 * the whole point of the basemap is that it recedes.
 */
private fun DrawScope.drawLand(view: MapView, fill: Color, coast: Color, path: Path) {
    // At world scale every ring matters; zoomed in, the small islands are the
    // ones that cost the most and say the least.
    val minimum = if (view.worldFraction > 0.4f) 0 else 12

    Coastline.rings.forEach { ring ->
        if (ring.size < minimum) return@forEach
        runsOf(ring, view).forEach { run ->
            if (run.size < 3) return@forEach
            path.reset()
            run.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            // A landmass cut by the globe's horizon is closed along the limb
            // rather than across the sphere, so Asia does not grow a chord.
            if (view is GlobeView && run.first() != run.last()) {
                closeAlongLimb(path, view, run.last(), run.first())
            }
            path.close()
            drawPath(path, fill)
            drawPath(path, coast, style = HAIRLINE)
        }
    }
}

/**
 * Split a ring into the runs that are actually on screen, breaking wherever
 * the projection says two consecutive points are not neighbours — behind the
 * horizon on a globe, across the seam on a chart.
 */
private fun runsOf(ring: List<LatLon>, view: MapView): List<List<Offset>> {
    val runs = mutableListOf<List<Offset>>()
    var current = mutableListOf<Offset>()

    ring.forEachIndexed { index, point ->
        val previous = if (index == 0) null else ring[index - 1]
        val broken = previous != null && view.breaks(previous, point)
        if (broken && current.isNotEmpty()) {
            runs += current
            current = mutableListOf()
        }
        if (view.visible(point)) current += view.raw(point)
    }
    if (current.isNotEmpty()) runs += current

    // A ring is closed, so a run that ends where the ring ends and one that
    // starts where it starts are the same piece of coast split by the loop
    // point. Joining them keeps the horizon closure to one chord, not two.
    if (runs.size > 1 && ring.first() == ring.last() &&
        view.visible(ring.first()) && view.visible(ring[ring.size - 2])
    ) {
        val tail = runs.removeAt(runs.size - 1)
        runs[0] = tail + runs[0]
    }
    return runs
}

/** Walk the shorter way round the limb from one cut edge back to the other. */
private fun closeAlongLimb(path: Path, view: GlobeView, from: Offset, to: Offset) {
    val start = view.angleOf(from)
    val end = view.angleOf(to)
    var sweep = end - start
    while (sweep > Math.PI) sweep -= (2 * Math.PI).toFloat()
    while (sweep < -Math.PI) sweep += (2 * Math.PI).toFloat()

    val steps = max(2, (abs(sweep) / 0.08f).toInt())
    for (step in 1..steps) {
        val point = view.onLimb(start + sweep * step / steps)
        path.lineTo(point.x, point.y)
    }
}

private fun DrawScope.drawGraticule(view: MapView, color: Color, path: Path) {
    graticule(view.graticuleStep).forEach { line -> strokeSampled(line, view, color, path) }
}

/**
 * The grid at one spacing, sampled: meridians run pole to pole and parallels
 * stop short of the poles, and on a globe both curve, so both projections
 * sample rather than drawing a straight line. There are four spacings, each
 * built the first time it is drawn and kept.
 */
private fun graticule(step: Int): List<List<LatLon>> = GRATICULES.getOrPut(step) {
    val meridians = (-180..180 step step).map { lon ->
        (-90..90 step 3).map { LatLon(it.toDouble(), lon.toDouble()) }
    }
    val parallels = (-60..60 step step).map { lat ->
        (-180..180 step 4).map { LatLon(lat.toDouble(), it.toDouble()) }
    }
    meridians + parallels
}

/** Drawn on the main thread only, so a plain map is enough. */
private val GRATICULES = HashMap<Int, List<List<LatLon>>>()

private fun DrawScope.strokeSampled(points: List<LatLon>, view: MapView, color: Color, path: Path) {
    path.reset()
    var started = false
    points.forEachIndexed { index, point ->
        if (!view.visible(point)) {
            started = false
            return@forEachIndexed
        }
        val previous = if (index == 0) null else points[index - 1]
        if (previous != null && view.breaks(previous, point)) started = false
        val screen = view.raw(point)
        if (!started) {
            path.moveTo(screen.x, screen.y)
            started = true
        } else {
            path.lineTo(screen.x, screen.y)
        }
    }
    drawPath(path, color, style = HAIRLINE)
}

private fun DrawScope.drawRoute(
    route: ChartRoute,
    samples: List<LatLon>,
    view: MapView,
    flight: Color,
    ground: Color,
    behind: Color,
) {
    val width = 1.8.dp.toPx()
    val effect = if (route.flying) {
        null
    } else {
        PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
    }
    val colour = if (route.flying) flight else ground

    // The leg is drawn as runs, so the part behind the globe is a faint hint
    // of where it went rather than a line across the face of the world.
    var path = Path()
    var started = false
    var currentlyVisible = true

    fun flush() {
        if (started) {
            drawPath(
                path = path,
                color = if (currentlyVisible) colour else behind.copy(alpha = 0.35f),
                style = Stroke(width = width, pathEffect = effect),
            )
        }
        path = Path()
        started = false
    }

    samples.forEachIndexed { index, point ->
        val isVisible = view.visible(point)
        val previous = if (index == 0) null else samples[index - 1]
        val seam = previous != null && Geo.crossesDateLine(previous, point)

        if (index == 0 || seam || isVisible != currentlyVisible) {
            flush()
            currentlyVisible = isVisible
        }
        val screen = view.raw(point)
        if (!started) {
            path.moveTo(screen.x, screen.y)
            started = true
        } else {
            path.lineTo(screen.x, screen.y)
        }
    }
    flush()
}

/**
 * Marks first, then whichever labels fit. Dropping a label is deliberate: at
 * world zoom a trip with forty saved restaurants should read as a trip, not as
 * a wall of text.
 */
private fun DrawScope.drawMarks(
    places: List<ChartPlace>,
    view: MapView,
    layouts: Map<String, TextLayoutResult>,
    ink: MarkInk,
) {
    val halo = ink.halo
    data class Drawn(val place: ChartPlace, val point: Offset, val radius: Float)

    val onScreen = places.mapNotNull { place ->
        val point = view.screen(place.position) ?: return@mapNotNull null
        if (point.x < -60f || point.x > size.width + 60f) return@mapNotNull null
        if (point.y < -40f || point.y > size.height + 40f) return@mapNotNull null
        Drawn(
            place = place,
            point = point,
            radius = when (place.kind) {
                ChartPlace.Kind.STATION -> 5.dp.toPx()
                ChartPlace.Kind.STAY -> 4.5.dp.toPx()
                ChartPlace.Kind.STOP -> 3.5.dp.toPx()
            },
        )
    }

    onScreen.forEach { drawn ->
        val tint = when (drawn.place.kind) {
            ChartPlace.Kind.STATION -> ink.station
            ChartPlace.Kind.STAY -> ink.stay
            ChartPlace.Kind.STOP -> ink.stop
        }
        // A ring, not a blob: the mark should not hide the place it marks.
        drawCircle(halo.copy(alpha = 0.85f), drawn.radius + 2f, drawn.point)
        drawCircle(tint, drawn.radius, drawn.point, style = Stroke(width = 1.6.dp.toPx()))
        if (drawn.place.kind == ChartPlace.Kind.STATION) {
            drawCircle(tint, 1.6.dp.toPx(), drawn.point)
        }
    }

    val placements = LabelPlacer.place(
        requests = onScreen.mapNotNull { drawn ->
            val layout = layouts[drawn.place.id] ?: return@mapNotNull null
            LabelPlacer.Request(
                id = drawn.place.id,
                anchorX = drawn.point.x,
                anchorY = drawn.point.y,
                width = layout.size.width.toFloat(),
                height = layout.size.height.toFloat(),
                priority = drawn.place.kind.rank,
                markRadius = drawn.radius + 2f,
            )
        },
        viewport = LabelPlacer.Box(0f, 0f, size.width, size.height),
        gap = 3.dp.toPx(),
    )

    placements.forEach { placement ->
        val layout = layouts[placement.id] ?: return@forEach
        // A halo behind the text, because a name over a coastline is a name
        // nobody can read.
        drawRect(
            color = halo.copy(alpha = 0.72f),
            topLeft = Offset(placement.left - 2f, placement.top - 1f),
            size = Size(layout.size.width + 4f, layout.size.height + 2f),
        )
        drawText(
            textLayoutResult = layout,
            color = ink.label,
            topLeft = Offset(placement.left, placement.top),
        )
    }
}

/** The colours a mark is drawn in, by kind, and the halo that keeps it legible over land. */
private class MarkInk(val station: Color, val stay: Color, val stop: Color, val label: Color, val halo: Color)

/** Centre and zoom that put every known point comfortably inside the canvas. */
private fun fitViewport(points: List<MapPoint>, size: Size): Pair<MapPoint, Float> {
    if (points.isEmpty() || size.width <= 0f) return MapPoint(0.5, 0.5) to 1f
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val spanX = max(maxX - minX, 0.004)
    val spanY = max(maxY - minY, 0.004)
    val fitX = 1.0 / (spanX * 1.35)
    val fitY = (size.height / size.width).toDouble() / (spanY * 1.4)
    val zoom = min(fitX, fitY).coerceIn(FLAT_MIN.toDouble(), 200.0).toFloat()
    return MapPoint((minX + maxX) / 2, (minY + maxY) / 2) to zoom
}

private const val ROUTE_SAMPLES = 72
private val LABEL_STYLE = TextStyle(fontSize = 10.sp, letterSpacing = 0.6.sp)
private val HAIRLINE = Stroke(width = 1f)
private const val FLAT_MIN = 0.55f
private const val FLAT_MAX = 400f
private const val GLOBE_MIN = 1f
private const val GLOBE_MAX = 9f
