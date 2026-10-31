package com.waymark.domain.logic

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Turning two local clock times into a leg with a real length.
 *
 * Every screen that takes a start and an end used to do its own version of
 * "if it ends before it starts, add a day", and every version was wrong for
 * somebody: SFO 21:45 to Singapore 06:30 lands two calendar days later, and
 * Hong Kong 00:50 to Los Angeles 22:15 lands the *day before* it left. One
 * day forward fixes neither.
 */
object LegTimes {

    /**
     * Longer than any scheduled flight (the longest are about nineteen hours),
     * short enough that a time typed against the wrong airport is caught.
     */
    const val MAX_FLIGHT_MINUTES = 22 * 60

    /** The calendar shifts an arrival can sit on, relative to the departure date. */
    val DAY_OFFSETS: IntRange = -1..2

    data class Leg(val departure: ZonedDateTime, val arrival: ZonedDateTime) {
        val minutes: Long get() = Duration.between(departure, arrival).toMinutes()

        /** Arrival's local date minus departure's local date: `+1`, `-1`, `0`. */
        val dayOffset: Int
            get() = ChronoUnit.DAYS.between(departure.toLocalDate(), arrival.toLocalDate()).toInt()
    }

    /**
     * A flight from local departure and arrival clock times.
     *
     * With [dayOffset] null the arrival date is inferred: of the calendar days
     * the arrival could fall on, the one that gives the shortest positive
     * flight wins. Flights are under a day long, so exactly one candidate is
     * ever plausible. With [dayOffset] given, that day is used as stated.
     *
     * Null when the times cannot describe a flight: zero or negative length,
     * or longer than [MAX_FLIGHT_MINUTES].
     */
    fun flight(
        date: LocalDate,
        departs: LocalTime,
        departureZone: ZoneId,
        arrives: LocalTime,
        arrivalZone: ZoneId,
        dayOffset: Int? = null,
    ): Leg? {
        val departure = ZonedDateTime.of(date, departs, departureZone)
        val offsets = dayOffset?.let { listOf(it) } ?: DAY_OFFSETS.toList()
        return offsets
            .map { offset ->
                Leg(departure, ZonedDateTime.of(date.plusDays(offset.toLong()), arrives, arrivalZone))
            }
            .filter { it.minutes in 1..MAX_FLIGHT_MINUTES }
            .minByOrNull { it.minutes }
    }

    /**
     * An end that should follow a start: a train, a dinner, an edited booking.
     *
     * When the end as typed is not after the start but would be a day later,
     * it is read as running past midnight — the 22:40 sleeper arriving at
     * 07:10. Anything further out of order is returned unchanged for the
     * caller to refuse, rather than silently moved by days.
     */
    fun endAfter(start: ZonedDateTime, end: LocalDateTime, endZone: ZoneId): ZonedDateTime {
        val asTyped = ZonedDateTime.of(end, endZone)
        if (asTyped.toInstant().isAfter(start.toInstant())) return asTyped
        val nextDay = asTyped.plusDays(1)
        return if (nextDay.toInstant().isAfter(start.toInstant())) nextDay else asTyped
    }

    /** True when [end] follows [start] — the check every save button needs. */
    fun isOrdered(start: ZonedDateTime, end: ZonedDateTime): Boolean =
        end.toInstant().isAfter(start.toInstant())
}
