package com.waymark.data.share

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The text inside a boarding pass's barcode (IATA Resolution 792, "BCBP").
 *
 * Every mobile and printed boarding pass carries the same fixed-width record
 * in its Aztec, PDF417 or QR code: the passenger, then per flight the booking
 * reference, the two airports, the airline, the flight number, the day of the
 * year, the cabin and the seat. That is everything Add flight asks for except
 * the clock times, which a boarding pass does not encode.
 */
object Bcbp {

    data class Leg(
        val designator: String,
        val date: LocalDate,
        val origin: String,
        val destination: String,
        val seat: String?,
        val cabin: String?,
        val reference: String?,
        val passenger: String,
    )

    private const val HEADER = 23
    private const val LEG = 37

    /** Every flight on the pass, or empty when the text is not a boarding pass. */
    fun parse(text: String, today: LocalDate = LocalDate.now()): List<Leg> {
        val data = text.trimEnd('\n', '\r')
        if (data.length < HEADER + LEG || data[0] != 'M') return emptyList()
        val legs = data[1].digitToIntOrNull()?.takeIf { it in 1..9 } ?: return emptyList()
        val passenger = passengerName(data.substring(2, 22))

        val found = mutableListOf<Leg>()
        var at = HEADER
        repeat(legs) {
            if (data.length < at + LEG) return found
            val record = data.substring(at, at + LEG)
            parseLeg(record, passenger, today)?.let { found += it } ?: return found
            val variable = record.substring(35, 37).trim().toIntOrNull(16) ?: 0
            at += LEG + variable
        }
        return found
    }

    private fun parseLeg(record: String, passenger: String, today: LocalDate): Leg? {
        val reference = record.substring(0, 7).trim().ifEmpty { null }
        val origin = record.substring(7, 10).trim()
        val destination = record.substring(10, 13).trim()
        val carrier = record.substring(13, 16).trim()
        val flight = record.substring(16, 21).trim()
        val dayOfYear = record.substring(21, 24).trim().toIntOrNull()
        val compartment = record[24]
        val seat = record.substring(25, 29).trim().trimStart('0').ifEmpty { null }

        if (origin.length != 3 || destination.length != 3 || !origin.all { it.isLetter() }) return null
        if (carrier.length !in 2..3 || dayOfYear == null || dayOfYear !in 1..366) return null
        val digits = flight.takeWhile { it.isDigit() }.trimStart('0').ifEmpty { return null }
        val suffix = flight.drop(flight.takeWhile { it.isDigit() }.length).trim()

        return Leg(
            designator = carrier + digits + suffix,
            date = dateOf(dayOfYear, today),
            origin = origin,
            destination = destination,
            seat = seat,
            cabin = cabinOf(compartment),
            reference = reference,
            passenger = passenger,
        )
    }

    /** The pass gives a day of the year; the year is whichever puts it closest to today. */
    private fun dateOf(dayOfYear: Int, today: LocalDate): LocalDate =
        listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { year -> runCatching { LocalDate.ofYearDay(year, dayOfYear) }.getOrNull() }
            .minBy { abs(ChronoUnit.DAYS.between(today, it)) }

    /** "SMITH/JANE MRS" → "Jane Smith". */
    private fun passengerName(field: String): String {
        val raw = field.trim()
        val surname = raw.substringBefore('/').trim()
        val given = raw.substringAfter('/', "").trim()
            .removeSuffix(" MR").removeSuffix(" MRS").removeSuffix(" MS").removeSuffix(" MISS")
            .removeSuffix("MR").removeSuffix("MRS").removeSuffix("MS")
            .trim()
        return listOf(given, surname).filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
    }

    private fun cabinOf(code: Char): String? = when (code.uppercaseChar()) {
        'F', 'A', 'P' -> "First"
        'J', 'C', 'D', 'I', 'Z', 'R' -> "Business"
        'W' -> "Premium economy"
        ' ' -> null
        else -> "Economy"
    }
}
