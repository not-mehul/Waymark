package com.waymark.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The motion vocabulary of the app.
 *
 * The rule from the style reference is that motion is responsiveness, not
 * decoration — nothing bounces, nothing spins for its own sake, and nothing
 * animates on load that the reader is waiting for. What is left: content
 * settling in rather than snapping, a meter filling to its value, a modal
 * arriving. All of it is short, eased out, and blocks no tap.
 *
 * ### Reduced motion
 *
 * Nothing here scales its own durations. Compose already runs every animation
 * at the platform's animator duration scale — Android's "remove animations"
 * accessibility setting, and battery saver, set it to zero — so a traveler who
 * has turned animations off gets the final state immediately, everywhere,
 * including in the controls that animate without going through this file. An
 * earlier version also multiplied by the scale here, which at the developer
 * setting of 0.5× halved everything twice.
 */
object Motion {

    /** Everything decelerates into place; nothing accelerates out of it. */
    val easing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Content arriving, a meter filling, a value changing. */
    const val SETTLE = 260
}

/**
 * Remembers which rows of a list have already settled in, so a row scrolled
 * out of view and back does not arrive a second time. A lazy list forgets a
 * row's own state when it scrolls away; this lives with the list instead.
 */
@Stable
class SettleOnce {
    private val settled = mutableSetOf<String>()

    internal fun claim(key: String): Boolean = settled.add(key)
}

@Composable
fun rememberSettleOnce(): SettleOnce = remember { SettleOnce() }

/**
 * Content that settles in: a short rise and fade, once.
 *
 * [order] staggers a list so rows arrive in sequence rather than as one block —
 * capped, because the tenth row of a long list should not wait a second to
 * exist. With [once] and a [key], a row that has settled before does not do it
 * again when it scrolls back into view.
 */
@Composable
fun Modifier.settleIn(
    order: Int = 0,
    rise: Dp = 10.dp,
    once: SettleOnce? = null,
    key: String? = null,
): Modifier {
    val first = remember(key) { once == null || key == null || once.claim(key) }
    var visible by remember(key) { mutableStateOf(!first) }
    LaunchedEffect(key) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.SETTLE,
            delayMillis = order.coerceAtMost(6) * 45,
            easing = Motion.easing,
        ),
        label = "settle",
    )

    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * rise.toPx()
    }
}

/**
 * A value that animates towards its target rather than jumping.
 *
 * For meters, ring figures and counted numbers. The first value is *not*
 * animated from zero unless [fromZero] is set: a screen re-entered from the
 * back stack should show its numbers, not re-earn them.
 */
@Composable
fun animatedValue(
    target: Float,
    fromZero: Boolean = false,
    durationMillis: Int = Motion.SETTLE,
): State<Float> {
    var started by remember { mutableStateOf(!fromZero) }
    LaunchedEffect(Unit) { started = true }

    return animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween(durationMillis, easing = Motion.easing),
        label = "value",
    )
}

/** A press that gives slightly under the finger. The only scale effect in the app. */
@Stable
fun Modifier.pressGive(pressed: Boolean, to: Float = 0.97f): Modifier = this.graphicsLayer {
    val factor = if (pressed) to else 1f
    scaleX = factor
    scaleY = factor
}
