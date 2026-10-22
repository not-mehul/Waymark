package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IcsExportTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(bundle.trip, TripParty(bundle.trip.id, bundle.travelers), bundle.segments)
    private val ics = IcsExport.render(dossier, nowMillis = 0L)

    @Test
    fun `one event per booking, wrapped in a calendar`() {
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertEquals(bundle.segments.size, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertEquals(bundle.segments.size, Regex("END:VEVENT").findAll(ics).count())
    }

    @Test
    fun `times are in UTC and match the booking to the second`() {
        val outbound = bundle.segments.first { it.id == "seg-outbound" }
        // 16:20 PDT on 14 May is 23:20 UTC.
        assertTrue(ics.contains("DTSTART:20260514T232000Z"))
        assertTrue(ics.contains("UID:${outbound.id}@waymark"))
    }

    @Test
    fun `references travel with the events`() {
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(unfolded.contains("Reference: K7QH2P (British Airways)"))
    }

    @Test
    fun `text is escaped and long lines are folded at 75 octets`() {
        assertEquals("a\\;b\\,c\\\\d\\ne", IcsExport.text("a;b,c\\d\ne"))
        val folded = IcsExport.fold("DESCRIPTION:" + "é".repeat(60))
        folded.split("\r\n").forEach { line ->
            assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75)
        }
        assertEquals("DESCRIPTION:" + "é".repeat(60), folded.replace("\r\n ", ""))
        ics.split("\r\n").forEach { assertTrue(it, it.toByteArray(Charsets.UTF_8).size <= 75) }
    }
}
