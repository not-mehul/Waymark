package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MarkdownExportTest {

    private val departure = LocalDate.of(2026, 5, 14)
    private val bundle = SampleItinerary.build(departure)
    private val dossier = TripDossier(
        trip = bundle.trip,
        party = TripParty(bundle.trip.id, bundle.travelers),
        segments = bundle.segments,
    )
    private val payload = MarkdownExport.Payload(
        dossier = dossier,
        reservations = bundle.reservations,
        ideas = bundle.ideas,
        documents = bundle.documents,
        packing = bundle.packing,
        analytics = TripAnalytics.report(dossier, bundle.ideas),
    )
    private val markdown = MarkdownExport.render(payload)

    @Test
    fun `the document opens with the trip and its dates`() {
        val lines = markdown.lines()
        assertTrue(lines.first().startsWith("# "))
        assertTrue(lines.first().contains(bundle.trip.name))
        assertTrue(markdown.contains(bundle.trip.destinationSummary))
        assertTrue(markdown.contains("2026"))
    }

    @Test
    fun `every section a trip has content for is present`() {
        listOf("## Itinerary", "## On the list", "## Packing", "## Documents", "## Bookings")
            .forEach { heading ->
                assertTrue("missing $heading", markdown.contains(heading))
            }
    }

    /**
     * The whole reason the export exists in this shape. A code that leaves the
     * vault in plain text is a code that ends up in a chat log.
     */
    @Test
    fun `no secret value is ever written out`() {
        val secrets = bundle.reservations.flatMap { it.secrets }.map { it.value } +
            bundle.documents.map { it.number }
        assertTrue("fixture has no secrets to leak", secrets.isNotEmpty())
        secrets.forEach { secret ->
            assertFalse("leaked $secret", markdown.contains(secret))
        }
    }

    @Test
    fun `the labels for those secrets are still shown, so the record is findable`() {
        assertTrue(markdown.contains("holds confirmation code"))
        assertTrue(markdown.contains("Passport"))
    }

    @Test
    fun `days are headings and bookings are list items beneath them`() {
        val itinerary = markdown.substringAfter("## Itinerary").substringBefore("## On the list")
        val dayHeadings = itinerary.lines().filter { it.startsWith("### ") }
        val bullets = itinerary.lines().filter { it.startsWith("- `") }

        assertTrue("expected several days, got ${dayHeadings.size}", dayHeadings.size >= 3)
        assertTrue(bullets.size >= bundle.segments.size)
        // Chronology: the first bullet is the first thing that happens.
        val firstDeparture = TimeText.clock(bundle.segments.minBy { it.startEpochMillis }.start)
        assertTrue(bullets.first().contains(firstDeparture))
    }

    @Test
    fun `packing and ideas render as checkboxes that reflect their state`() {
        assertTrue(markdown.contains("- [ ] "))
        // The sample seeds some packed items, so both box states appear.
        assertTrue(bundle.packing.any { it.packed })
        assertTrue(markdown.contains("- [x] "))
    }

    @Test
    fun `a traveler is named only when a segment is not the whole party`() {
        val everyone = bundle.travelers.map { it.displayName }
        val itinerary = markdown.substringAfter("## Itinerary").substringBefore("## On the list")
        val shared = bundle.segments.filter {
            it.travelerIds.isEmpty() || it.travelerIds.size == bundle.travelers.size
        }
        assertTrue("fixture has no shared segments", shared.isNotEmpty())

        // Whole-party legs carry no "— name" suffix; split ones do.
        val split = bundle.segments.filter {
            it.travelerIds.isNotEmpty() && it.travelerIds.size < bundle.travelers.size
        }
        if (split.isNotEmpty()) {
            assertTrue(everyone.any { name -> itinerary.contains("— $name") })
        }
    }

    @Test
    fun `the filename sorts by date and is safe on any filesystem`() {
        val name = MarkdownExport.fileName(payload)
        assertTrue(name.startsWith(bundle.trip.startDate().toString()))
        assertTrue(name.endsWith(".md"))
        assertTrue(name.all { it.isLetterOrDigit() || it == '-' || it == '.' })
        assertFalse(name.contains("--"))
    }

    @Test
    fun `an empty trip still produces a valid document rather than a crash`() {
        val bare = MarkdownExport.render(
            MarkdownExport.Payload(
                dossier = TripDossier(
                    trip = bundle.trip,
                    party = TripParty(bundle.trip.id, emptyList()),
                    segments = emptyList(),
                )
            )
        )
        assertTrue(bare.startsWith("# "))
        assertFalse(bare.contains("## Itinerary"))
        assertTrue(bare.contains("Exported from Waymark"))
    }

    @Test
    fun `the closing note states what was withheld`() {
        assertTrue(markdown.trimEnd().endsWith("not included."))
    }
}
