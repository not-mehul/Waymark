package com.waymark.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The tab rail: words on a hairline, with a short amber rule under the live one.
 *
 * This replaces a `SegmentedToggle` — a bordered box with a bright filled
 * segment — carrying five options. At phone width that is five boxed words
 * competing with the trip title directly above them, and the filled segment
 * shouts about a choice the reader already made. A rule under a word is the
 * quietest thing that can still say "you are here", and it is what the rest of
 * the app's section markers already look like.
 *
 * The indicator animates between tabs; nothing else moves.
 */
@Composable
fun TabRail(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    val density = LocalDensity.current
    var railWidth by remember { mutableStateOf(0.dp) }

    val tabWidth = if (labels.isEmpty()) 0.dp else railWidth / labels.size
    val indicatorWidth = tabWidth * 0.55f
    val indicatorOffset by animateDpAsState(
        targetValue = tabWidth * selectedIndex + (tabWidth - indicatorWidth) / 2,
        animationSpec = tween(durationMillis = 200),
        label = "tab-indicator",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(WaymarkShapes.control)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(index) }
                        .padding(vertical = WaymarkSpacing.snug),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = Waymark.type.control.copy(
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (active) colors.textHeading else colors.textDim,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // The hairline runs the whole width; the rule sits on top of it.
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderFaint)
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(2.dp)
                    .background(colors.accentAmber)
            )
        }
    }
}

/**
 * Who the screen is showing, and the only way to change it.
 *
 * The old control was a row of chips — "Everyone" plus one per traveler —
 * permanently across the top of the timeline. But a trip is for everyone
 * almost all of the time, so that row spent its life displaying a decision
 * nobody was making, and being one thumb-width from changing it by accident.
 *
 * This shows nothing at all in the ordinary case. Filtered, it becomes a
 * visible, dismissible statement of what is being hidden — which is the state
 * that actually needs an indicator.
 */
@Composable
fun PartyFilterBar(
    names: Map<String, String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    if (names.size < 2) return

    if (selected == null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "Showing everyone",
                style = Waymark.type.hint,
                color = colors.textFaint,
                modifier = Modifier
                    .clip(WaymarkShapes.chip)
                    .clickable(role = Role.Button) { onSelect(names.keys.first()) }
                    .padding(horizontal = WaymarkSpacing.snug, vertical = 2.dp),
            )
        }
        return
    }

    // Filtered: name it, and make clearing it the obvious next tap.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaymarkShapes.chip)
            .background(colors.amber(0.14f))
            .padding(horizontal = WaymarkSpacing.small, vertical = WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        Text(
            text = "Showing ${names[selected] ?: "one traveler"} only",
            style = Waymark.type.bodySmall,
            color = colors.textHeading,
            modifier = Modifier.weight(1f),
        )
        // Cycle through the party, then back to everyone.
        val order = names.keys.toList()
        val next = order.getOrNull(order.indexOf(selected) + 1)
        Text(
            text = if (next != null) "Next" else "",
            style = Waymark.type.control,
            color = colors.textDim,
            modifier = Modifier
                .clip(WaymarkShapes.chip)
                .clickable(enabled = next != null, role = Role.Button) { onSelect(next) }
                .padding(horizontal = WaymarkSpacing.snug, vertical = 2.dp),
        )
        GhostIconButton(
            icon = WaymarkIcons.Close,
            contentDescription = "Show everyone",
            onClick = { onSelect(null) },
        )
    }
}
