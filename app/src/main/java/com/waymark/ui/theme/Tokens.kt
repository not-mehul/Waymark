package com.waymark.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * "Editorial Dusk & Dawn" as a semantic token set.
 *
 * The rule from the style reference holds here exactly as it does in CSS:
 * **no component may name a colour**. Every colour in the app comes from this
 * class, and every token is defined in both themes — a token added to one
 * theme only is a bug.
 *
 * Names describe role, never hue: `textHeading`, not `cream`. That is what
 * lets a single composable serve both themes.
 */
@Immutable
data class WaymarkColors(
    // Surface gradient — the page is never a flat fill.
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,

    // Text, six steps from highest emphasis to decoration.
    val textStrong: Color,
    val textHeading: Color,
    val textBody: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,

    // Accents — small doses only.
    val accentAmber: Color,
    val accentBright: Color,
    val accentBrightHover: Color,
    val accentSage: Color,
    val onAccent: Color,
    val danger: Color,

    // Lines.
    val border: Color,
    val borderStrong: Color,
    val borderSoft: Color,
    val borderFaint: Color,

    // Surfaces, lightest to densest.
    val panel: Color,
    val panelFaint: Color,
    val input: Color,
    val chip: Color,
    val chipHover: Color,
    val hover: Color,
    val disabled: Color,
    val modal: Color,
    val modalBackdrop: Color,

    // Effects.
    val focusRing: Color,
    val shadow: Color,
    val shadowStrength: Float,

    // Semantic state washes.
    val dangerWash: Color,
    val noticeWash: Color,

    val isDusk: Boolean,
) {
    /** Composable alpha over the accent, standing in for the CSS RGB triples. */
    fun amber(alpha: Float): Color = accentAmber.copy(alpha = alpha)
    fun sage(alpha: Float): Color = accentSage.copy(alpha = alpha)
    fun cream(alpha: Float): Color = textHeading.copy(alpha = alpha)
    fun shadow(alpha: Float): Color = shadow.copy(alpha = alpha * shadowStrength)
}

/** Editorial Dusk — the dark theme, and the default. */
val DuskColors = WaymarkColors(
    backgroundTop = Color(0xFF14110E),
    backgroundMid = Color(0xFF0F0D0B),
    backgroundBottom = Color(0xFF0C0A08),

    textStrong = Color(0xFFF5F5F4),
    textHeading = Color(0xFFF5ECD9),
    textBody = Color(0xFFE8E1D5),
    textMuted = Color(0xFFD6D3D1),
    textDim = Color(0xFFA8A29E),
    textFaint = Color(0xFF78716C),

    accentAmber = Color(0xFFD4A574),
    accentBright = Color(0xFFFCD34D),
    accentBrightHover = Color(0xFFFEF3C7),
    accentSage = Color(0xFF9FB3A0),
    onAccent = Color(0xFF1C1917),
    danger = Color(0xFFF87171),

    border = Color(0xFF44403C),
    borderStrong = Color(0x9944403C),
    borderSoft = Color(0x8044403C),
    borderFaint = Color(0x6644403C),

    panel = Color(0x9914110E),
    panelFaint = Color(0x6614110E),
    input = Color(0xCC0C0A08),
    chip = Color(0x991C1917),
    chipHover = Color(0xD91C1917),
    hover = Color(0x66292524),
    disabled = Color(0x99292524),
    modal = Color(0xF514110E),
    modalBackdrop = Color(0xD10C0A08),

    focusRing = Color(0x80A8A29E),
    shadow = Color(0xFF000000),
    shadowStrength = 1f,

    dangerWash = Color(0x33450A0A),
    noticeWash = Color(0x1AFCD34D),

    isDusk = true,
)

/** Editorial Dawn — warm paper, never stark white. */
val DawnColors = WaymarkColors(
    backgroundTop = Color(0xFFF3ECE0),
    backgroundMid = Color(0xFFEFE6D8),
    backgroundBottom = Color(0xFFE9DFCE),

    textStrong = Color(0xFF1C1917),
    textHeading = Color(0xFF211D17),
    textBody = Color(0xFF3A342C),
    textMuted = Color(0xFF4A443B),
    textDim = Color(0xFF6B6358),
    textFaint = Color(0xFF938A7C),

    accentAmber = Color(0xFFB07D3E),
    accentBright = Color(0xFFE0A92A),
    accentBrightHover = Color(0x60C89320),
    accentSage = Color(0xFF6F8770),
    onAccent = Color(0xFF1C1917),
    danger = Color(0xFFC0392B),

    border = Color(0xFFCDBFA6),
    borderStrong = Color(0x73786950),
    borderSoft = Color(0x52786950),
    borderFaint = Color(0x38786950),

    panel = Color(0xB3FFFBF3),
    panelFaint = Color(0x73FFFBF3),
    input = Color(0xE6FFFCF5),
    chip = Color(0xD9F5EEE2),
    chipHover = Color(0xF2EEE5D5),
    hover = Color(0x4DCDBFA6),
    disabled = Color(0x99DCD2C0),
    modal = Color(0xFAFCF8F0),
    modalBackdrop = Color(0x733C3428),

    focusRing = Color(0x80786950),
    // A black shadow on cream reads as dirt; Dawn shadows are warm brown and softer.
    shadow = Color(0xFF5A482C),
    shadowStrength = 0.4f,

    dangerWash = Color(0x1FC0392B),
    noticeWash = Color(0x24E0A92A),

    isDusk = false,
)
