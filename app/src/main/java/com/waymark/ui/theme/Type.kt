package com.waymark.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type is identical in both themes; only colour changes.
 *
 * The three families are the platform's own — a serif with book proportions,
 * the system sans, and the system mono. Nothing is downloaded, so the app
 * renders with the same character on a plane as it does on wifi.
 *
 * Display serif carries headings, numbers and verdicts. Mono carries anything
 * data-flavoured: codes, times, all-caps tracked labels. Sans carries prose
 * and controls.
 */
@Immutable
class WaymarkTypography(
    val displayFamily: FontFamily = FontFamily.Serif,
    val bodyFamily: FontFamily = FontFamily.SansSerif,
    val monoFamily: FontFamily = FontFamily.Monospace,
) {
    /** 3rem — the mobile step of the page title. */
    val pageTitle = TextStyle(
        fontFamily = displayFamily,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.9).sp,
        fontWeight = FontWeight.Normal,
    )

    val screenTitle = TextStyle(
        fontFamily = displayFamily,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    )

    /** 1.875rem, italic — the section voice. */
    val sectionHeading = TextStyle(
        fontFamily = displayFamily,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontStyle = FontStyle.Italic,
    )

    val cardTitle = TextStyle(
        fontFamily = displayFamily,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    )

    /** Stat and hub numbers. */
    val stat = TextStyle(
        fontFamily = displayFamily,
        fontSize = 30.sp,
        lineHeight = 32.sp,
    )

    val verdict = TextStyle(
        fontFamily = displayFamily,
        fontSize = 36.sp,
        lineHeight = 40.sp,
    )

    val tagline = TextStyle(
        fontFamily = displayFamily,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontStyle = FontStyle.Italic,
    )

    val body = TextStyle(
        fontFamily = bodyFamily,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    )

    val bodySmall = TextStyle(
        fontFamily = bodyFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    val control = TextStyle(
        fontFamily = bodyFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    /** All-caps tracked mono. The core motif: eyebrows and section markers. */
    val sectionLabel = TextStyle(
        fontFamily = monoFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.75.sp,
    )

    /** All-caps tracked mono, one step down. Field labels. */
    val fieldLabel = TextStyle(
        fontFamily = monoFamily,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 2.sp,
    )

    /** Serial numbers, times, codes — anything technical. */
    val data = TextStyle(
        fontFamily = monoFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    val dataSmall = TextStyle(
        fontFamily = monoFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val dataLarge = TextStyle(
        fontFamily = monoFamily,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.sp,
    )

    /** Hints and footnotes — serif italic, the signature aside. */
    val hint = TextStyle(
        fontFamily = displayFamily,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontStyle = FontStyle.Italic,
    )

    val buttonLabel = TextStyle(
        fontFamily = bodyFamily,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.9.sp,
        fontWeight = FontWeight.Medium,
    )
}
