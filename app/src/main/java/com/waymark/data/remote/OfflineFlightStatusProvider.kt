package com.waymark.data.remote

import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import kotlin.math.abs

/**
 * A flight-state model that works with the radio off.
 *
 * It is deterministic: the same flight on the same day always yields the same
 * schedule adjustment, so the app never contradicts itself between screens or
 * across restarts. Phase (boarding, airborne, landed) is then derived from the
 * real clock, which is what makes it useful rather than decorative.
 *
 * It is labelled as a model everywhere it surfaces. It is not a claim about
 * the actual aircraft.
 */
class OfflineFlightStatusProvider(
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : FlightStatusProvider {

    override val sourceName: String = "Offline model"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun fetch(flight: Segment.Flight): FlightStatus {
        val now = nowProvider()
        val seed = seedFor(flight)
        val delayMinutes = delayFor(seed)
        val cancelled = (seed % 97L) == 3L

        val estimatedDeparture = flight.startEpochMillis + delayMinutes * 60_000L
        // Crews claw back some of a departure delay in the air, never all of it.
        val recovered = (delayMinutes * 0.25).toInt()
        val estimatedArrival = flight.endEpochMillis + (delayMinutes - recovered) * 60_000L
        val boarding = estimatedDeparture - 40 * 60_000L

        val state = when {
            cancelled -> FlightState.CANCELLED
            now >= estimatedArrival + 12 * 60_000L -> FlightState.ARRIVED
            now >= estimatedArrival -> FlightState.LANDED
            now >= estimatedDeparture -> FlightState.EN_ROUTE
            now >= estimatedDeparture - 20 * 60_000L -> FlightState.GATE_CLOSED
            now >= boarding -> FlightState.BOARDING
            delayMinutes >= 15 -> FlightState.DELAYED
            now >= estimatedDeparture - 24 * 3_600_000L -> FlightState.ON_TIME
            else -> FlightState.SCHEDULED
        }

        val progress = when {
            state == FlightState.CANCELLED -> 0
            now <= estimatedDeparture -> 0
            now >= estimatedArrival -> 100
            else -> {
                val span = (estimatedArrival - estimatedDeparture).coerceAtLeast(1L)
                (((now - estimatedDeparture).toDouble() / span) * 100).toInt().coerceIn(0, 100)
            }
        }

        val position = if (state.isAirborne && flight.origin.hasCoordinates && flight.destination.hasCoordinates) {
            val point = Geo.interpolate(
                LatLon(flight.origin.latitude, flight.origin.longitude),
                LatLon(flight.destination.latitude, flight.destination.longitude),
                progress / 100.0,
            )
            FlightStatus.Position(
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeFeet = cruiseAltitude(seed, progress),
                groundSpeedKnots = 430 + (seed % 70L).toInt(),
                headingDegrees = Geo.bearingDegrees(
                    LatLon(point.latitude, point.longitude),
                    LatLon(flight.destination.latitude, flight.destination.longitude),
                ).toInt(),
            )
        } else {
            null
        }

        return FlightStatus(
            segmentId = flight.id,
            designator = flight.designator,
            state = state,
            scheduledDepartureMillis = flight.startEpochMillis,
            estimatedDepartureMillis = if (cancelled) flight.startEpochMillis else estimatedDeparture,
            scheduledArrivalMillis = flight.endEpochMillis,
            estimatedArrivalMillis = if (cancelled) flight.endEpochMillis else estimatedArrival,
            departureTerminal = flight.departureTerminal,
            departureGate = flight.departureGate ?: gateFor(seed, flight.origin.code),
            arrivalTerminal = flight.arrivalTerminal,
            arrivalGate = flight.arrivalGate,
            baggageBelt = if (state == FlightState.LANDED || state == FlightState.ARRIVED) {
                ((seed % 8L) + 1L).toString()
            } else {
                null
            },
            boardingMillis = if (cancelled) null else boarding,
            position = position,
            progressPercent = progress,
            observedAtMillis = now,
            source = sourceName,
        )
    }

    /**
     * Stable across restarts: designator plus scheduled departure day.
     *
     * The day is run through a SplitMix64 finaliser rather than a plain
     * string hash. A string hash of consecutive days produces consecutive
     * seeds, which would make tomorrow's delay a near-copy of today's — the
     * one property this model must not have.
     */
    private fun seedFor(flight: Segment.Flight): Long {
        val day = flight.startEpochMillis.floorDiv(86_400_000L)
        var hash = 1125899906842597L
        flight.designator.forEach { char -> hash = 31 * hash + char.code }
        var mixed = hash xor (day * -7046029254386353131L)
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        mixed = mixed xor (mixed ushr 31)
        return abs(mixed % 100_003L)
    }

    /**
     * Roughly the real-world shape: most flights leave near enough on time, a
     * fifth run meaningfully late, a few are badly late.
     */
    private fun delayFor(seed: Long): Int = when ((seed % 100L).toInt()) {
        in 0..64 -> 0
        in 65..74 -> ((seed / 7L) % 12L).toInt() + 3
        in 75..89 -> ((seed / 11L) % 45L).toInt() + 15
        in 90..96 -> ((seed / 13L) % 90L).toInt() + 60
        else -> ((seed / 17L) % 120L).toInt() + 150
    }

    private fun cruiseAltitude(seed: Long, progress: Int): Int = when {
        progress < 8 -> 12_000 + progress * 1_500
        progress > 92 -> 8_000 + (100 - progress) * 1_800
        else -> 33_000 + ((seed % 5L).toInt() * 1_000)
    }

    private fun gateFor(seed: Long, airportCode: String?): String? {
        if (airportCode == null) return null
        val letter = ('A' + (seed % 6L).toInt())
        val number = (seed % 40L).toInt() + 1
        return "$letter$number"
    }
}
