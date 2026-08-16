package com.waymark.domain.logic

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Every string the app prints for a time, a duration, or a date.
 * Kept in one place so the timeline, the map and the numbers never disagree.
 */
object TimeText {

    private val clock24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val clock12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val monthDay = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val zoneAbbrev = DateTimeFormatter.ofPattern("zzz", Locale.US)

    fun clock(time: ZonedDateTime, use24Hour: Boolean = true): String =
        time.format(if (use24Hour) clock24 else clock12)

    fun day(date: LocalDate): String = date.format(dayLong)
    fun dayCompact(date: LocalDate): String = date.format(dayShort)
    fun monthDay(date: LocalDate): String = date.format(monthDay)

    fun zoneLabel(time: ZonedDateTime): String = time.format(zoneAbbrev)

    fun dateRange(start: LocalDate, end: LocalDate): String = when {
        start == end -> start.format(dayShort)
        start.year == end.year && start.month == end.month ->
            "${start.dayOfMonth}–${end.dayOfMonth} ${start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))}"
        start.year == end.year -> "${start.format(monthDay)} – ${end.format(monthDay)} ${start.year}"
        else -> "${start.format(monthDay)} ${start.year} – ${end.format(monthDay)} ${end.year}"
    }

    /**
     * A span, in the units a person would actually use for it.
     *
     * Precision has to fall away as the span grows or the number stops meaning
     * anything: a connection is "2h 40m", but a fortnight in Italy is not
     * "336h", and a two-month sabbatical is certainly not "1440h". Each
     * threshold drops the smallest unit and picks up a larger one.
     *
     * | Span | Reads as |
     * | --- | --- |
     * | under a day | `2h 40m`, `45m` |
     * | a day to a week | `3d 4h` |
     * | a week to four weeks | `2w 3d` |
     * | four weeks or more | `2mo 1w 3d` |
     *
     * Terms that are zero are dropped, so a clean fortnight is `2w`, not
     * `2w 0d`. Negatives keep their sign: a connection can be short by twenty
     * minutes.
     */
    fun duration(minutes: Int): String {
        val sign = if (minutes < 0) "−" else ""
        val total = abs(minutes)
        val days = total / MINUTES_PER_DAY

        val terms: List<Pair<Int, String>> = when {
            days >= DAYS_PER_MONTH -> listOf(
                days / DAYS_PER_MONTH to "mo",
                (days % DAYS_PER_MONTH) / DAYS_PER_WEEK to "w",
                days % DAYS_PER_WEEK to "d",
            )

            days >= DAYS_PER_WEEK -> listOf(
                days / DAYS_PER_WEEK to "w",
                days % DAYS_PER_WEEK to "d",
            )

            days >= 1 -> listOf(
                days to "d",
                (total % MINUTES_PER_DAY) / 60 to "h",
            )

            else -> listOf(
                total / 60 to "h",
                total % 60 to "m",
            )
        }

        val written = terms.filter { it.first > 0 }.joinToString(" ") { "${it.first}${it.second}" }
        // Everything rounded away — a zero span, or forty seconds.
        return sign + written.ifEmpty { "0${terms.last().second}" }
    }

    fun durationBetween(startMillis: Long, endMillis: Long): String =
        duration(((endMillis - startMillis) / 60_000L).toInt())

    private const val MINUTES_PER_DAY = 24 * 60
    private const val DAYS_PER_WEEK = 7

    /**
     * A "month" here is four weeks, not a calendar month. A duration is a
     * length, not a range between two dates, so it has no particular month to
     * be the length of — and 28 days keeps `1mo` meaning the same thing in
     * January as in February.
     */
    private const val DAYS_PER_MONTH = 28

    /** "in 3h 10m", "12m ago", "now". */
    fun relative(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val deltaMinutes = ((targetMillis - nowMillis) / 60_000L).toInt()
        return when {
            abs(deltaMinutes) < 1 -> "now"
            deltaMinutes > 0 -> "in ${duration(deltaMinutes)}"
            else -> "${duration(-deltaMinutes)} ago"
        }
    }

    /** Day offset shown next to an arrival that lands on another date: "+1". */
    fun dayOffsetSuffix(departure: ZonedDateTime, arrival: ZonedDateTime): String? {
        val days = ChronoUnit.DAYS.between(departure.toLocalDate(), arrival.toLocalDate()).toInt()
        return when {
            days == 0 -> null
            days > 0 -> "+$days"
            else -> "$days"
        }
    }

    /** "Today", "Tomorrow", else the long day. */
    fun dayHeadline(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day(date)
    }

    /** "+7h" — the shift a traveler's body is being asked to absorb. */
    fun zoneShift(from: ZoneId, to: ZoneId, atMillis: Long): String? {
        val instant = java.time.Instant.ofEpochMilli(atMillis)
        val fromOffset = from.rules.getOffset(instant).totalSeconds
        val toOffset = to.rules.getOffset(instant).totalSeconds
        val deltaMinutes = (toOffset - fromOffset) / 60
        if (deltaMinutes == 0) return null
        val sign = if (deltaMinutes > 0) "+" else "−"
        return sign + duration(abs(deltaMinutes))
    }
}
