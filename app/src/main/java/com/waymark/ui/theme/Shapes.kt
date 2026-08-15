package com.waymark.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Two device-independent pixels, everywhere. Sharp corners are core to the
 * editorial feel — no pills, no softly rounded cards.
 */
object WaymarkShapes {
    val corner = 2.dp
    val panel = RoundedCornerShape(corner)
    val control = RoundedCornerShape(corner)
    val chip = RoundedCornerShape(corner)
    val modal = RoundedCornerShape(corner)
}

/** Spacing steps, from the layout section of the style reference. */
object WaymarkSpacing {
    val hairline = 1.dp
    val tight = 4.dp
    val snug = 8.dp
    val small = 12.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 40.dp
    val screenHorizontal = 20.dp
    val screenTop = 32.dp
    val screenBottom = 56.dp
}
