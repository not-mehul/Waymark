package com.waymark

/**
 * Spans of time in the words the page uses for them, for what the shell draws
 * on its own while the app is closed: the journey's countdown and the
 * widget's "in 3h". The same rules as `duration` and `relative` in
 * src/domain/time-text.ts.
 */
object Spans {

    const val MINUTE_MILLIS = 60_000L
    private const val MINUTES_PER_DAY = 24 * 60L
    private const val DAYS_PER_WEEK = 7L

    /** Four weeks, not a calendar month: a duration has no particular month to be the length of. */
    private const val DAYS_PER_MONTH = 28L

    /** `2h 40m`, `3d 4h`, `2w 3d`, `2mo 1w 3d`. Zero terms are dropped; negatives keep their sign. */
    fun duration(minutes: Long): String {
        val sign = if (minutes < 0) "−" else ""
        val total = kotlin.math.abs(minutes)
        val days = total / MINUTES_PER_DAY
        val terms: List<Pair<Long, String>> = when {
            days >= DAYS_PER_MONTH -> listOf(
                days / DAYS_PER_MONTH to "mo",
                days % DAYS_PER_MONTH / DAYS_PER_WEEK to "w",
                days % DAYS_PER_WEEK to "d",
            )

            days >= DAYS_PER_WEEK -> listOf(days / DAYS_PER_WEEK to "w", days % DAYS_PER_WEEK to "d")

            days >= 1 -> listOf(days to "d", total % MINUTES_PER_DAY / 60 to "h")

            else -> listOf(total / 60 to "h", total % 60 to "m")
        }
        val written = terms.filter { it.first > 0 }.joinToString(" ") { "${it.first}${it.second}" }
        return sign + written.ifEmpty { "0${terms.last().second}" }
    }

    /** "in 3h 10m", "12m ago", "now". */
    fun relative(targetMillis: Long, nowMillis: Long): String {
        val minutes = (targetMillis - nowMillis) / MINUTE_MILLIS
        return when {
            kotlin.math.abs(minutes) < 1 -> "now"
            minutes > 0 -> "in ${duration(minutes)}"
            else -> "${duration(-minutes)} ago"
        }
    }

    /** Whole minutes until [targetMillis], rounded up so "1m" means not yet. */
    fun minutesUntil(targetMillis: Long, nowMillis: Long): Long =
        (targetMillis - nowMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS
}
