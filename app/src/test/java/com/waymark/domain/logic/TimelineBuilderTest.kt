package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TimelineBuilderTest {

    private val departure = LocalDate.of(2026, 5, 14)
    private val bundle = SampleItinerary.build(departure)
    private val dossier = TripDossier(
        trip = bundle.trip,
        party = TripParty(bundle.trip.id, bundle.travelers),
        segments = bundle.segments,
    )
    private val duringTrip = Instant.parse("2026-05-16T09:00:00Z")

    @Test
    fun `entries come out in clock order`() {
        val entries = TimelineBuilder.build(dossier, now = duringTrip)
        val keys = entries.map { it.sortKey }
        assertEquals(keys.sorted(), keys)
    }

    @Test
    fun `every segment appears exactly once, and lodging twice`() {
        val entries = TimelineBuilder.build(dossier, now = duringTrip)
            .filterIsInstance<TimelineEntry.Event>()
        bundle.segments.forEach { segment ->
            val appearances = entries.count { it.segment.id == segment.id }
            val expected = if (segment is Segment.Lodging) 2 else 1
            assertEquals("${segment.id} appearances", expected, appearances)
        }
    }

    @Test
    fun `each day gets one break, before its first event`() {
        val entries = TimelineBuilder.build(dossier, now = duringTrip)
        val dayBreaks = entries.filterIsInstance<TimelineEntry.DayBreak>()
        assertEquals(dayBreaks.map { it.date }.distinct().size, dayBreaks.size)

        val firstIndex = entries.indexOfFirst { it is TimelineEntry.Event }
        assertTrue(entries.take(firstIndex).any { it is TimelineEntry.DayBreak })
    }

    @Test
    fun `filtering by traveler drops the other traveler's segments`() {
        val julian = bundle.travelers.first { it.fullName.startsWith("Julian") }
        val entries = TimelineBuilder.build(dossier, travelerFilter = julian.id, now = duringTrip)
            .filterIsInstance<TimelineEntry.Event>()
        assertTrue(entries.none { it.segment.id == "seg-eurostar-mara" })
        assertTrue(entries.any { it.segment.id == "seg-eurostar-julian" })
    }

    @Test
    fun `the now marker lands between past and future`() {
        val entries = TimelineBuilder.build(dossier, now = duringTrip)
        val nowIndex = entries.indexOfFirst { it is TimelineEntry.Now }
        assertTrue(nowIndex >= 0)
        val events = entries.filterIsInstance<TimelineEntry.Event>()
        val before = events.filter { entries.indexOf(it) < nowIndex }
        val after = events.filter { entries.indexOf(it) > nowIndex }
        // Compared on each entry's own instant: a lodging check-out sits at
        // the end of the stay, not at the check-in that started it.
        assertTrue(before.all { it.sortKey <= duringTrip.toEpochMilli() })
        assertTrue(after.all { it.sortKey >= duringTrip.toEpochMilli() })
    }

    @Test
    fun `links describe the gaps between movements`() {
        val links = TimelineBuilder.build(dossier, now = duringTrip)
            .filterIsInstance<TimelineEntry.Link>()
        assertTrue(links.isNotEmpty())
        links.forEach { link ->
            assertTrue(link.fromSegment.id != link.toSegment.id)
            assertTrue(link.toSegment.startEpochMillis >= link.fromSegment.startEpochMillis)
        }
    }

    @Test
    fun `an empty trip produces an empty timeline rather than a header`() {
        val empty = dossier.copy(segments = emptyList())
        assertTrue(TimelineBuilder.build(empty, now = duringTrip).isEmpty())
    }

    @Test
    fun `the next event is the first one not yet finished`() {
        val next = TimelineBuilder.nextEvent(dossier, duringTrip)
        assertTrue(next != null)
        assertTrue(next!!.endEpochMillis >= duringTrip.toEpochMilli())
    }
}
