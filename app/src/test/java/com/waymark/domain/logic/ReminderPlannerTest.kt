package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPlannerTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val segments = bundle.segments
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun firstOf(kind: SegmentKind): Segment =
        segments.first { it.kind == kind }

    @Test
    fun `defaults cover the things a traveler would miss`() {
        val defaults = ReminderPreferences.DEFAULT
        assertTrue(defaults[ReminderCategory.FLIGHTS].isOn)
        assertTrue(defaults[ReminderCategory.GROUND].isOn)
        assertTrue(defaults[ReminderCategory.BOOKINGS].isOn)
        // A hotel check-in is not something anyone needs waking for.
        assertTrue(!defaults[ReminderCategory.STAYS].isOn)
        assertTrue(defaults.anyOn)
    }

    /** The gap this fills: everything except flights used to pass unremarked. */
    @Test
    fun `every category can raise a reminder, not only flights`() {
        val everythingOn = ReminderCategory.entries.fold(ReminderPreferences.DEFAULT) { acc, it ->
            acc.with(it, Lead(2 * 60))
        }
        val raised = SegmentKind.entries.map { kind ->
            val segment = firstOf(kind)
            val now = segment.startEpochMillis - hour
            ReminderPlanner.due(segments, everythingOn, now).map { it.segmentId }
        }
        raised.forEachIndexed { index, ids ->
            assertTrue("${SegmentKind.entries[index]} raised nothing", ids.isNotEmpty())
        }
    }

    @Test
    fun `a category switched off raises nothing`() {
        val flight = firstOf(SegmentKind.FLIGHT)
        val off = ReminderPreferences.DEFAULT.with(ReminderCategory.FLIGHTS, Lead.OFF)
        val due = ReminderPlanner.due(segments, off, flight.startEpochMillis - hour)
        assertTrue(due.none { it.segmentId == flight.id })
    }

    @Test
    fun `nothing is raised before the lead time or after the start`() {
        val flight = firstOf(SegmentKind.FLIGHT)
        val twoHours = ReminderPreferences.DEFAULT.with(ReminderCategory.FLIGHTS, Lead(2 * 60))

        fun raisedAt(now: Long) =
            ReminderPlanner.due(segments, twoHours, now).any { it.segmentId == flight.id }

        assertTrue("too early", !raisedAt(flight.startEpochMillis - 3 * hour))
        assertTrue("inside the window", raisedAt(flight.startEpochMillis - 90 * minute))
        assertTrue("at the boundary", raisedAt(flight.startEpochMillis - 2 * hour))
        assertTrue("already gone", !raisedAt(flight.startEpochMillis + minute))
    }

    /**
     * The signature deliberately omits the countdown, so a check at two hours
     * out does not re-announce what a check at three hours out already said.
     */
    @Test
    fun `the signature is stable as the clock closes in, and changes when the booking moves`() {
        val flight = firstOf(SegmentKind.FLIGHT)
        val preferences = ReminderPreferences.DEFAULT.with(ReminderCategory.FLIGHTS, Lead(3 * 60))

        fun signatureAt(now: Long) = ReminderPlanner
            .due(segments, preferences, now)
            .first { it.segmentId == flight.id }
            .signature

        val early = signatureAt(flight.startEpochMillis - 3 * hour)
        val late = signatureAt(flight.startEpochMillis - 20 * minute)
        assertEquals(early, late)

        val moved = segments.map {
            if (it.id != flight.id) it else (it as Segment.Flight).copy(
                startEpochMillis = it.startEpochMillis + hour,
                endEpochMillis = it.endEpochMillis + hour,
            )
        }
        val afterMove = ReminderPlanner
            .due(moved, preferences, flight.startEpochMillis - 20 * minute)
            .first { it.segmentId == flight.id }
            .signature
        assertTrue("a rescheduled booking must announce itself again", afterMove != early)
    }

    @Test
    fun `the headline names the thing and how long is left`() {
        val flight = firstOf(SegmentKind.FLIGHT) as Segment.Flight
        val preferences = ReminderPreferences.DEFAULT.with(ReminderCategory.FLIGHTS, Lead(3 * 60))
        val reminder = ReminderPlanner
            .due(segments, preferences, flight.startEpochMillis - 2 * hour)
            .first { it.segmentId == flight.id }

        assertTrue(reminder.headline.contains(flight.designator))
        assertTrue(reminder.headline.contains("2h"))
        assertEquals(flight.designator, reminder.label)
        assertTrue(reminder.detail.contains(flight.origin.shortLabel))
    }

    @Test
    fun `the look-ahead is the longest lead in use, and zero when all are off`() {
        val preferences = ReminderPreferences.DEFAULT
            .with(ReminderCategory.FLIGHTS, Lead(6 * 60))
            .with(ReminderCategory.GROUND, Lead(30))
        assertEquals(6 * hour, ReminderPlanner.lookAheadMillis(preferences))

        val silent = ReminderCategory.entries.fold(ReminderPreferences.DEFAULT) { acc, it ->
            acc.with(it, Lead.OFF)
        }
        assertEquals(0L, ReminderPlanner.lookAheadMillis(silent))
        assertTrue(!silent.anyOn)
        assertTrue(ReminderPlanner.due(segments, silent, segments.first().startEpochMillis).isEmpty())
    }

    @Test
    fun `the summary reads as a sentence rather than a dump`() {
        val onlyFlights = ReminderCategory.entries.fold(ReminderPreferences.DEFAULT) { acc, it ->
            if (it == ReminderCategory.FLIGHTS) acc else acc.with(it, Lead.OFF)
        }
        assertEquals("Flights 3h", onlyFlights.summary())

        val silent = ReminderCategory.entries.fold(ReminderPreferences.DEFAULT) { acc, it ->
            acc.with(it, Lead.OFF)
        }
        assertEquals("Off for everything", silent.summary())
    }

    @Test
    fun `every offered choice is at least as coarse as the check that raises it`() {
        Lead.CHOICES.filter { it.isOn }.forEach {
            assertTrue("${it.minutes}m is finer than the worker's period", it.minutes >= 15)
        }
        assertEquals(Lead.OFF, Lead.CHOICES.first())
        assertEquals(Lead.CHOICES.sortedBy { it.minutes }, Lead.CHOICES)
    }
}
