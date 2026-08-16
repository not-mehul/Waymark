package com.waymark.domain.logic

import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FlightUpdateTest {

    private val departure = ZonedDateTime.of(2026, 5, 14, 16, 20, 0, 0, ZoneId.of("America/Los_Angeles"))
    private val arrival = ZonedDateTime.of(2026, 5, 15, 10, 55, 0, 0, ZoneId.of("Europe/London"))
    private val now = departure.minusHours(3).toInstant().toEpochMilli()

    private val flight = Segment.Flight(
        id = "seg-1",
        tripId = "trip-1",
        carrierCode = "BA",
        flightNumber = "286",
        origin = Place("San Francisco International", "SFO", "San Francisco", "United States", 37.6213, -122.3790, "America/Los_Angeles"),
        destination = Place("Heathrow", "LHR", "London", "United Kingdom", 51.4700, -0.4543, "Europe/London"),
        startEpochMillis = departure.toInstant().toEpochMilli(),
        endEpochMillis = arrival.toInstant().toEpochMilli(),
        startZoneId = "America/Los_Angeles",
        endZoneId = "Europe/London",
        departureTerminal = "I",
    )

    @Test
    fun `an empty update is a no-op the repository can skip`() {
        assertTrue(FlightUpdate().isEmpty)
        assertTrue(FlightUpdate(departureGate = "   ").isEmpty)
        assertTrue(!FlightUpdate(departureDelayMinutes = 0).isEmpty)
        assertTrue(!FlightUpdate(state = FlightState.BOARDING).isEmpty)
    }

    @Test
    fun `a delay moves both ends unless arrival is reported separately`() {
        val carried = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 40), now)
        assertEquals(40, carried.departureDelayMinutes)
        assertEquals(40, carried.arrivalDelayMinutes)

        val recovered = FlightUpdate.apply(
            flight,
            null,
            FlightUpdate(departureDelayMinutes = 40, arrivalDelayMinutes = 25),
            now,
        )
        assertEquals(40, recovered.departureDelayMinutes)
        assertEquals(25, recovered.arrivalDelayMinutes)
    }

    @Test
    fun `a field left null keeps what was already known`() {
        val first = FlightUpdate.apply(
            flight,
            null,
            FlightUpdate(departureGate = "A12", departureDelayMinutes = 30),
            now,
        )
        val second = FlightUpdate.apply(flight, first, FlightUpdate(baggageBelt = "7"), now)

        assertEquals("A12", second.departureGate)
        assertEquals(30, second.departureDelayMinutes)
        assertEquals("7", second.baggageBelt)
        // And the terminal booked on the segment survives when nobody overrode it.
        assertEquals("I", second.departureTerminal)
    }

    @Test
    fun `state is inferred from the delay only when nobody stated one`() {
        val inferred = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 45), now)
        assertEquals(FlightState.DELAYED, inferred.state)

        val small = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 5), now)
        assertEquals(FlightState.SCHEDULED, small.state)

        val stated = FlightUpdate.apply(
            flight,
            null,
            FlightUpdate(state = FlightState.BOARDING, departureDelayMinutes = 45),
            now,
        )
        assertEquals(FlightState.BOARDING, stated.state)
    }

    @Test
    fun `every hand-entered status says so`() {
        val status = FlightUpdate.apply(flight, null, FlightUpdate(departureGate = "B4"), now)
        assertEquals(FlightUpdate.SOURCE, status.source)
        assertEquals(now, status.observedAtMillis)
    }

    @Test
    fun `progress tracks the clock between the revised times`() {
        val midFlight = departure.plusHours(5).toInstant().toEpochMilli()
        val airborne = FlightUpdate.apply(
            flight,
            null,
            FlightUpdate(state = FlightState.EN_ROUTE),
            midFlight,
        )
        assertTrue("was ${airborne.progressPercent}", airborne.progressPercent in 40..60)

        val landed = FlightUpdate.apply(
            flight,
            null,
            FlightUpdate(state = FlightState.LANDED),
            arrival.plusHours(1).toInstant().toEpochMilli(),
        )
        assertEquals(100, landed.progressPercent)
    }

    // — Alerts ————————————————————————————————————————————————————————————

    @Test
    fun `a small delay says nothing`() {
        val status = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 10), now)
        assertNull(FlightUpdate.alertFor(flight, null, status))
    }

    @Test
    fun `a material delay is raised once, and again only when it grows`() {
        val first = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 40), now)
        val alert = requireNotNull(FlightUpdate.alertFor(flight, null, first))
        assertTrue(alert.headline.contains("BA286"))
        assertEquals(DisruptionAlert.Severity.WARNING, alert.severity)

        // Re-entering the same 40 minutes is not news.
        val same = FlightUpdate.apply(flight, first, FlightUpdate(departureDelayMinutes = 40), now)
        assertNull(FlightUpdate.alertFor(flight, first, same))

        // Creeping by five minutes is not news either.
        val creep = FlightUpdate.apply(flight, first, FlightUpdate(departureDelayMinutes = 45), now)
        assertNull(FlightUpdate.alertFor(flight, first, creep))

        val worse = FlightUpdate.apply(flight, first, FlightUpdate(departureDelayMinutes = 100), now)
        val second = requireNotNull(FlightUpdate.alertFor(flight, first, worse))
        assertEquals(DisruptionAlert.Severity.CRITICAL, second.severity)
    }

    @Test
    fun `a cancellation outranks a delay and is raised only on the transition`() {
        val delayed = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 200), now)
        val cancelled = FlightUpdate.apply(
            flight,
            delayed,
            FlightUpdate(state = FlightState.CANCELLED),
            now,
        )
        val alert = requireNotNull(FlightUpdate.alertFor(flight, delayed, cancelled))
        assertEquals(DisruptionAlert.Severity.CRITICAL, alert.severity)
        assertTrue(alert.headline.contains("cancelled"))

        assertNull(FlightUpdate.alertFor(flight, cancelled, cancelled))
    }

    @Test
    fun `the first gate entered is not a gate change`() {
        val first = FlightUpdate.apply(flight, null, FlightUpdate(departureGate = "A12"), now)
        assertNull(FlightUpdate.alertFor(flight, null, first))

        val moved = FlightUpdate.apply(flight, first, FlightUpdate(departureGate = "B7"), now)
        val alert = requireNotNull(FlightUpdate.alertFor(flight, first, moved))
        assertTrue(alert.headline.contains("B7"))
        assertTrue(alert.detail.contains("A12"))
    }

    @Test
    fun `the signature changes only when the news does`() {
        val base = FlightUpdate.apply(flight, null, FlightUpdate(departureDelayMinutes = 40), now)
        val alert = requireNotNull(FlightUpdate.alertFor(flight, null, base))

        // A later report of the same delay produces the same signature, which
        // is what keeps a repeated entry from posting a second notification.
        val again = FlightUpdate.apply(flight, base, FlightUpdate(departureDelayMinutes = 44), now)
        assertEquals(
            FlightUpdate.signatureOf(alert, base),
            FlightUpdate.signatureOf(alert, again),
        )

        val worse = FlightUpdate.apply(flight, base, FlightUpdate(departureDelayMinutes = 95), now)
        assertNotEquals(
            FlightUpdate.signatureOf(alert, base),
            FlightUpdate.signatureOf(alert, worse),
        )
    }
}
