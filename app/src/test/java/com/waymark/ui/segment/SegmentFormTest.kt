package com.waymark.ui.segment

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Segment
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentFormTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val party = bundle.travelers
    private val outbound = bundle.segments.filterIsInstance<Segment.Flight>().first { it.id == "seg-outbound" }
    private val stay = bundle.segments.filterIsInstance<Segment.Lodging>().first()

    private fun saved(outcome: SegmentForm.Outcome): Segment =
        (outcome as SegmentForm.Outcome.Saved).segment

    @Test
    fun `an untouched form saves the booking unchanged`() {
        val form = SegmentForm.of(outbound, party)
        val result = saved(form.apply(outbound, party))
        // Everyone ticked is written back as everyone.
        assertEquals(outbound.copy(travelerIds = form.travelerIds), result)
        assertEquals(outbound.startEpochMillis, result.startEpochMillis)
        assertEquals(outbound.endEpochMillis, result.endEpochMillis)
    }

    @Test
    fun `a new flight number and gate are written to the flight`() {
        val form = SegmentForm.of(outbound, party).copy(name = "ba 287", departureGate = "a9")
        val result = saved(form.apply(outbound, party)) as Segment.Flight
        assertEquals("BA", result.carrierCode)
        assertEquals("287", result.flightNumber)
        assertEquals("A9", result.departureGate)
    }

    @Test
    fun `changing an airport moves the departure into that airport's zone`() {
        val form = SegmentForm.of(outbound, party).copy(where = "JFK")
        val result = saved(form.apply(outbound, party)) as Segment.Flight
        assertEquals("JFK", result.origin.code)
        assertEquals("America/New_York", result.startZoneId)
        // The clock time is kept as typed, in the new zone.
        assertEquals(outbound.start.toLocalTime(), result.start.toLocalTime())
    }

    @Test
    fun `an unknown airport or a bad flight number is refused`() {
        val unknown = SegmentForm.of(outbound, party).copy(destination = "QQQ")
        assertEquals(
            SegmentForm.Problem.UnknownAirport("QQQ"),
            (unknown.apply(outbound, party) as SegmentForm.Outcome.Refused).problem,
        )
        val number = SegmentForm.of(outbound, party).copy(name = "hello")
        assertEquals(
            SegmentForm.Problem.NotAFlightNumber,
            (number.apply(outbound, party) as SegmentForm.Outcome.Refused).problem,
        )
    }

    @Test
    fun `an end a little before the start runs past midnight, further is refused`() {
        val form = SegmentForm.of(stay, party)
        val sameDayEarlier = form.copy(endDate = form.startDate, endTime = form.startTime.minusHours(1))
        val result = saved(sameDayEarlier.apply(stay, party))
        assertEquals(form.startDate.plusDays(1), result.end.toLocalDate())

        val daysEarlier = form.copy(endDate = form.startDate.minusDays(3))
        assertEquals(
            SegmentForm.Problem.EndsBeforeStart,
            (daysEarlier.apply(stay, party) as SegmentForm.Outcome.Refused).problem,
        )
    }

    @Test
    fun `nobody on a booking, or a stay with no name, is refused`() {
        val nobody = SegmentForm.of(outbound, party).copy(travelerIds = emptySet())
        assertEquals(
            SegmentForm.Problem.NoTraveler,
            (nobody.apply(outbound, party) as SegmentForm.Outcome.Refused).problem,
        )
        val unnamed = SegmentForm.of(stay, party).copy(name = "  ")
        assertEquals(
            SegmentForm.Problem.NoName,
            (unnamed.apply(stay, party) as SegmentForm.Outcome.Refused).problem,
        )
    }

    @Test
    fun `a stay keeps its place while the words are unchanged`() {
        val form = SegmentForm.of(stay, party).copy(room = "Room 4", address = "1 Example Street")
        val result = saved(form.apply(stay, party)) as Segment.Lodging
        assertEquals(stay.origin.position, result.origin.position)
        assertEquals("Room 4", result.roomDescription)
        assertEquals("1 Example Street", result.origin.address)
    }

    @Test
    fun `seats and tickets are kept only for people on the flight`() {
        val first = party.first().id
        val form = SegmentForm.of(outbound, party).copy(
            travelerIds = setOf(first),
            seats = party.associate { it.id to "1A" },
        )
        val result = saved(form.apply(outbound, party)) as Segment.Flight
        assertEquals(setOf(first), result.seats.keys)
    }

    @Test
    fun `a ground leg can change how it goes`() {
        val ground = bundle.segments.filterIsInstance<Segment.Ground>().firstOrNull() ?: return
        val form = SegmentForm.of(ground, party).copy(mode = GroundMode.BUS, pickup = "")
        val result = saved(form.apply(ground, party)) as Segment.Ground
        assertEquals(GroundMode.BUS, result.mode)
        assertNull(result.pickupInstruction)
        assertTrue(result.endEpochMillis > result.startEpochMillis)
    }
}
