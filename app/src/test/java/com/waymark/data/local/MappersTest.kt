package com.waymark.data.local

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Place
import com.waymark.domain.model.RaisedReminder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Row ⇄ model, for every kind of row the app writes. */
class MappersTest {

    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))

    @Test
    fun `every booking in the worked example survives a round trip`() {
        sample.segments.forEach { segment ->
            assertEquals(segment, Mappers.toSegment(Mappers.toEntity(segment)))
        }
    }

    @Test
    fun `trips, travelers and ideas survive a round trip`() {
        assertEquals(sample.trip, Mappers.toTrip(Mappers.toEntity(sample.trip)))
        sample.travelers.forEach { assertEquals(it, Mappers.toTraveler(Mappers.toEntity(it))) }
        sample.ideas.forEach { assertEquals(it, Mappers.toIdea(Mappers.toEntity(it))) }
    }

    @Test
    fun `a reminder survives a round trip`() {
        val reminder = RaisedReminder(
            signature = "reminder:seg-1:1",
            segmentId = "seg-1",
            label = "BA286",
            headline = "BA286 in 3h",
            detail = "SFO to LHR",
            startEpochMillis = 10_800_000L,
            raisedAtMillis = 0L,
            posted = true,
            acknowledged = false,
        )
        assertEquals(reminder, Mappers.toReminder(Mappers.toEntity(reminder)))
    }

    @Test
    fun `a row of a kind this build does not know is skipped, not a crash`() {
        val row = Mappers.toEntity(sample.segments.first()).copy(kind = "TELEPORT")
        assertNull(Mappers.toSegment(row))
    }

    @Test
    fun `a place without coordinates stays without them`() {
        val hotel = Place(name = "Hotel Rivoli", city = "Paris", timeZoneId = "Europe/Paris")
        assertEquals(hotel, Codecs.decodePlace(Codecs.encodePlace(hotel)))
    }

    @Test
    fun `the old stand-in for unknown coordinates reads as unknown`() {
        val legacy = listOf("Somewhere", "", "", "", "0.0", "0.0", "UTC").joinToString("\u001F")
        val place = Codecs.decodePlace(legacy)!!
        assertNull(place.latitude)
        assertNull(place.position)
    }

    @Test
    fun `ids, lists and maps survive their codecs`() {
        assertEquals(setOf("a", "b"), Codecs.decodeIds(Codecs.encodeIds(listOf("a", " ", "b"))))
        assertEquals(listOf("BA 123", "AF 9"), Codecs.decodeList(Codecs.encodeList(listOf("BA 123", "AF 9"))))
        val seats = mapOf("t-1" to "21A", "t-2" to "21B")
        assertEquals(seats, Codecs.decodeMap(Codecs.encodeMap(seats)))
        assertEquals(emptyMap<String, String>(), Codecs.decodeMap("garbage"))
    }
}
