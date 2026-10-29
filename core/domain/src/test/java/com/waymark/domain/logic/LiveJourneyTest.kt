package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.SegmentKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveJourneyTest {

    private val segments = SampleItinerary.build(LocalDate.of(2026, 5, 14)).segments
    private val flight = segments.filter { it.kind == SegmentKind.FLIGHT }.minBy { it.startEpochMillis }
    private val minute = 60_000L
    private val hour = 60 * minute
    private val on = ReminderPreferences.DEFAULT

    @Test
    fun `nothing shows long before a departure`() {
        assertNull(LiveJourney.of(listOf(flight), on, flight.startEpochMillis - 5 * hour))
    }

    @Test
    fun `a flight shows from three hours out, counting down to departure`() {
        val journey = checkNotNull(LiveJourney.of(listOf(flight), on, flight.startEpochMillis - 40 * minute))
        assertEquals(LiveJourney.Phase.DEPARTING, journey.phase)
        assertEquals(0, journey.progress)
        assertEquals("40m", journey.chip)
        assertEquals("Departs in 40m", journey.headline)
        assertEquals(flight.startEpochMillis, journey.targetMillis)
    }

    @Test
    fun `under way it counts down to landing and fills in`() {
        val halfway = flight.startEpochMillis + flight.duration.toMillis() / 2
        val journey = checkNotNull(LiveJourney.of(listOf(flight), on, halfway))
        assertEquals(LiveJourney.Phase.UNDER_WAY, journey.phase)
        assertEquals(LiveJourney.PROGRESS_MAX / 2, journey.progress)
        assertTrue(journey.headline.startsWith("Lands in "))
        assertEquals(flight.endEpochMillis, journey.targetMillis)
    }

    @Test
    fun `it is gone on landing`() {
        assertNull(LiveJourney.of(listOf(flight), on, flight.endEpochMillis))
    }

    @Test
    fun `turning flight reminders off quiets it`() {
        val off = on.with(ReminderCategory.FLIGHTS, Lead.OFF)
        assertNull(LiveJourney.of(listOf(flight), off, flight.startEpochMillis))
        assertNull(LiveJourney.nextChangeAt(listOf(flight), off, flight.startEpochMillis - 5 * hour))
    }

    @Test
    fun `stays and bookings never show`() {
        val quiet = segments.filter { it.kind == SegmentKind.LODGING || it.kind == SegmentKind.EXPERIENCE }
        quiet.forEach { assertNull(LiveJourney.of(listOf(it), on, it.startEpochMillis)) }
    }

    @Test
    fun `it wakes for the window, then every tick, then for landing`() {
        val before = flight.startEpochMillis - 5 * hour
        assertEquals(flight.startEpochMillis - 3 * hour, LiveJourney.nextChangeAt(listOf(flight), on, before))

        val showing = flight.startEpochMillis - 2 * hour
        assertEquals(showing + LiveJourney.TICK_MILLIS, LiveJourney.nextChangeAt(listOf(flight), on, showing))

        val nearlyThere = flight.endEpochMillis - 5 * minute
        assertEquals(flight.endEpochMillis, LiveJourney.nextChangeAt(listOf(flight), on, nearlyThere))
    }
}
