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
 * [WaymarkIcon], so every icon inherits its token like `currentColor` does.
 *
 * They are declared here rather than pulled from a dependency for the same
 * reason the type stack is a system stack: the app should carry everything it
 * needs to render itself.
 */
object WaymarkIcons {

    val Plane: ImageVector = stroked("plane") {
        moveTo(21f, 12f)
        lineTo(3.5f, 6.5f)
        lineTo(6.5f, 12f)
        lineTo(3.5f, 17.5f)
        close()
        moveTo(6.5f, 12f)
        lineTo(13f, 12f)
    }

    val Bed: ImageVector = stroked("bed") {
        moveTo(3f, 18f)
        lineTo(3f, 8f)
        moveTo(3f, 13f)
        lineTo(21f, 13f)
        lineTo(21f, 18f)
        moveTo(6.5f, 10f)
        lineTo(10.5f, 10f)
        lineTo(10.5f, 13f)
        moveTo(12.5f, 13f)
        lineTo(12.5f, 10f)
        lineTo(19f, 10f)
        lineTo(19f, 13f)
    }

    val Train: ImageVector = stroked("train") {
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 16f)
        lineTo(6f, 16f)
        close()
        moveTo(6f, 10f)
        lineTo(18f, 10f)
        moveTo(8f, 20f)
        lineTo(10f, 16f)
        moveTo(16f, 20f)
        lineTo(14f, 16f)
    }

    val Car: ImageVector = stroked("car") {
        moveTo(3f, 16f)
        lineTo(3f, 12f)
        lineTo(5.5f, 7f)
        lineTo(18.5f, 7f)
        lineTo(21f, 12f)
        lineTo(21f, 16f)
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        moveTo(6f, 16f)
        lineTo(6f, 18f)
        moveTo(18f, 16f)
        lineTo(18f, 18f)
    }

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

    val Users: ImageVector = stroked("users") {
        circle(9f, 8f, 3.4f)
        moveTo(3f, 20f)
        lineTo(3f, 18.5f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 0f)
        lineTo(15f, 20f)
        moveTo(17f, 5.2f)
        arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 5.6f)
        moveTo(18.5f, 14.2f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.5f, 4.3f)
        lineTo(21f, 20f)
    }

    val Lock: ImageVector = stroked("lock") {
        moveTo(5f, 11f)
        lineTo(19f, 11f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        close()
        moveTo(8f, 11f)
        lineTo(8f, 8f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 0f)
        lineTo(16f, 11f)
    }

    val Clock: ImageVector = stroked("clock") {
        circle(12f, 12f, 9f)
        moveTo(12f, 7f)
        lineTo(12f, 12f)
        lineTo(15.5f, 14f)
    }

    val Alert: ImageVector = stroked("alert") {
        moveTo(12f, 3.5f)
        lineTo(22f, 20f)
        lineTo(2f, 20f)
        close()
        moveTo(12f, 10f)
        lineTo(12f, 14.5f)
        moveTo(12f, 17f)
        lineTo(12f, 17.4f)
    }

    val Ticket: ImageVector = stroked("ticket") {
        moveTo(3f, 8f)
        lineTo(21f, 8f)
        lineTo(21f, 11f)
        arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 2.6f)
        lineTo(21f, 17f)
        lineTo(3f, 17f)
        lineTo(3f, 13.6f)
        arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -2.6f)
        close()
        moveTo(14f, 8f)
        lineTo(14f, 17f)
    }

    val Barcode: ImageVector = stroked("barcode") {
        moveTo(4f, 5f)
        lineTo(4f, 19f)
        moveTo(7.5f, 5f)
        lineTo(7.5f, 19f)
        moveTo(11f, 5f)
        lineTo(11f, 19f)
        moveTo(15f, 5f)
        lineTo(15f, 19f)
        moveTo(18f, 5f)
        lineTo(18f, 19f)
        moveTo(21f, 5f)
        lineTo(21f, 19f)
    }

    val Sun: ImageVector = stroked("sun") {
        circle(12f, 12f, 4.2f)
        moveTo(12f, 2.5f)
        lineTo(12f, 4.5f)
        moveTo(12f, 19.5f)
        lineTo(12f, 21.5f)
        moveTo(2.5f, 12f)
        lineTo(4.5f, 12f)
        moveTo(19.5f, 12f)
        lineTo(21.5f, 12f)
        moveTo(5.5f, 5.5f)
        lineTo(7f, 7f)
        moveTo(17f, 17f)
        lineTo(18.5f, 18.5f)
        moveTo(18.5f, 5.5f)
        lineTo(17f, 7f)
        moveTo(7f, 17f)
        lineTo(5.5f, 18.5f)
    }

    val Moon: ImageVector = stroked("moon") {
        moveTo(20f, 14.5f)
        arcToRelative(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -10.5f, -10.5f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.5f, 10.5f)
        close()
    }

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
        moveTo(4f, 12.5f)
        lineTo(9.5f, 18f)
        lineTo(20f, 6.5f)
    }

    val ChevronRight: ImageVector = stroked("chevron-right") {
        moveTo(9f, 5f)
        lineTo(16f, 12f)
        lineTo(9f, 19f)
    }

    val ChevronDown: ImageVector = stroked("chevron-down") {
        moveTo(5f, 9f)
        lineTo(12f, 16f)
        lineTo(19f, 9f)
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
        circle(11f, 11f, 6.5f)
        moveTo(15.8f, 15.8f)
        lineTo(21f, 21f)
    }

    val Refresh: ImageVector = stroked("refresh") {
        moveTo(20f, 12f)
        arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, -2.4f, 5.7f)
        moveTo(20f, 6f)
        lineTo(20f, 12f)
        lineTo(14.5f, 12f)
    }

    val Eye: ImageVector = stroked("eye") {
        moveTo(2.5f, 12f)
        arcToRelative(11f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 0f)
        arcToRelative(11f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -19f, 0f)
        close()
        circle(12f, 12f, 3.2f)
    }

    val EyeOff: ImageVector = stroked("eye-off") {
        moveTo(4f, 8.5f)
        arcToRelative(11f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 7f)
        moveTo(4f, 20f)
        lineTo(20f, 4f)
    }

    val Info: ImageVector = stroked("info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        moveTo(12f, 7.8f)
        lineTo(12f, 8.2f)
    }

    val Globe: ImageVector = stroked("globe") {
        circle(12f, 12f, 9f)
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        moveTo(12f, 3f)
        arcToRelative(13f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 18f)
        arcToRelative(13f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -18f)
        close()
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

    val Split: ImageVector = stroked("split") {
        moveTo(4f, 20f)
        lineTo(4f, 14f)
        lineTo(12f, 8f)
        lineTo(20f, 14f)
        lineTo(20f, 20f)
        moveTo(12f, 8f)
        lineTo(12f, 4f)
    }

    val Coffee: ImageVector = stroked("coffee") {
        moveTo(4f, 8f)
        lineTo(17f, 8f)
        lineTo(17f, 15f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -13f, 0f)
        close()
        moveTo(17f, 10f)
        lineTo(20f, 10f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 5f)
        lineTo(17f, 15f)
        moveTo(6f, 4f)
        lineTo(6f, 5.5f)
        moveTo(10f, 4f)
        lineTo(10f, 5.5f)
    }

    /** The mark used for the app itself: a bearing needle in a ring. */
    val Waymark: ImageVector = stroked("waymark") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 4.5f)
        lineTo(12f, 19.5f)
        moveTo(8f, 15.5f)
        lineTo(12f, 5.5f)
        lineTo(16f, 15.5f)
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
