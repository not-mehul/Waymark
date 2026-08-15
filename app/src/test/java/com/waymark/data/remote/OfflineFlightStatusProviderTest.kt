package com.waymark.data.remote

import com.waymark.data.catalog.Airports
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.Segment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OfflineFlightStatusProviderTest {

    private val departure = Instant.parse("2026-05-14T23:20:00Z").toEpochMilli()
    private val arrival = departure + 9 * 60 * 60 * 1000

    private val flight = Segment.Flight(
        id = "seg-1",
        tripId = "trip",
        carrierCode = "BA",
        flightNumber = "286",
        origin = Airports.place("SFO"),
        destination = Airports.place("LHR"),
        startEpochMillis = departure,
        endEpochMillis = arrival,
        startZoneId = "America/Los_Angeles",
        endZoneId = "Europe/London",
    )

    private fun providerAt(millis: Long) = OfflineFlightStatusProvider { millis }

    @Test
    fun `the same flight on the same day always yields the same schedule`() = runBlocking {
        val first = providerAt(departure - 3 * 3_600_000).fetch(flight)
        val second = providerAt(departure - 3 * 3_600_000).fetch(flight)
        assertEquals(first.estimatedDepartureMillis, second.estimatedDepartureMillis)
        assertEquals(first.state, second.state)
    }

    @Test
    fun `phase follows the clock`() = runBlocking {
        val wellBefore = providerAt(departure - 40 * 3_600_000).fetch(flight)
        val boarding = providerAt(departure - 30 * 60_000).fetch(flight)
        val airborne = providerAt(departure + 4 * 3_600_000).fetch(flight)
        val afterwards = providerAt(arrival + 4 * 3_600_000).fetch(flight)

        assertTrue(wellBefore.state == FlightState.SCHEDULED || wellBefore.state == FlightState.DELAYED)
        assertTrue(
            "boarding phase was ${boarding.state}",
            boarding.state in setOf(
                FlightState.BOARDING,
                FlightState.GATE_CLOSED,
                FlightState.DELAYED,
                FlightState.CANCELLED,
            ),
        )
        assertTrue(
            "airborne phase was ${airborne.state}",
            airborne.state.isAirborne || airborne.state == FlightState.CANCELLED,
        )
        assertTrue(
            "post-arrival phase was ${afterwards.state}",
            afterwards.state == FlightState.ARRIVED || afterwards.state == FlightState.CANCELLED,
        )
    }

    @Test
    fun `an airborne flight reports a position on the route`() = runBlocking {
        val status = providerAt(departure + 4 * 3_600_000).fetch(flight)
        if (!status.state.isAirborne) return@runBlocking
        val position = requireNotNull(status.position)
        assertTrue(status.progressPercent in 1..99)
        // Somewhere over the Atlantic or Canada: north of both airports and
        // between their longitudes.
        assertTrue(position.latitude > 37.0)
        assertTrue(position.longitude in -123.0..0.0)
        assertTrue((position.altitudeFeet ?: 0) > 20_000)
    }

    @Test
    fun `delays are reported on both ends, with some time clawed back in the air`() =
        runBlocking {
            // Sweep a month of dates to find a delayed instance of this flight.
            val delayed = (0..30).asSequence()
                .map { offset ->
                    flight.copy(
                        id = "seg-$offset",
                        startEpochMillis = departure + offset * 86_400_000L,
                        endEpochMillis = arrival + offset * 86_400_000L,
                    )
                }
                .map { candidate ->
                    candidate to runBlocking {
                        providerAt(candidate.startEpochMillis - 3_600_000).fetch(candidate)
                    }
                }
                .firstOrNull { (_, status) ->
                    status.departureDelayMinutes >= 15 && status.state != FlightState.CANCELLED
                }

            requireNotNull(delayed) { "the model should produce delays across a month" }
            val (_, status) = delayed
            assertTrue(status.arrivalDelayMinutes > 0)
            assertTrue(status.arrivalDelayMinutes <= status.departureDelayMinutes)
        }

    @Test
    fun `most flights are not delayed`() = runBlocking {
        val statuses = (0..99).map { offset ->
            val candidate = flight.copy(
                id = "seg-$offset",
                startEpochMillis = departure + offset * 86_400_000L,
                endEpochMillis = arrival + offset * 86_400_000L,
            )
            providerAt(candidate.startEpochMillis - 7_200_000).fetch(candidate)
        }
        val onTime = statuses.count { it.departureDelayMinutes == 0 }
        assertTrue("only $onTime of 100 were on time", onTime > 50)
    }
}
