package com.waymark.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn in the Lucide idiom: a 24-unit box, two-unit stroke,
 * round caps and joins, no fills. Colour comes from the tint applied by
 * [WaymarkIcon], so every icon inherits its token the way `currentColor` does.
 *
 * They are declared here rather than pulled from a dependency for the same
 * reason the type stack is a system stack: the app should carry everything it
 * needs to render itself.
 *
 * ### The grid these are drawn on
 *
 * The first pass at this set was drawn by eye and it showed — a bed whose
 * frame did not meet its legs, a car with no floor, a globe whose meridian
 * bulged outside its own sphere, arcs with a radius smaller than half the
 * chord they had to span. Every icon here now obeys four rules:
 *
 * 1. **Ink lives in 3–21 on both axes.** The outer 3 units are the optical
 *    margin that keeps a 16dp glyph from crowding the text beside it.
 * 2. **Centre on 12, 12.** Not the bounding box of the shape — the visual
 *    weight. A cup with a handle is centred on the cup.
 * 3. **A shape that reads as closed is closed.** `close()`, not a final
 *    `lineTo` that lands almost back at the start.
 * 4. **Every arc can exist.** An elliptical arc needs `2 × radius ≥ chord`,
 *    or the renderer silently scales it up and the curve is not the one that
 *    was drawn.
 */
object WaymarkIcons {

    // — Modes of travel ————————————————————————————————————————————————

    /** A paper plane, nose up-right; the send/depart gesture. */
    val Plane: ImageVector = stroked("plane") {
        moveTo(21f, 3f)
        lineTo(3f, 10.5f)
        lineTo(10.5f, 13.5f)
        lineTo(13.5f, 21f)
        close()
        moveTo(10.5f, 13.5f)
        lineTo(21f, 3f)
    }

    /** Headboard, mattress, base rail, pillow — a bed that meets itself. */
    val Bed: ImageVector = stroked("bed") {
        moveTo(3f, 18f)
        lineTo(3f, 6f)
        moveTo(3f, 11.5f)
        lineTo(21f, 11.5f)
        lineTo(21f, 18f)
        moveTo(3f, 15.5f)
        lineTo(21f, 15.5f)
        moveTo(6.5f, 11.5f)
        lineTo(6.5f, 8f)
        lineTo(11.5f, 8f)
        lineTo(11.5f, 11.5f)
    }

    /** Carriage, two windows, wheels, rail. The splayed legs of the previous
     *  version made it read as a bookshelf. */
    val Train: ImageVector = stroked("train") {
        moveTo(5f, 4f)
        lineTo(19f, 4f)
        lineTo(19f, 16f)
        lineTo(5f, 16f)
        close()
        moveTo(5f, 10f)
        lineTo(19f, 10f)
        moveTo(12f, 4f)
        lineTo(12f, 10f)
        moveTo(8.5f, 16f)
        lineTo(8.5f, 18.5f)
        moveTo(15.5f, 16f)
        lineTo(15.5f, 18.5f)
        moveTo(4f, 20.5f)
        lineTo(20f, 20.5f)
    }

    val Car: ImageVector = stroked("car") {
        moveTo(3f, 16f)
        lineTo(3f, 11f)
        lineTo(6f, 5.5f)
        lineTo(18f, 5.5f)
        lineTo(21f, 11f)
        lineTo(21f, 16f)
        close()
        moveTo(3f, 11f)
        lineTo(21f, 11f)
        moveTo(6.5f, 16f)
        lineTo(6.5f, 18.5f)
        moveTo(17.5f, 16f)
        lineTo(17.5f, 18.5f)
    }

    // — Place and direction ————————————————————————————————————————————

    val Compass: ImageVector = stroked("compass") {
        circle(12f, 12f, 9f)
        moveTo(15.5f, 8.5f)
        lineTo(13.5f, 13.5f)
        lineTo(8.5f, 15.5f)
        lineTo(10.5f, 10.5f)
        close()
    }

    val MapPin: ImageVector = stroked("map-pin") {
        moveTo(12f, 21f)
        lineTo(6.5f, 13.5f)
        arcToRelative(6.8f, 6.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11f, 0f)
        close()
        circle(12f, 10f, 2.4f)
    }

    val Map: ImageVector = stroked("map") {
        moveTo(3f, 6f)
        lineTo(9f, 3.5f)
        lineTo(15f, 6.5f)
        lineTo(21f, 4f)
        lineTo(21f, 18f)
        lineTo(15f, 20.5f)
        lineTo(9f, 17.5f)
        lineTo(3f, 20f)
        close()
        moveTo(9f, 3.5f)
        lineTo(9f, 17.5f)
        moveTo(15f, 6.5f)
        lineTo(15f, 20.5f)
    }

    /**
     * The meridian is an ellipse with a 4.5-unit semi-minor axis and a 9-unit
     * semi-major, matching the sphere exactly. The previous one used a
     * 13-unit radius on a 9-unit ball, so the meridian escaped the globe.
     */
    val Globe: ImageVector = stroked("globe") {
        circle(12f, 12f, 9f)
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        moveTo(12f, 3f)
        arcToRelative(4.5f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 18f)
        arcToRelative(4.5f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -18f)
        close()
    }

    /** A route forking into two, with the branches ending in corner arrows. */
    val Split: ImageVector = stroked("split") {
        moveTo(12f, 19.5f)
        lineTo(12f, 11.5f)
        lineTo(5f, 4.5f)
        moveTo(12f, 11.5f)
        lineTo(19f, 4.5f)
        moveTo(5f, 4.5f)
        lineTo(5f, 8.5f)
        moveTo(5f, 4.5f)
        lineTo(9f, 4.5f)
        moveTo(19f, 4.5f)
        lineTo(19f, 8.5f)
        moveTo(19f, 4.5f)
        lineTo(15f, 4.5f)
    }

    // — People, time, security —————————————————————————————————————————

    /**
     * Two travelers: one drawn whole, one behind it. The previous version left
     * the second figure as two disconnected arcs, which read as damage rather
     * than as a person.
     */
    val Users: ImageVector = stroked("users") {
        circle(9.5f, 8.5f, 3.6f)
        moveTo(3.5f, 19.5f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 0f)
        circle(17.5f, 6.8f, 2.6f)
        moveTo(16f, 12.6f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.5f, 6.9f)
    }

    val Lock: ImageVector = stroked("lock") {
        moveTo(5f, 10.5f)
        lineTo(19f, 10.5f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        close()
        moveTo(8f, 10.5f)
        lineTo(8f, 7.5f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 0f)
        lineTo(16f, 10.5f)
    }

    val Clock: ImageVector = stroked("clock") {
        circle(12f, 12f, 9f)
        moveTo(12f, 6.5f)
        lineTo(12f, 12f)
        lineTo(16f, 14f)
    }

    // — Travel documents ———————————————————————————————————————————————

    /**
     * A stub with a perforation down it. The previous version notched both
     * outer edges with arcs that bulged the wrong way, closing two small loops
     * that read as solid blocks at icon size.
     */
    val Ticket: ImageVector = stroked("ticket") {
        moveTo(3f, 6.5f)
        lineTo(21f, 6.5f)
        lineTo(21f, 17.5f)
        lineTo(3f, 17.5f)
        close()
        moveTo(14.5f, 6.5f)
        lineTo(14.5f, 8.8f)
        moveTo(14.5f, 10.9f)
        lineTo(14.5f, 13.1f)
        moveTo(14.5f, 15.2f)
        lineTo(14.5f, 17.5f)
    }

    /**
     * Six bars with uneven gaps and equal outer margins. Evenly spaced bars
     * read as a list icon; a barcode is recognisable precisely because its
     * rhythm is irregular.
     */
    val Barcode: ImageVector = stroked("barcode") {
        moveTo(4.5f, 5f)
        lineTo(4.5f, 19f)
        moveTo(8f, 5f)
        lineTo(8f, 19f)
        moveTo(10.5f, 5f)
        lineTo(10.5f, 19f)
        moveTo(14f, 5f)
        lineTo(14f, 19f)
        moveTo(17f, 5f)
        lineTo(17f, 19f)
        moveTo(19.5f, 5f)
        lineTo(19.5f, 19f)
    }

    /** Knife and fork: the eating half of a trip list. */
    val Fork: ImageVector = stroked("fork") {
        moveTo(6f, 3f)
        lineTo(6f, 8f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 0f)
        lineTo(12f, 3f)
        moveTo(9f, 11f)
        lineTo(9f, 21f)
        moveTo(17f, 21f)
        lineTo(17f, 3f)
        arcToRelative(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 10f)
        lineTo(17f, 13f)
    }

    /**
     * A pediment on columns — the monument, the museum, the thing you queue
     * for. Drawn rather than reusing a sun or an eye, both of which already
     * mean something else in this app.
     */
    val Sights: ImageVector = stroked("sights") {
        moveTo(12f, 3.5f)
        lineTo(20.5f, 9f)
        lineTo(3.5f, 9f)
        close()
        moveTo(4.5f, 11.5f)
        lineTo(19.5f, 11.5f)
        moveTo(6.5f, 11.5f)
        lineTo(6.5f, 18f)
        moveTo(12f, 11.5f)
        lineTo(12f, 18f)
        moveTo(17.5f, 11.5f)
        lineTo(17.5f, 18f)
        moveTo(3.5f, 20.5f)
        lineTo(20.5f, 20.5f)
    }

    /** A ticket stub torn in half: something you go and do. */
    val Activity: ImageVector = stroked("activity") {
        moveTo(3f, 12f)
        lineTo(7f, 12f)
        lineTo(9.5f, 5f)
        lineTo(14.5f, 19f)
        lineTo(17f, 12f)
        lineTo(21f, 12f)
    }

    /** A shopping bag with handles. */
    val Shop: ImageVector = stroked("shop") {
        moveTo(4.5f, 7.5f)
        lineTo(19.5f, 7.5f)
        lineTo(18.5f, 20.5f)
        lineTo(5.5f, 20.5f)
        close()
        moveTo(8.5f, 10f)
        lineTo(8.5f, 6f)
        arcToRelative(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 0f)
        lineTo(15.5f, 10f)
    }

    /** A month: the binding rings, the header rule, the grid of days. */
    val Calendar: ImageVector = stroked("calendar") {
        moveTo(3.5f, 5.5f)
        lineTo(20.5f, 5.5f)
        lineTo(20.5f, 20.5f)
        lineTo(3.5f, 20.5f)
        close()
        moveTo(3.5f, 10f)
        lineTo(20.5f, 10f)
        moveTo(8f, 3f)
        lineTo(8f, 7.5f)
        moveTo(16f, 3f)
        lineTo(16f, 7.5f)
    }

    /** A suitcase: the packing list, and the bag it ends up in. */
    val Bag: ImageVector = stroked("bag") {
        moveTo(4f, 8f)
        lineTo(20f, 8f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(8.5f, 8f)
        lineTo(8.5f, 4.5f)
        lineTo(15.5f, 4.5f)
        lineTo(15.5f, 8f)
        moveTo(4f, 14f)
        lineTo(20f, 14f)
    }

    // — Weather and theme ——————————————————————————————————————————————

    val Sun: ImageVector = stroked("sun") {
        circle(12f, 12f, 4.2f)
        moveTo(12f, 3f)
        lineTo(12f, 5f)
        moveTo(12f, 19f)
        lineTo(12f, 21f)
        moveTo(3f, 12f)
        lineTo(5f, 12f)
        moveTo(19f, 12f)
        lineTo(21f, 12f)
        moveTo(5.6f, 5.6f)
        lineTo(7f, 7f)
        moveTo(17f, 17f)
        lineTo(18.4f, 18.4f)
        moveTo(18.4f, 5.6f)
        lineTo(17f, 7f)
        moveTo(7f, 17f)
        lineTo(5.6f, 18.4f)
    }

    val Moon: ImageVector = stroked("moon") {
        moveTo(20f, 14.5f)
        arcToRelative(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -10.5f, -10.5f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 10.5f)
        close()
    }

    /** Cup and handle, both drawn as true semicircles. */
    val Coffee: ImageVector = stroked("coffee") {
        moveTo(3f, 8f)
        lineTo(15f, 8f)
        lineTo(15f, 14f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -12f, 0f)
        close()
        moveTo(15f, 9.5f)
        lineTo(18f, 9.5f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 5f)
        lineTo(15f, 14.5f)
        moveTo(6.5f, 3.5f)
        lineTo(6.5f, 5.5f)
        moveTo(10.5f, 3.5f)
        lineTo(10.5f, 5.5f)
    }

    // — Controls ———————————————————————————————————————————————————————

    val Plus: ImageVector = stroked("plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val Close: ImageVector = stroked("close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    val Check: ImageVector = stroked("check") {
        moveTo(4.5f, 12.5f)
        lineTo(9.5f, 17.5f)
        lineTo(19.5f, 6.5f)
    }

    val ChevronLeft: ImageVector = stroked("chevron-left") {
        moveTo(14.5f, 5f)
        lineTo(7.5f, 12f)
        lineTo(14.5f, 19f)
    }

    val ChevronRight: ImageVector = stroked("chevron-right") {
        moveTo(9.5f, 5f)
        lineTo(16.5f, 12f)
        lineTo(9.5f, 19f)
    }

    val ChevronUp: ImageVector = stroked("chevron-up") {
        moveTo(5f, 14.5f)
        lineTo(12f, 7.5f)
        lineTo(19f, 14.5f)
    }

    val ChevronDown: ImageVector = stroked("chevron-down") {
        moveTo(5f, 9.5f)
        lineTo(12f, 16.5f)
        lineTo(19f, 9.5f)
    }

    val ArrowLeft: ImageVector = stroked("arrow-left") {
        moveTo(20f, 12f)
        lineTo(4f, 12f)
        moveTo(10f, 6f)
        lineTo(4f, 12f)
        lineTo(10f, 18f)
    }

    val ArrowRight: ImageVector = stroked("arrow-right") {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(14f, 6f)
        lineTo(20f, 12f)
        lineTo(14f, 18f)
    }

    val Search: ImageVector = stroked("search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.2f, 15.2f)
        lineTo(20.5f, 20.5f)
    }

    val Refresh: ImageVector = stroked("refresh") {
        moveTo(20f, 12f)
        arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, -2.4f, 5.7f)
        moveTo(20f, 6.5f)
        lineTo(20f, 12f)
        lineTo(14.5f, 12f)
    }

    /** Three dots — the one control that stands for "the rest of it". */
    val Menu: ImageVector = stroked("menu") {
        moveTo(12f, 5.4f)
        lineTo(12f, 5.8f)
        moveTo(12f, 11.8f)
        lineTo(12f, 12.2f)
        moveTo(12f, 18.2f)
        lineTo(12f, 18.6f)
    }

    /** Out of the app and into something else: the export gesture. */
    val Share: ImageVector = stroked("share") {
        moveTo(12f, 15f)
        lineTo(12f, 3.5f)
        moveTo(7.5f, 8f)
        lineTo(12f, 3.5f)
        lineTo(16.5f, 8f)
        moveTo(5f, 12.5f)
        lineTo(5f, 20.5f)
        lineTo(19f, 20.5f)
        lineTo(19f, 12.5f)
    }

    /** Axes and three bars: the analytics screen. */
    val Chart: ImageVector = stroked("chart") {
        moveTo(4f, 4f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        moveTo(8.5f, 20f)
        lineTo(8.5f, 14f)
        moveTo(13f, 20f)
        lineTo(13f, 9f)
        moveTo(17.5f, 20f)
        lineTo(17.5f, 5f)
    }

    /**
     * The reveal control in the vault, and the most-tapped icon in the app.
     *
     * The first version was 19 units wide and 7 tall — a ratio of nearly 3:1 —
     * with a pupil almost as tall as the eye containing it. At 18dp that is a
     * dark blob in a lens, not an eye. This one is 17 by 11, which is roughly
     * the proportion of a real eye, and the pupil is small enough to sit in it
     * with white either side.
     */
    val Eye: ImageVector = stroked("eye") {
        moveTo(3.5f, 12f)
        arcToRelative(9f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 0f)
        arcToRelative(9f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -17f, 0f)
        close()
        circle(12f, 12f, 2.8f)
    }

    /**
     * The same eye, closed and struck through. The pupil is gone rather than
     * crossed out: a slash over a pupil produced three lines meeting in the
     * middle of a nine-pixel circle, which at icon size was a smudge. An eye
     * with no pupil and a rule across it reads instantly.
     */
    val EyeOff: ImageVector = stroked("eye-off") {
        moveTo(3.5f, 12f)
        arcToRelative(9f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 0f)
        arcToRelative(9f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -17f, 0f)
        close()
        moveTo(4.5f, 19.5f)
        lineTo(19.5f, 4.5f)
    }

    val Alert: ImageVector = stroked("alert") {
        moveTo(12f, 4f)
        lineTo(21.5f, 20f)
        lineTo(2.5f, 20f)
        close()
        moveTo(12f, 10.5f)
        lineTo(12f, 14.5f)
        moveTo(12f, 17f)
        lineTo(12f, 17.4f)
    }

    val Info: ImageVector = stroked("info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        moveTo(12f, 7.8f)
        lineTo(12f, 8.2f)
    }

    /** A pencil at the usual 45°: edit. */
    val Pencil: ImageVector = stroked("pencil") {
        moveTo(4f, 20f)
        lineTo(4.8f, 16f)
        lineTo(16f, 4.8f)
        lineTo(19.2f, 8f)
        lineTo(8f, 19.2f)
        close()
        moveTo(13.5f, 7.3f)
        lineTo(16.7f, 10.5f)
    }

    val Trash: ImageVector = stroked("trash") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(9f, 7f)
        lineTo(9f, 4.5f)
        lineTo(15f, 4.5f)
        lineTo(15f, 7f)
        moveTo(6f, 7f)
        lineTo(7f, 20f)
        lineTo(17f, 20f)
        lineTo(18f, 7f)
    }

    /**
     * The mark used for the app itself: a surveyor's north needle.
     *
     * Two long triangles meeting on a vertical axis — the figure at the centre
     * of every compass rose ever printed on a chart, and the oldest way there
     * is of drawing "this way". It is one closed outline and one line, which
     * is as few strokes as a navigational mark can be made of, and it holds up
     * at 18dp in a menu and at 108dp on a home screen without redrawing.
     *
     * It replaces a needle inside a ring: at icon size the ring and the needle
     * competed, and the launcher version of it had the ring centred a third of
     * the way up the canvas, so the two halves of the mark did not even meet.
     */
    val Waymark: ImageVector = stroked("waymark") {
        moveTo(12f, 3f)
        lineTo(18.5f, 16f)
        lineTo(12f, 21f)
        lineTo(5.5f, 16f)
        close()
        moveTo(12f, 3f)
        lineTo(12f, 21f)
    }

    private fun stroked(name: String, path: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = androidx.compose.ui.graphics.vector.PathData(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}

/** Circle as two half arcs — the SVG idiom, since PathBuilder has no oval. */
private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
    moveTo(centreX - radius, centreY)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, -radius * 2, 0f)
    close()
}
