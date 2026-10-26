package com.waymark.domain.logic

import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The trip as a calendar file (RFC 5545), one event per booking.
 *
 * Times are written in UTC, which every calendar reads correctly and shows in
 * whatever zone its owner is in — the alternative, a VTIMEZONE block per zone,
 * is several hundred lines for no gain in correctness. Each event's UID is the
 * booking's own id, so importing the same trip twice updates the events
 * rather than doubling them.
 */
object IcsExport {

    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun fileName(dossier: TripDossier): String =
        MarkdownExport.fileName(MarkdownExport.Payload(dossier)).removeSuffix(".md") + ".ics"

    fun render(dossier: TripDossier, nowMillis: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Waymark//Itinerary//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${text(dossier.trip.name)}",
        )
        dossier.segments.chronological().forEach { segment ->
            lines += "BEGIN:VEVENT"
            lines += "UID:${segment.id}@waymark"
            lines += "DTSTAMP:${utc(nowMillis)}"
            lines += "DTSTART:${utc(segment.startEpochMillis)}"
            lines += "DTEND:${utc(segment.endEpochMillis)}"
            lines += "SUMMARY:${text(summary(segment))}"
            location(segment)?.let { lines += "LOCATION:${text(it)}" }
            description(segment, dossier).takeIf { it.isNotBlank() }?.let {
                lines += "DESCRIPTION:${text(it)}"
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        // CRLF line endings and 75-octet folding are what the format requires.
        return lines.joinToString("\r\n", postfix = "\r\n") { fold(it) }
    }

    private fun summary(segment: Segment): String = when (segment) {
        is Segment.Flight -> "${segment.designator} ${segment.origin.shortLabel} → ${segment.destination.shortLabel}"
        is Segment.Lodging -> segment.propertyName
        is Segment.Ground -> segment.title
        is Segment.Experience -> segment.name
    }

    private fun location(segment: Segment): String? {
        val place = segment.origin
        return listOfNotNull(place.name.ifBlank { null }, place.address).distinct()
            .joinToString(", ").ifBlank { null }
    }

    private fun description(segment: Segment, dossier: TripDossier): String = buildList {
        segment.confirmationCode?.let { add("Reference: $it" + (segment.bookedWith?.let { by -> " ($by)" } ?: "")) }
        when (segment) {
            is Segment.Flight -> {
                segment.departureTerminal?.let { add("Terminal $it") }
                segment.cabin?.let { add(it) }
                segment.seats.forEach { (travelerId, seat) ->
                    val who = dossier.party.byId(travelerId)?.displayName ?: "Seat"
                    add("$who: $seat")
                }
            }
            is Segment.Lodging -> {
                add("${segment.nights} night" + if (segment.nights == 1L) "" else "s")
                segment.roomDescription?.let { add(it) }
                segment.checkInNote?.let { add(it) }
            }
            is Segment.Ground -> {
                segment.provider?.let { add(it) }
                segment.pickupInstruction?.let { add(it) }
            }
            is Segment.Experience -> segment.curatedBy?.let { add(it) }
        }
        segment.note?.let { add(it) }
    }.joinToString("\n")

    private fun utc(millis: Long): String = stamp.format(Instant.ofEpochMilli(millis))

    /** TEXT values escape backslash, semicolon, comma and newline. */
    internal fun text(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /**
     * Lines longer than 75 octets continue on the next line after a single
     * space. Counted in UTF-8 bytes, and never splitting a character.
     */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var width = 0
        var limit = 75
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val bytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (width + bytes > limit) {
                out.append("\r\n ")
                width = 0
                limit = 74
            }
            out.append(line, index, index + chars)
            width += bytes
            index += chars
        }
        return out.toString()
    }
}
