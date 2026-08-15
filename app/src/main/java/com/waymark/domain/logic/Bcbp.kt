package com.waymark.domain.logic

import com.waymark.domain.model.BoardingPass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * IATA Resolution 792 "BCBP" — the string encoded in the barcode on every
 * boarding pass. Waymark stores the issued string verbatim so a pass keeps
 * working with no network, and can also compose one for passes typed in by
 * hand.
 *
 * Only the 60-character mandatory section of an M1 (single-leg) pass is
 * handled here; conditional items are preserved as an opaque tail.
 */
object Bcbp {

    private const val MANDATORY_LENGTH = 60

    data class Parsed(
        val passengerName: String,
        val recordLocator: String,
        val origin: String,
        val destination: String,
        val carrier: String,
        val flightNumber: String,
        val julianDate: Int,
        val cabin: String,
        val seat: String,
        val sequence: String,
        val passengerStatus: String,
        val tail: String,
    ) {
        /** BCBP carries only a day-of-year, so the year has to come from context. */
        fun flightDate(year: Int): LocalDate? =
            runCatching { LocalDate.ofYearDay(year, julianDate) }.getOrNull()
    }

    fun build(
        passengerName: String,
        recordLocator: String,
        origin: String,
        destination: String,
        carrier: String,
        flightNumber: String,
        date: LocalDate,
        cabin: String = "Y",
        seat: String? = null,
        sequence: String? = null,
        passengerStatus: String = "0",
    ): String = buildString {
        append("M1")
        append(nameField(passengerName))
        append('E')
        append(pad(recordLocator.uppercase(), 7))
        append(pad(origin.uppercase(), 3))
        append(pad(destination.uppercase(), 3))
        append(pad(carrier.uppercase(), 3))
        append(pad(flightNumber.filter { it.isDigit() }.padStart(4, '0'), 5))
        append(date.dayOfYear.toString().padStart(3, '0'))
        append(cabin.take(1).uppercase().ifBlank { "Y" })
        append(pad(seat?.uppercase()?.padStart(4, '0') ?: "    ", 4))
        append(pad(sequence?.padStart(4, '0')?.plus(" ") ?: "     ", 5))
        append(passengerStatus.take(1))
        append("00")
    }

    fun forPass(pass: BoardingPass, date: LocalDate, recordLocator: String): String = build(
        passengerName = pass.passengerName,
        recordLocator = recordLocator,
        origin = pass.origin,
        destination = pass.destination,
        carrier = pass.designator.takeWhile { !it.isDigit() },
        flightNumber = pass.designator.dropWhile { !it.isDigit() },
        date = date,
        cabin = pass.cabin?.take(1) ?: "Y",
        seat = pass.seat,
        sequence = pass.sequenceNumber,
    )

    fun parse(payload: String): Parsed? {
        val raw = payload.trimEnd('\n', '\r')
        if (raw.length < MANDATORY_LENGTH) return null
        if (raw.firstOrNull() != 'M') return null
        return runCatching {
            Parsed(
                passengerName = raw.substring(2, 22).trim(),
                recordLocator = raw.substring(23, 30).trim(),
                origin = raw.substring(30, 33).trim(),
                destination = raw.substring(33, 36).trim(),
                carrier = raw.substring(36, 39).trim(),
                flightNumber = raw.substring(39, 44).trim().trimStart('0'),
                julianDate = raw.substring(44, 47).trim().toIntOrNull() ?: 0,
                cabin = raw.substring(47, 48),
                seat = raw.substring(48, 52).trim().trimStart('0'),
                sequence = raw.substring(52, 57).trim().trimStart('0'),
                passengerStatus = raw.substring(57, 58),
                tail = if (raw.length > MANDATORY_LENGTH) raw.substring(MANDATORY_LENGTH) else "",
            )
        }.getOrNull()
    }

    /** "SMITH/JANE MS" padded to the 20-character field. */
    private fun nameField(fullName: String): String {
        val cleaned = fullName.trim().uppercase()
        val formatted = if (cleaned.contains('/')) {
            cleaned
        } else {
            val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            when {
                parts.isEmpty() -> ""
                parts.size == 1 -> parts[0]
                else -> "${parts.last()}/${parts.dropLast(1).joinToString("")}"
            }
        }
        return pad(formatted, 20)
    }

    private fun pad(value: String, length: Int): String =
        value.take(length).padEnd(length, ' ')

    /** Boarding usually opens 40 minutes out; used when an airline omits it. */
    fun defaultBoardingTime(departureMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(departureMillis).minusSeconds(40 * 60).atZone(zone).toInstant()
            .toEpochMilli()
}
