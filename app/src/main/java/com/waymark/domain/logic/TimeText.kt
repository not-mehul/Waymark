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
 * Kept in one place so the timeline, the map and the vault never disagree.
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

    /** "2h 40m", "45m", "−15m" for negatives. */
    fun duration(minutes: Int): String {
        val sign = if (minutes < 0) "−" else ""
        val total = abs(minutes)
        val hours = total / 60
        val mins = total % 60
        return when {
            hours == 0 -> "$sign${mins}m"
            mins == 0 -> "$sign${hours}h"
            else -> "$sign${hours}h ${mins}m"
        }
    }

    fun durationBetween(startMillis: Long, endMillis: Long): String =
        duration(((endMillis - startMillis) / 60_000L).toInt())

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
