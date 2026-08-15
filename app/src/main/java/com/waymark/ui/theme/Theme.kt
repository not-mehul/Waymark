package com.waymark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

enum class ThemeMode { SYSTEM, DUSK, DAWN }

/** The live theme, and the one control that changes it. */
@Immutable
class ThemeController(
    val mode: ThemeMode,
    val isDusk: Boolean,
    val setMode: (ThemeMode) -> Unit,
) {
    /** The toggle is binary; SYSTEM resolves to whichever side it currently sits on. */
    fun toggle() = setMode(if (isDusk) ThemeMode.DAWN else ThemeMode.DUSK)
}

val LocalWaymarkColors: ProvidableCompositionLocal<WaymarkColors> =
    staticCompositionLocalOf { DuskColors }

val LocalWaymarkTypography: ProvidableCompositionLocal<WaymarkTypography> =
    staticCompositionLocalOf { WaymarkTypography() }

val LocalThemeController: ProvidableCompositionLocal<ThemeController> =
    staticCompositionLocalOf {
        ThemeController(ThemeMode.SYSTEM, true) {}
    }

/**
 * One system, two expressions. Switching themes swaps a single token set;
 * no component rule changes, because no component names a colour.
 */
@Composable
fun WaymarkTheme(
    initialMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    val systemDark = isSystemInDarkTheme()
    val isDusk = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DUSK -> true
        ThemeMode.DAWN -> false
    }
    val colors = if (isDusk) DuskColors else DawnColors
    val typography = remember { WaymarkTypography() }
    val controller = ThemeController(mode, isDusk) { mode = it }

    // Native surfaces — status bar icons, scrollbars — follow the theme, the
    // Android equivalent of `color-scheme`. Applied as a side effect so that
    // composition itself stays free of window mutation.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDusk
                    isAppearanceLightNavigationBars = !isDusk
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalWaymarkColors provides colors,
        LocalWaymarkTypography provides typography,
        LocalThemeController provides controller,
        LocalTextStyle provides typography.body.copy(color = colors.textBody),
    ) {
        MaterialTheme(
            // Material's scheme is present only so its primitives (ripples,
            // text selection) do not fight the palette. Nothing reads it directly.
            colorScheme = if (isDusk) {
                darkColorScheme(
                    primary = colors.accentBright,
                    onPrimary = colors.onAccent,
                    background = colors.backgroundMid,
                    surface = colors.panel,
                    onSurface = colors.textBody,
                    error = colors.danger,
                )
            } else {
                lightColorScheme(
                    primary = colors.accentBright,
                    onPrimary = colors.onAccent,
                    background = colors.backgroundMid,
                    surface = colors.panel,
                    onSurface = colors.textBody,
                    error = colors.danger,
                )
            },
            content = content,
        )
    }
}

/** Token access. `Waymark.colors.textDim`, never a literal. */
object Waymark {
    val colors: WaymarkColors
        @Composable @ReadOnlyComposable get() = LocalWaymarkColors.current

    val type: WaymarkTypography
        @Composable @ReadOnlyComposable get() = LocalWaymarkTypography.current

    val shapes: WaymarkShapes get() = WaymarkShapes

    val theme: ThemeController
        @Composable @ReadOnlyComposable get() = LocalThemeController.current
}
