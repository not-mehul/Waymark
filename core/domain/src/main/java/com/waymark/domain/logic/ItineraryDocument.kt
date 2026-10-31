package com.waymark.domain.logic

import com.waymark.domain.model.Money
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.WeatherBasis
import com.waymark.domain.model.WeatherDay
import com.waymark.domain.model.chronological
import java.time.LocalDate

/**
 * The trip as a document to print or keep as a PDF: a sequence of blocks
 * that a renderer lays out on pages. What goes in it is decided here, as a
 * pure function, so it is tested on the JVM; how it looks is the renderer's.
 *
 * It is the itinerary a person carries on paper: every booking under its
 * day, with the times in the place's own clock, who is on it, and the
 * reference to quote at a desk — then what is owed, if anything is.
 */
object ItineraryDocument {

    sealed interface Block {
        data class Title(val name: String, val summary: String, val dates: String, val party: String?) : Block

        data class Section(val text: String) : Block

        /** A day, with its weather when known: "Thursday 14 May" · "21° / 14° · Partly cloudy". */
        data class Day(val text: String, val weather: String?) : Block

        data class Entry(
            /** "16:20 PDT". */
            val time: String,
            val title: String,
            val details: List<String>,
            /** Who is on it, when it is not everyone. */
            val people: String?,
            val reference: String?,
            val note: String?,
        ) : Block

        /** A label and a value on one line: a reference, a balance. */
        data class Row(val label: String, val value: String) : Block

        data class Note(val text: String) : Block
    }

    data class Payload(
        val dossier: TripDossier,
        val costs: CostSplitter.Summary? = null,
        /** The weather for each day, where it is known. */
        val weather: Map<LocalDate, WeatherDay> = emptyMap(),
    )

    fun fileName(dossier: TripDossier): String =
        MarkdownExport.fileName(MarkdownExport.Payload(dossier)).removeSuffix(".md") + ".pdf"

    fun build(payload: Payload): List<Block> = buildList {
        val dossier = payload.dossier
        val trip = dossier.trip
        val party = dossier.party.travelers
        add(
            Block.Title(
                name = trip.name,
                summary = trip.destinationSummary,
                dates = TimeText.dateRange(trip.startDate(), trip.endDate()),
                party = party.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.displayName },
            )
        )

        val segments = dossier.segments.chronological()
        if (segments.isEmpty()) {
            add(Block.Note("Nothing is booked yet."))
        } else {
            add(Block.Section("Itinerary"))
            var day: LocalDate? = null
            segments.forEach { segment ->
                val date = segment.start.toLocalDate()
                if (date != day) {
                    day = date
                    add(Block.Day(TimeText.day(date), payload.weather[date]?.let(::weatherLine)))
                }
                add(entry(segment, payload))
            }
        }

        val costs = payload.costs
        if (costs != null && (costs.total > 0 || costs.unconverted.isNotEmpty())) {
            costs(costs, payload)
        }
    }

    private fun entry(segment: Segment, payload: Payload): Block.Entry {
        val details = when (segment) {
            is Segment.Flight -> listOfNotNull(
                "${segment.origin.shortLabel} → ${segment.destination.shortLabel}",
                "lands ${TimeText.clock(segment.end)} ${TimeText.zoneLabel(segment.end)}" +
                    (TimeText.dayOffsetSuffix(segment.start, segment.end)?.let { " ($it)" } ?: ""),
                segment.departureTerminal?.let { "terminal $it" },
                segment.departureGate?.let { "gate $it" },
                segment.cabin,
                segment.seats.takeIf { it.isNotEmpty() }?.let { seats(it, payload) },
            )

            is Segment.Lodging -> listOfNotNull(
                "${segment.nights} night${if (segment.nights == 1L) "" else "s"}, " +
                    "until ${TimeText.dayCompact(segment.end.toLocalDate())}",
                segment.roomDescription,
                segment.origin.address ?: segment.origin.city.takeIf { it.isNotBlank() },
                segment.checkInNote,
            )

            is Segment.Ground -> listOfNotNull(
                "${segment.origin.shortLabel} → ${segment.destination.shortLabel}",
                TimeText.durationBetween(segment.startEpochMillis, segment.endEpochMillis),
                segment.provider,
            )

            is Segment.Experience -> listOfNotNull(
                segment.category,
                segment.origin.address ?: segment.origin.city.takeIf { it.isNotBlank() },
            )
        }
        val party = payload.dossier.party.travelers
        val people = segment.travelerIds
            .takeIf { it.isNotEmpty() && it.size < party.size }
            ?.let { ids -> party.filter { it.id in ids }.joinToString(", ") { it.displayName } }
            ?.ifBlank { null }
        val reference = segment.confirmationCode?.takeIf { it.isNotBlank() }?.let { code ->
            segment.bookedWith?.takeIf { it.isNotBlank() }?.let { "$code · $it" } ?: code
        }
        return Block.Entry(
            time = "${TimeText.clock(segment.start)} ${TimeText.zoneLabel(segment.start)}",
            title = segment.title,
            details = details,
            people = people,
            reference = reference,
            note = segment.note?.takeIf { it.isNotBlank() },
        )
    }

    private fun seats(seats: Map<String, String>, payload: Payload): String {
        val names = payload.dossier.party.travelers.associate { it.id to it.displayName }
        return "seats " + seats.entries.joinToString(", ") { (id, seat) -> "${names[id] ?: "?"} $seat" }
    }

    private fun MutableList<Block>.costs(summary: CostSplitter.Summary, payload: Payload) {
        val names = payload.dossier.party.travelers.associate { it.id to it.displayName }
        fun name(id: String) = names[id] ?: "Somebody no longer on the trip"
        fun money(minor: Long) = Money(minor, summary.currency).format()

        add(Block.Section("Costs"))
        add(Block.Row("Spent together", money(summary.total)))
        summary.byCategory.forEach { (category, minor) -> add(Block.Row(category.label, money(minor))) }
        if (summary.transfers.isEmpty()) {
            add(Block.Note("Everyone has paid their share."))
        } else {
            summary.transfers.forEach { add(Block.Row("${name(it.from)} pays ${name(it.to)}", money(it.amount))) }
        }
        if (summary.unconverted.isNotEmpty()) {
            add(
                Block.Note(
                    "Not counted, for want of a rate: " +
                        summary.unconverted.joinToString(", ") { "${it.description} (${it.amount.format()})" } + "."
                )
            )
        }
    }

    private fun weatherLine(day: WeatherDay): String {
        val temps = "${WeatherText.temperature(day.highC)} / ${WeatherText.temperature(day.lowC)}"
        val basis = if (day.basis == WeatherBasis.LAST_YEAR) " · last year" else ""
        return "$temps · ${WeatherText.describe(day.code)}$basis"
    }
}
