package com.waymark.domain.logic

import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TravelDocument
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological
import java.time.LocalDate

/**
 * The whole trip as one markdown document.
 *
 * The point is to be readable by a person and by anything that reads markdown
 * — a note, an email, a wiki, a printout carried in a pocket. So it is
 * hierarchy and prose, not a table dump: days as headings, each booking as a
 * line that says the thing a traveler needs at that moment.
 *
 * Booking references travel with it — a confirmation code is the reason to
 * send somebody an itinerary in the first place, and there is no vault left to
 * keep it behind. **Document numbers do not.** A passport number is a
 * different class of thing from a hotel reference, and an itinerary pasted
 * into a group chat should not carry one; the export names the document and
 * states its expiry, which is the part anyone else needs to know.
 */
object MarkdownExport {

    /** Everything the exporter needs, so it can stay a pure function. */
    data class Payload(
        val dossier: TripDossier,
        val ideas: List<Idea> = emptyList(),
        val documents: List<TravelDocument> = emptyList(),
        val analytics: TripAnalyticsReport? = null,
    )

    /** A filename that sorts by date and survives every filesystem. */
    fun fileName(payload: Payload): String {
        val slug = payload.dossier.trip.name
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .ifEmpty { "trip" }
        return "${payload.dossier.trip.startDate()}-$slug.md"
    }

    fun render(payload: Payload): String = buildString {
        val trip = payload.dossier.trip
        val party = payload.dossier.party

        appendLine("# ${trip.name}")
        appendLine()
        appendLine("*${trip.destinationSummary}*")
        appendLine()
        appendLine("**${TimeText.dateRange(trip.startDate(), trip.endDate())}**")
        if (party.travelers.isNotEmpty()) {
            appendLine()
            appendLine(party.travelers.joinToString(" · ") { it.displayName })
        }

        itinerary(payload)
        ideas(payload)
        documents(payload)
        references(payload)
        numbers(payload)

        appendLine()
        appendLine("---")
        appendLine()
        appendLine("Exported from Waymark.")
    }

    // — Sections ——————————————————————————————————————————————————————————

    private fun StringBuilder.itinerary(payload: Payload) {
        val segments = payload.dossier.segments.chronological()
        if (segments.isEmpty()) return

        appendLine()
        appendLine("## Itinerary")

        var day: LocalDate? = null
        segments.forEach { segment ->
            val segmentDay = segment.start.toLocalDate()
            if (segmentDay != day) {
                day = segmentDay
                appendLine()
                appendLine("### ${TimeText.day(segmentDay)}")
                appendLine()
            }
            appendLine(line(segment, payload))
        }
    }

    /** One booking, as one line: when, what, and who — in that order. */
    private fun line(segment: Segment, payload: Payload): String {
        val clock = TimeText.clock(segment.start)
        val who = travelerNames(segment.travelerIds, payload)
        val detail = when (segment) {
            is Segment.Flight -> buildList {
                add("**${segment.designator}** ${segment.origin.shortLabel} → ${segment.destination.shortLabel}")
                add(
                    "lands ${TimeText.clock(segment.end)}" +
                        (TimeText.dayOffsetSuffix(segment.start, segment.end)?.let { " ($it)" } ?: "")
                )
                segment.departureTerminal?.let { add("terminal $it") }
                segment.aircraft?.let { add(it) }
                segment.cabin?.let { add(it) }
            }.joinToString(", ")

            is Segment.Lodging -> buildList {
                add("**${segment.propertyName}**")
                add("${segment.nights} night${if (segment.nights == 1L) "" else "s"}")
                segment.roomDescription?.let { add(it) }
                segment.origin.city.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(", ")

            is Segment.Ground -> buildList {
                add("**${modeLabel(segment.mode)}** ${segment.origin.shortLabel} → ${segment.destination.shortLabel}")
                add(TimeText.durationBetween(segment.startEpochMillis, segment.endEpochMillis))
                segment.provider?.let { add(it) }
            }.joinToString(", ")

            is Segment.Experience -> buildList {
                add("**${segment.name}**")
                add(segment.category)
                segment.origin.city.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(", ")
        }

        val suffix = if (who.isNullOrBlank()) "" else " — $who"
        val note = segment.note?.let { "  \n  $it" }.orEmpty()
        return "- `$clock` $detail$suffix$note"
    }

    private fun StringBuilder.ideas(payload: Payload) {
        val live = payload.ideas.filter { it.status != IdeaStatus.DISMISSED }
        if (live.isEmpty()) return

        appendLine()
        appendLine("## On the list")

        live.groupBy { it.city.ifBlank { "Anywhere" } }
            .toSortedMap()
            .forEach { (city, cityIdeas) ->
                appendLine()
                appendLine("### $city")
                appendLine()
                IdeaKind.entries.forEach { kind ->
                    val ofKind = cityIdeas.filter { it.kind == kind }
                    if (ofKind.isEmpty()) return@forEach
                    appendLine("**${kind.heading}**")
                    appendLine()
                    ofKind.sortedBy { it.title }.forEach { appendLine(ideaLine(it)) }
                    appendLine()
                }
            }
    }

    private fun ideaLine(idea: Idea): String = buildString {
        // Ticked when it is settled: either it has been done, or it is on the
        // timeline above and will be. The list doubles as a record of what
        // actually happened, which is most of what anyone re-reads it for.
        val settled = idea.status == IdeaStatus.DONE || idea.scheduledSegmentId != null
        append(if (settled) "- [x] " else "- [ ] ")
        append(idea.title)
        val notes = buildList {
            idea.plannedDate?.let { add(TimeText.dayCompact(it)) }
            idea.typicalMinutes?.let { add(TimeText.duration(it)) }
            idea.priceBand?.let { add(it.label) }
            idea.bestTime?.let { add(it) }
        }
        if (notes.isNotEmpty()) append(" — ${notes.joinToString(", ")}")
        idea.note?.let { append("  \n  $it") }
    }

    private fun StringBuilder.documents(payload: Payload) {
        if (payload.documents.isEmpty()) return

        appendLine()
        appendLine("## Documents")
        appendLine()
        appendLine("Document numbers are deliberately left out.")
        appendLine()

        payload.documents
            .sortedWith(compareBy({ it.travelerId }, { it.kind.ordinal }))
            .forEach { document ->
                val owner = payload.dossier.party.byId(document.travelerId)?.displayName
                append("- **${document.kind.label}**")
                owner?.let { append(" — $it") }
                document.issuer?.let { append(", issued by $it") }
                document.expiresOn?.let { append(", expires ${TimeText.dayCompact(it)}") }
                appendLine()
            }
    }

    /** Every booking that carries a reference, with the reference. */
    private fun StringBuilder.references(payload: Payload) {
        val booked = payload.dossier.segments
            .filter { !it.confirmationCode.isNullOrBlank() }
            .chronological()
        if (booked.isEmpty()) return

        appendLine()
        appendLine("## Bookings")
        appendLine()

        booked.forEach { segment ->
            append("- **${segment.title}**")
            segment.bookedWith?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            append(" — `${segment.confirmationCode}`")
            appendLine()
        }
    }

    private fun StringBuilder.numbers(payload: Payload) {
        val report = payload.analytics ?: return

        appendLine()
        appendLine("## The numbers")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| Distance | ${Geo.formatDistance(report.totalDistanceKm)} |")
        appendLine("| Flown | ${Geo.formatDistance(report.flownKm)} |")
        appendLine("| Hours moving | ${"%.1f".format(report.hoursMoving)} |")
        appendLine("| Time zones crossed | ${report.timeZonesCrossed} |")
        appendLine("| Countries | ${report.countriesVisited.joinToString(", ")} |")
        appendLine("| Estimated carbon | ${report.carbonKg.toInt()} kg CO₂e |")
        report.longestLegLabel?.let {
            appendLine("| Longest leg | $it, ${Geo.formatDistance(report.longestLegKm)} |")
        }
    }

    // — Shared helpers ————————————————————————————————————————————————————

    /**
     * Null when the whole party is on the segment: naming everyone on every
     * line is noise, and the party is listed once at the top.
     */
    private fun travelerNames(ids: Set<String>, payload: Payload): String? {
        val party = payload.dossier.party.travelers
        if (ids.isEmpty() || ids.size == party.size) return null
        return party.filter { it.id in ids }.joinToString(", ") { it.displayName }
            .ifBlank { null }
    }

    private fun modeLabel(mode: GroundMode): String = mode.label
}
