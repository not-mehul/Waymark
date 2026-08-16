package com.waymark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * A choice that explains itself.
 *
 * Both taxonomy pickers in the app used to be rows of one-word chips — "Stay ·
 * Ground · Booked", "See · Eat · Table · Walk · Do · Shop" — which is fine when
 * the reader already knows the vocabulary and useless when they do not. Nobody
 * guesses that "Ground" means a taxi or that "Table" means a restaurant while
 * "Eat" means a dish.
 *
 * A card carries the word, an icon, and the sentence that makes the word
 * obvious. It costs vertical space, which is why it appears at the top of a
 * form where the decision is made once, and never in a row of filters.
 */
@Composable
fun ChoiceCard(
    title: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaymarkShapes.control)
            .background(if (selected) colors.amber(0.14f) else colors.panelFaint)
            .border(
                1.dp,
                if (selected) colors.amber(0.55f) else colors.borderFaint,
                WaymarkShapes.control,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = WaymarkSpacing.small, vertical = WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        WaymarkIcon(
            icon = icon,
            tint = if (selected) colors.accentAmber else colors.textDim,
            size = 18.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = Waymark.type.bodySmall,
                color = if (selected) colors.textHeading else colors.textBody,
            )
            Text(text = detail, style = Waymark.type.hint, color = colors.textDim)
        }
    }
}

/** The same choice as a compact chip, for places a full card would not fit. */
@Composable
fun ChoiceChip(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    Row(
        modifier = modifier
            .clip(WaymarkShapes.chip)
            .background(if (selected) colors.amber(0.16f) else colors.chip)
            .border(
                1.dp,
                if (selected) colors.amber(0.5f) else colors.borderFaint,
                WaymarkShapes.chip,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = WaymarkSpacing.small, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WaymarkIcon(
            icon = icon,
            tint = if (selected) colors.accentAmber else colors.textDim,
            size = 14.dp,
        )
        Text(
            text = title,
            style = Waymark.type.control,
            color = if (selected) colors.textHeading else colors.textMuted,
        )
    }
}
