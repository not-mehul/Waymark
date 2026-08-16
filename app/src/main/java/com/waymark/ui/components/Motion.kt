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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.provider.Settings

/**
 * The whole motion vocabulary of the app, in one file.
 *
 * The rule from the style reference is that motion is responsiveness, not
 * decoration — nothing bounces, nothing spins for its own sake, and nothing
 * animates on load that the reader is waiting for. What is left is worth
 * having: a screen's content settling in rather than snapping, a tick that
 * draws itself when you pack something, a meter that fills to its value, a
 * number that counts rather than jumping. All of it is short, all of it is
 * eased out, and none of it blocks a tap.
 *
 * ### Reduced motion
 *
 * Every animation here goes through [motionScale], which reads the platform's
 * animator duration setting. A traveler who has turned animations off in
 * Android's accessibility settings — or is in battery saver, which does the
 * same thing — gets the final state immediately, everywhere, with no separate
 * code path to forget about.
 */
object Motion {

    /** Everything decelerates into place; nothing accelerates out of it. */
    val easing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** A control acknowledging a tap. */
    const val QUICK = 140

    /** Content arriving, a meter filling, a value changing. */
    const val SETTLE = 260

    /** The longest thing in the app: a tick drawing itself. */
    const val DRAW = 420
}

/**
 * 1 normally, 0 when the platform has been told to stop animating.
 *
 * Read once per composition rather than watched: this setting changes about as
 * often as the device is set up, and observing it would cost a
 * `ContentObserver` on every animated composable in the app.
 */
@Composable
fun motionScale(): Float {
    val view = LocalView.current
    return remember(view) {
        if (view.isInEditMode) {
            1f
        } else {
            runCatching {
                Settings.Global.getFloat(
                    view.context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
        }
    }
}

/** A duration, scaled by the platform setting. Zero means "arrive already there". */
@Composable
fun motionMillis(base: Int): Int = (base * motionScale()).toInt()

/**
 * Content that settles in: a short rise and fade, once, on first composition.
 *
 * Used for a screen's body and for panels that appear in place. [order] staggers
 * a list so rows arrive in sequence rather than as one block — capped, because
 * the tenth row of a long list should not wait a second to exist.
 */
@Composable
fun Modifier.settleIn(
    order: Int = 0,
    rise: Dp = 10.dp,
): Modifier {
    val scale = motionScale()
    var visible by remember { mutableStateOf(scale <= 0f) }
    val delay = (order.coerceAtMost(6) * 45 * scale).toInt()

    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = (Motion.SETTLE * scale).toInt(),
            delayMillis = delay,
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
    val scale = motionScale()
    var started by remember { mutableStateOf(!fromZero) }
    LaunchedEffect(Unit) { started = true }

    return animateFloatAsState(
        targetValue = if (started) target else 0f,
        animationSpec = tween((durationMillis * scale).toInt(), easing = Motion.easing),
        label = "value",
    )
}

/**
 * Progress from 0 to 1 while [on] is true, and straight back to 0 when it is
 * not — the shape a tick or a stroke draws itself along.
 */
@Composable
fun drawProgress(on: Boolean, durationMillis: Int = Motion.DRAW): State<Float> {
    val scale = motionScale()
    return animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (on) (durationMillis * scale).toInt() else 0,
            easing = Motion.easing,
        ),
        label = "draw",
    )
}

/** A press that gives slightly under the finger. The only scale effect in the app. */
@Stable
fun Modifier.pressGive(pressed: Boolean, to: Float = 0.97f): Modifier = this.graphicsLayer {
    val factor = if (pressed) to else 1f
    scaleX = factor
    scaleY = factor
}
