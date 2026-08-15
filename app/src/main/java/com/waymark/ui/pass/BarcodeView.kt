package com.waymark.ui.pass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.Code39

/**
 * A Code 39 symbol, drawn from run lengths.
 *
 * This prints the short reference — the sequence or ticket number — not the
 * full BCBP payload: a sixty-character string in a one-dimensional symbology
 * would be too dense to scan on a phone screen. Airlines encode that payload
 * in a two-dimensional symbol, and when a traveler imports the airline's own
 * pass image Waymark shows that image instead of this.
 */
@Composable
fun Code39Barcode(
    value: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    barColor: Color,
    backgroundColor: Color,
    quietZoneModules: Int = 10,
) {
    val symbol = remember(value) { Code39.encode(value, withCheckCharacter = true) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val totalModules = symbol.totalModules + quietZoneModules * 2
        if (totalModules <= 0) return@Canvas
        val moduleWidth = size.width / totalModules

        drawRect(color = backgroundColor, topLeft = Offset.Zero, size = size)

        var x = quietZoneModules * moduleWidth
        symbol.runs.forEach { run ->
            val width = run.modules * moduleWidth
            if (run.isBar) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                )
            }
            x += width
        }
    }
}
