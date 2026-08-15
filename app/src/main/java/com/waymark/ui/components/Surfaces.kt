package com.waymark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * A reading zone. Translucent fill, hairline border, two-pixel corners.
 *
 * Compose has no backdrop blur below API 31 and no cheap one above it, so the
 * translucency does the lifting instead: the panel fill is semi-opaque over
 * the glow layer, which reads as the same recessed surface without a
 * per-frame render effect.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    faint: Boolean = false,
    padding: Dp = WaymarkSpacing.medium,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Waymark.colors
    Column(
        modifier = modifier
            .clip(WaymarkShapes.panel)
            .background(if (faint) colors.panelFaint else colors.panel)
            .border(1.dp, borderColor ?: colors.borderSoft, WaymarkShapes.panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Section marker: all-caps, tracked, mono, dim. The core motif. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = text.uppercase(),
        style = Waymark.type.sectionLabel,
        color = color ?: Waymark.colors.textDim,
        modifier = modifier,
    )
}

/** Field marker, one step smaller than [SectionLabel]. */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = text.uppercase(),
        style = Waymark.type.fieldLabel,
        color = color ?: Waymark.colors.textDim,
        modifier = modifier,
    )
}

/**
 * A section marker with a hairline running to the edge — the divider and the
 * label are one gesture, which is what keeps the page from feeling boxed.
 */
@Composable
fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        SectionLabel(label)
        Hairline(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, strong: Boolean = false) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (strong) Waymark.colors.borderStrong else Waymark.colors.borderFaint)
    )
}

/** Serif heading with a single italic word carrying the emphasis. */
@Composable
fun EditorialHeading(
    lead: String,
    emphasis: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    Column(modifier = modifier) {
        Text(
            text = lead,
            style = Waymark.type.pageTitle,
            color = colors.textHeading,
        )
        if (emphasis != null) {
            Text(
                text = emphasis,
                style = Waymark.type.pageTitle.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                ),
                color = colors.accentAmber,
            )
        }
    }
}

/** A number that matters, in serif, with its label beneath in tracked mono. */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val colors = Waymark.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
        Text(
            text = value,
            style = Waymark.type.stat,
            color = if (emphasised) colors.accentAmber else colors.textHeading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FieldLabel(label)
    }
}

/** The italic term-definition aside used for footnotes. */
@Composable
fun EditorialNote(
    term: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
        Text(
            text = "$term.",
            style = Waymark.type.hint,
            color = colors.textMuted,
        )
        Text(
            text = body,
            style = Waymark.type.hint,
            color = colors.textDim,
        )
    }
}

/** Small tinted marker used for a traveler's initials. */
@Composable
fun PartyMark(
    initials: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    size: Dp = 26.dp,
) {
    val colors = Waymark.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(WaymarkShapes.chip)
            .background(if (active) colors.amber(0.18f) else colors.panelFaint)
            .border(
                1.dp,
                if (active) colors.amber(0.45f) else colors.borderFaint,
                WaymarkShapes.chip,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = Waymark.type.dataSmall,
            color = if (active) colors.accentAmber else colors.textFaint,
        )
    }
}

/** Vertical spacer used between the timeline's stacked marks. */
@Composable
fun VerticalRule(
    modifier: Modifier = Modifier,
    height: Dp,
    color: Color? = null,
) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(color ?: Waymark.colors.borderFaint)
    )
}
