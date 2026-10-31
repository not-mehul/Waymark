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
    val onInvert: Color,
    val onDanger: Color,
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

    // The map. Water and land are the only two places in the system where a
    // colour carries meaning rather than emphasis, so they get their own
    // tokens rather than borrowing a surface that happens to look right.
    val mapWater: Color,
    val mapLand: Color,
    val mapCoast: Color,
    val mapGrid: Color,

    val isDusk: Boolean,
) {
    /** Composable alpha over the accent, standing in for the CSS RGB triples. */
    fun amber(alpha: Float): Color = accentAmber.copy(alpha = alpha)
    fun sage(alpha: Float): Color = accentSage.copy(alpha = alpha)
    fun cream(alpha: Float): Color = textHeading.copy(alpha = alpha)
    fun shadow(alpha: Float): Color = shadow.copy(alpha = alpha * shadowStrength)
}

/** Editorial Dusk — the dark theme, and the default. Values come from design/tokens.json. */
val DuskColors = WaymarkColors(
    backgroundTop = DuskTokens.bgGradTop,
    backgroundMid = DuskTokens.bgGradMid,
    backgroundBottom = DuskTokens.bgGradBot,

    textStrong = DuskTokens.textStrong,
    textHeading = DuskTokens.textHeading,
    textBody = DuskTokens.textBody,
    textMuted = DuskTokens.textMuted,
    textDim = DuskTokens.textDim,
    textFaint = DuskTokens.textFaint,

    accentAmber = DuskTokens.accentAmber,
    accentBright = DuskTokens.accentBright,
    accentBrightHover = DuskTokens.accentBrightHover,
    accentSage = DuskTokens.accentSage,
    onAccent = DuskTokens.onAccent,
    onInvert = DuskTokens.onInvert,
    onDanger = DuskTokens.onDanger,
    danger = DuskTokens.danger,

    border = DuskTokens.border,
    borderStrong = DuskTokens.borderStrong,
    borderSoft = DuskTokens.borderSoft,
    borderFaint = DuskTokens.borderFaint,

    panel = DuskTokens.panelBg,
    panelFaint = DuskTokens.panelBgFaint,
    input = DuskTokens.inputBg,
    chip = DuskTokens.chipBg,
    chipHover = DuskTokens.chipBgHover,
    hover = DuskTokens.hoverBg,
    disabled = DuskTokens.disabledBg,
    modal = DuskTokens.modalBg,
    modalBackdrop = DuskTokens.modalBackdrop,

    focusRing = DuskTokens.focusRing,
    shadow = DuskTokens.shadowRgb,
    shadowStrength = DuskTokens.SHADOW_STRENGTH,

    dangerWash = DuskTokens.errorBg,
    noticeWash = DuskTokens.noticeBg,

    mapWater = DuskTokens.mapWater,
    mapLand = DuskTokens.mapLand,
    mapCoast = DuskTokens.mapCoast,
    mapGrid = DuskTokens.mapGrid,

    isDusk = true,
)

/** Editorial Dawn — warm paper, never stark white. Values come from design/tokens.json. */
val DawnColors = WaymarkColors(
    backgroundTop = DawnTokens.bgGradTop,
    backgroundMid = DawnTokens.bgGradMid,
    backgroundBottom = DawnTokens.bgGradBot,

    textStrong = DawnTokens.textStrong,
    textHeading = DawnTokens.textHeading,
    textBody = DawnTokens.textBody,
    textMuted = DawnTokens.textMuted,
    textDim = DawnTokens.textDim,
    textFaint = DawnTokens.textFaint,

    accentAmber = DawnTokens.accentAmber,
    accentBright = DawnTokens.accentBright,
    accentBrightHover = DawnTokens.accentBrightHover,
    accentSage = DawnTokens.accentSage,
    onAccent = DawnTokens.onAccent,
    onInvert = DawnTokens.onInvert,
    onDanger = DawnTokens.onDanger,
    danger = DawnTokens.danger,

    border = DawnTokens.border,
    borderStrong = DawnTokens.borderStrong,
    borderSoft = DawnTokens.borderSoft,
    borderFaint = DawnTokens.borderFaint,

    panel = DawnTokens.panelBg,
    panelFaint = DawnTokens.panelBgFaint,
    input = DawnTokens.inputBg,
    chip = DawnTokens.chipBg,
    chipHover = DawnTokens.chipBgHover,
    hover = DawnTokens.hoverBg,
    disabled = DawnTokens.disabledBg,
    modal = DawnTokens.modalBg,
    modalBackdrop = DawnTokens.modalBackdrop,

    focusRing = DawnTokens.focusRing,
    shadow = DawnTokens.shadowRgb,
    shadowStrength = DawnTokens.SHADOW_STRENGTH,

    dangerWash = DawnTokens.errorBg,
    noticeWash = DawnTokens.noticeBg,

    mapWater = DawnTokens.mapWater,
    mapLand = DawnTokens.mapLand,
    mapCoast = DawnTokens.mapCoast,
    mapGrid = DawnTokens.mapGrid,

    isDusk = false,
)
