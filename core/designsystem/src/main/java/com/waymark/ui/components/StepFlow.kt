package com.waymark.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * One question at a time.
 *
 * Adding a plan used to be a single scrolling form of ten fields, most of
 * which did not apply to what was being added, and adding an idea was a modal
 * with four sections. Both asked a traveler to hold the whole shape of the
 * thing in their head while filling in the middle of it.
 *
 * A step flow asks one thing per screen, in the order the answers actually
 * arrive: what kind of thing, then what it is called, then when, then the
 * details nobody has to hand. The progress rail is tappable, so any earlier
 * step can be corrected without unwinding the later ones — the answers live in
 * the caller's state and survive going back and forward. Dismissing at any
 * point saves nothing, which is why the last step is the only one with a
 * commit control on it.
 */
@Composable
fun StepFlow(
    steps: List<String>,
    index: Int,
    title: String,
    onStep: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    WaymarkModal(title = title, eyebrow = steps.getOrNull(index), onDismiss = onDismiss) {
        StepRail(
            steps = steps,
            index = index,
            // Forward is earned, not offered: only a step already visited can
            // be jumped to.
            onStep = { if (it <= index) onStep(it) },
            modifier = modifier,
        )

        // Sliding in the travelled direction is the whole animation budget
        // here: it says "you went back" without anything bouncing.
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState
                val distance = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(220)) { width -> distance * width / 6 } +
                        fadeIn(tween(180))
                    ) togetherWith (
                    slideOutHorizontally(tween(220)) { width -> -distance * width / 6 } +
                        fadeOut(tween(120))
                    ) using SizeTransform(clip = false)
            },
            label = "step",
        ) { step ->
            Column(
                // A floor, so the card does not jump between a one-field step
                // and a four-field one.
                modifier = Modifier.heightIn(min = 148.dp),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
            ) {
                content(step)
            }
        }
    }
}

/**
 * The rail: one segment per step, filled up to where you are, tappable back.
 * It replaces a step counter — "2 of 4" is a fact, a rail is a position.
 */
@Composable
private fun StepRail(
    steps: List<String>,
    index: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Waymark.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEachIndexed { step, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(WaymarkShapes.chip)
                    .clickable(enabled = step <= index, role = Role.Button) { onStep(step) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            when {
                                step < index -> colors.amber(0.55f)
                                step == index -> colors.accentAmber
                                else -> colors.borderFaint
                            }
                        )
                )
            }
        }
    }
}

/**
 * The footer every step shares: back on the left, the forward action on the
 * right, and nothing else. The forward action is "Next" until the last step,
 * where it commits.
 *
 * The two halves are the same width and the same height. An earlier version
 * gave the forward button 1.4 of the row's width for emphasis, which — on top
 * of `MutedButton` being four pixels shorter than `PrimaryButton` at the time
 * — read as a mistake rather than as a hierarchy. The colour does the
 * emphasis; the geometry stays even.
 */
@Composable
fun StepFooter(
    onBack: (() -> Unit)?,
    forwardLabel: String,
    forwardEnabled: Boolean,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    finishing: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = WaymarkSpacing.snug),
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MutedButton(
                text = "Back",
                icon = WaymarkIcons.ChevronLeft,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
        }
        PrimaryButton(
            text = forwardLabel,
            icon = if (finishing) WaymarkIcons.Check else WaymarkIcons.ChevronRight,
            onClick = onForward,
            enabled = forwardEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A step's own heading: the question, asked plainly, with no field label under it. */
@Composable
fun StepQuestion(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Waymark.type.cardTitle,
        color = Waymark.colors.textHeading,
        modifier = modifier,
    )
}
