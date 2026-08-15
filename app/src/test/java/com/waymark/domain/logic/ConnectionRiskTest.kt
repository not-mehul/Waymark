package com.waymark.domain.logic

import com.waymark.data.catalog.Airports
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ConnectionRiskTest {

    private val base = Instant.parse("2026-05-14T08:00:00Z").toEpochMilli()

    private fun flight(
        id: String,
        from: String,
        to: String,
        departMinutes: Long,
        blockMinutes: Long,
        carrier: String = "BA",
        departureTerminal: String? = null,
        arrivalTerminal: String? = null,
    ): Segment.Flight {
        val origin = Airports.place(from)
        val destination = Airports.place(to)
        return Segment.Flight(
            id = id,
            tripId = "trip",
            carrierCode = carrier,
            flightNumber = "100",
            origin = origin,
            destination = destination,
            startEpochMillis = base + departMinutes * 60_000,
            endEpochMillis = base + (departMinutes + blockMinutes) * 60_000,
            startZoneId = origin.timeZoneId,
            endZoneId = destination.timeZoneId,
            departureTerminal = departureTerminal,
            arrivalTerminal = arrivalTerminal,
        )
    }

    @Test
    fun `same terminal domestic connection with two hours is comfortable`() {
        val inbound = flight("a", "SFO", "ORD", 0, 240, arrivalTerminal = "3")
        val onward = flight("b", "ORD", "BOS", 360, 150, departureTerminal = "3")
        val verdict = ConnectionRisk.assess(inbound, onward)
        assertEquals(RiskLevel.COMFORTABLE, verdict.level)
        assertTrue(verdict.slackMinutes > 60)
    }

    @Test
    fun `terminal change on an international arrival needs more time`() {
        val inbound = flight("a", "LHR", "JFK", 0, 480, arrivalTerminal = "7")
        val onward = flight("b", "JFK", "BOS", 540, 80, departureTerminal = "8")
        val (required, reason) = ConnectionRisk.requiredMinutes(inbound, onward)
        assertTrue("required was $required", required >= 120)
        assertTrue(reason.contains("immigration"))
    }

    @Test
    fun `a delay eats the connection`() {
        val inbound = flight("a", "SFO", "LHR", 0, 600, arrivalTerminal = "5")
        val onward = flight("b", "LHR", "FCO", 690, 150, departureTerminal = "5")
        val comfortable = ConnectionRisk.assess(inbound, onward)
        assertTrue(comfortable.level != RiskLevel.BROKEN)

        val delayed = ConnectionRisk.assess(
            inbound = inbound,
            onward = onward,
            inboundStatus = status(inbound.id, inbound.endEpochMillis + 75 * 60_000),
        )
        assertTrue(
            "expected degradation, got ${delayed.level}",
            delayed.level == RiskLevel.AT_RISK || delayed.level == RiskLevel.BROKEN,
        )
    }

    @Test
    fun `an onward flight that leaves before the inbound lands is broken`() {
        val inbound = flight("a", "SFO", "LHR", 0, 600)
        val onward = flight("b", "LHR", "CDG", 500, 80)
        assertEquals(RiskLevel.BROKEN, ConnectionRisk.assess(inbound, onward).level)
    }

    @Test
    fun `changing carrier with checked bags costs extra`() {
        val inbound = flight("a", "SFO", "JFK", 0, 330, carrier = "AA", arrivalTerminal = "8")
        val onward = flight("b", "JFK", "LHR", 420, 420, carrier = "BA", departureTerminal = "8")
        val withBags = ConnectionRisk.requiredMinutes(inbound, onward, checkedBags = true).first
        val withoutBags = ConnectionRisk.requiredMinutes(inbound, onward, checkedBags = false).first
        assertEquals(20, withBags - withoutBags)
    }

    @Test
    fun `an airport change is judged on the ground transfer`() {
        val inbound = flight("a", "SFO", "LHR", 0, 600)
        val onward = flight("b", "LGW", "FCO", 780, 150)
        val (required, reason) = ConnectionRisk.requiredMinutes(inbound, onward)
        assertTrue("required was $required", required > 120)
        assertTrue(reason.lowercase().contains("airport change"))
    }

    @Test
    fun `ground gaps are judged against the estimate plus a buffer`() {
        val hotel = Airports.place("LHR")
        val station = Airports.place("LGW")
        val tight = ConnectionRisk.assessGap(hotel, station, availableMinutes = 20)
        val relaxed = ConnectionRisk.assessGap(hotel, station, availableMinutes = 360)
        assertEquals(RiskLevel.AT_RISK, tight.level)
        assertEquals(RiskLevel.COMFORTABLE, relaxed.level)
    }

    private fun status(segmentId: String, estimatedArrival: Long) = FlightStatus(
        segmentId = segmentId,
        designator = "BA100",
        state = FlightState.DELAYED,
        scheduledDepartureMillis = base,
        estimatedDepartureMillis = base,
        scheduledArrivalMillis = estimatedArrival,
        estimatedArrivalMillis = estimatedArrival,
    )
}
