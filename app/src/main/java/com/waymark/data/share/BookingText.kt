package com.waymark.data.share

import com.waymark.data.catalog.Airlines
import com.waymark.data.catalog.Airports
import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.model.Segment
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale

/**
 * Flights read out of a confirmation email, a pasted itinerary, a text
 * message — on the device, with nothing sent anywhere.
 *
 * Airline emails have no common format, so this reads the way a person skims
 * one: find each flight number, then look just after it for two airport codes,
 * a date and two times. A flight number counts only when its airline is one
 * Waymark knows and an airport code turns up beside it, which keeps "Room 215"
 * and "Order AB 1234" out. Whatever is found is a draft for the Add flight
 * screen to show and the traveler to check — never saved unseen unless it is
 * complete.
 */
object BookingText {

    data class FoundFlight(
        val designator: String,
        val date: LocalDate?,
        val origin: String?,
        val destination: String?,
        val departs: LocalTime?,
        val arrives: LocalTime?,
    ) {
        val complete: Boolean
            get() = date != null && origin != null && destination != null && departs != null && arrives != null
    }

    data class Reading(val flights: List<FoundFlight>, val reference: String?)

    /**
     * A complete flight as a booking on [tripId], or null when anything is
     * missing or does not add up: an airport Waymark does not know, or times
     * that describe no possible flight. The arrival day is worked out from
     * the two clocks, as on the Add flight screen.
     */
    fun toSegment(found: FoundFlight, tripId: String, id: String, reference: String?): Segment.Flight? {
        val designator = FlightDesignator.parse(found.designator) ?: return null
        val origin = Airports.find(found.origin)?.toPlace() ?: return null
        val destination = Airports.find(found.destination)?.toPlace() ?: return null
        val leg = LegTimes.flight(
            date = found.date ?: return null,
            departs = found.departs ?: return null,
            departureZone = origin.zone,
            arrives = found.arrives ?: return null,
            arrivalZone = destination.zone,
        ) ?: return null
        return Segment.Flight(
            id = id,
            tripId = tripId,
            carrierCode = designator.carrier,
            flightNumber = "${designator.number}${designator.suffix.orEmpty()}",
            origin = origin,
            destination = destination,
            startEpochMillis = leg.departure.toInstant().toEpochMilli(),
            endEpochMillis = leg.arrival.toInstant().toEpochMilli(),
            startZoneId = origin.timeZoneId,
            endZoneId = destination.timeZoneId,
            confirmationCode = reference,
            bookedWith = Airlines.name(designator.carrier),
        )
    }

    private val DESIGNATOR = Regex("\\b([A-Z][A-Z0-9]|[0-9][A-Z])\\s?0*(\\d{1,4})([A-Z]?)\\b")
    private val CODE = Regex("\\b[A-Z]{3}\\b")

    /** 16:20, 4.20 pm, 16h20 — and not the 14.05 of a date written 14.05.2026. */
    private val TIME = Regex("(?<![\\d.:])([01]?\\d|2[0-3])[:.h]([0-5]\\d)(?![\\d:]|\\.\\d)\\s*([AaPp]\\.?[Mm]\\.?)?")
    private val REFERENCE = Regex(
        "(?i)(?:booking reference|booking ref|confirmation(?: code| number)?|record locator|reservation code|pnr)" +
            "\\s*(?:is|:|#|no\\.?)?\\s*([A-Z0-9]{5,8})\\b",
    )

    /** Words that look like airport codes in capitals and are not. */
    private val NOT_STATIONS = setOf(
        "THE", "AND", "FOR", "YOU", "ARE", "NOT", "ALL", "ANY", "PNR", "REF", "TAX", "FEE", "USD", "EUR",
        "GBP", "JPY", "AUD", "CAD", "CHF", "PLC", "LTD", "INC", "VAT", "MON", "TUE", "WED", "THU", "FRI",
        "SAT", "SUN", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
        "SEAT", "GATE",
    )

    fun read(text: String, today: LocalDate = LocalDate.now()): Reading {
        val reference = REFERENCE.find(text)?.groupValues?.get(1)?.uppercase()
        val hits = DESIGNATOR.findAll(text)
            .filter { Airlines.knows(it.groupValues[1]) }
            .toList()

        val flights = hits.mapIndexedNotNull { index, hit ->
            // The flight's own stretch of text: up to the next flight number,
            // and no further than a few hundred characters.
            val end = hits.getOrNull(index + 1)?.range?.first ?: text.length
            val window = text.substring(hit.range.last + 1, minOf(end, hit.range.last + 1 + WINDOW))
            val stations = CODE.findAll(window)
                .map { it.value }
                .filter { it !in NOT_STATIONS && Airports.find(it) != null }
                .distinct()
                .take(2)
                .toList()
            if (stations.isEmpty()) return@mapIndexedNotNull null
            val times = TIME.findAll(window).mapNotNull(::timeOf).take(2).toList()
            FoundFlight(
                designator = hit.groupValues[1] + hit.groupValues[2].trimStart('0') + hit.groupValues[3],
                date = nearestDate(text, hit.range, hit.range.last + 1 + window.length, today),
                origin = stations.getOrNull(0),
                destination = stations.getOrNull(1),
                departs = times.getOrNull(0),
                arrives = times.getOrNull(1),
            )
        }.distinctBy { it.designator to it.date }

        return Reading(flights, reference)
    }

    private const val WINDOW = 400

    private fun timeOf(match: MatchResult): LocalTime? {
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val meridiem = match.groupValues[3].lowercase().filter { it.isLetter() }
        if (meridiem.isNotEmpty()) {
            if (hour !in 1..12) return null
            if (meridiem.startsWith("p") && hour != 12) hour += 12
            if (meridiem.startsWith("a") && hour == 12) hour = 0
        }
        return LocalTime.of(hour, minute)
    }

    // — Dates ———————————————————————————————————————————————————————————————

    private val MONTHS: Map<String, Month> = Month.entries.flatMap { month ->
        listOf(
            month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() to month,
            month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() to month,
        )
    }.toMap() + ("sept" to Month.SEPTEMBER)

    private val MONTH = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private val ISO = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val DAY_MONTH = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*($MONTH)\\.?,?\\s*(\\d{4})?\\b")
    private val MONTH_DAY = Regex("(?i)\\b($MONTH)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s*(\\d{4})?\\b")

    /**
     * The date written closest to the flight number at [hit] — a heading just
     * above it ("Outbound — 14 May") or a phrase just after ("on May 3") —
     * looking no further ahead than [until], where the next flight begins. A
     * year left out is the next one that makes the date upcoming.
     */
    private fun nearestDate(text: String, hit: IntRange, until: Int, today: LocalDate): LocalDate? {
        val scope = text.substring(0, until)
        val candidates = buildList {
            ISO.findAll(scope).forEach { match ->
                val (year, month, day) = match.destructured
                runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }
                    .getOrNull()?.let { add(match.range to it) }
            }
            DAY_MONTH.findAll(scope).forEach { match ->
                dateOf(match.groupValues[1], match.groupValues[2], match.groupValues[3], today)
                    ?.let { add(match.range to it) }
            }
            MONTH_DAY.findAll(scope).forEach { match ->
                dateOf(match.groupValues[2], match.groupValues[1], match.groupValues[3], today)
                    ?.let { add(match.range to it) }
            }
        }
        return candidates.minByOrNull { (range, _) ->
            if (range.first > hit.last) range.first - hit.last else hit.first - range.last
        }?.second
    }

    private fun dateOf(day: String, monthName: String, year: String, today: LocalDate): LocalDate? {
        val month = MONTHS[monthName.lowercase().trimEnd('.')] ?: return null
        val dayOfMonth = day.toIntOrNull() ?: return null
        if (year.isNotEmpty()) {
            return runCatching { LocalDate.of(year.toInt(), month, dayOfMonth) }.getOrNull()
        }
        val monthDay = runCatching { MonthDay.of(month, dayOfMonth) }.getOrNull() ?: return null
        val thisYear = monthDay.atYear(today.year)
        // An email about a flight is about a flight to come; a month already
        // gone this year means next year.
        return if (thisYear.isBefore(today.minusDays(7))) monthDay.atYear(today.year + 1) else thisYear
    }
}
