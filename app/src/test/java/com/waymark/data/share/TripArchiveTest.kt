package com.waymark.data.share

import com.waymark.data.catalog.SampleItinerary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripArchiveTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val trip = TripArchive.TripBundle(bundle.trip, bundle.travelers, bundle.segments, bundle.ideas)

    @Test
    fun `a trip survives the round trip through a file exactly`() {
        val text = TripArchive.encode(TripArchive.archiveOf(listOf(trip), nowMillis = 1L))
        val back = TripArchive.bundlesOf(TripArchive.decode(text)).single()
        assertEquals(trip.trip, back.trip)
        assertEquals(trip.travelers, back.travelers)
        assertEquals(trip.segments, back.segments)
        assertEquals(trip.ideas, back.ideas)
    }

    @Test
    fun `the worked example fits in a QR code, bookings intact`() {
        val code = TripArchive.toCode(TripArchive.archiveOf(listOf(trip), nowMillis = 1L))
        assertNotNull("the example is too large for a code", code)
        assertTrue(code!!.payload.length <= TripArchive.MAX_CODE_CHARS)
        val back = TripArchive.bundlesOf(TripArchive.fromCode(code.payload)).single()
        assertEquals(trip.trip, back.trip)
        assertEquals(trip.travelers, back.travelers)
        // Whatever was left out to fit, every booking arrives with its times,
        // its places and its reference.
        back.segments.zip(trip.segments).forEach { (got, sent) ->
            assertEquals(sent.id, got.id)
            assertEquals(sent.startEpochMillis, got.startEpochMillis)
            assertEquals(sent.endEpochMillis, got.endEpochMillis)
            assertEquals(sent.origin, got.origin)
            assertEquals(sent.destination, got.destination)
            assertEquals(sent.confirmationCode, got.confirmationCode)
        }
    }

    @Test
    fun `a small trip travels in a code complete`() {
        val small = trip.copy(segments = trip.segments.take(3), ideas = trip.ideas.take(1))
        val code = requireNotNull(TripArchive.toCode(TripArchive.archiveOf(listOf(small), nowMillis = 1L)))
        assertEquals(TripArchive.CodeDetail.EVERYTHING, code.detail)
        val back = TripArchive.bundlesOf(TripArchive.fromCode(code.payload)).single()
        assertEquals(small.segments, back.segments)
        assertEquals(small.ideas, back.ideas)
    }

    @Test
    fun `something that is not a Waymark file says so`() {
        listOf("", "{}", "{\"format\":\"other\",\"version\":1,\"exportedAt\":0}", "not json").forEach { text ->
            val failure = runCatching { TripArchive.decode(text) }.exceptionOrNull()
            assertTrue(text, failure is TripArchive.InvalidArchive)
        }
        val garbled = runCatching { TripArchive.fromCode("%%%") }.exceptionOrNull()
        assertTrue(garbled is TripArchive.InvalidArchive)
    }

    @Test
    fun `a file from a newer version is refused with a reason`() {
        val newer = "{\"format\":\"waymark\",\"version\":99,\"exportedAt\":0}"
        val failure = runCatching { TripArchive.decode(newer) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("newer"))
    }

    @Test
    fun `fields a later version adds are ignored rather than fatal`() {
        val text = TripArchive.encode(TripArchive.archiveOf(listOf(trip), nowMillis = 1L))
            .replaceFirst("\"format\"", "\"somethingNew\": 1, \"format\"")
        assertEquals(1, TripArchive.decode(text).trips.size)
    }
}
