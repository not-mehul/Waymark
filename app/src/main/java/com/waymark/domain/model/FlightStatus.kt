package com.waymark.domain.model

import java.time.Duration
import java.time.Instant

enum class FlightState {
    SCHEDULED, ON_TIME, DELAYED, BOARDING, GATE_CLOSED, DEPARTED, EN_ROUTE,
    LANDED, ARRIVED, DIVERTED, CANCELLED, UNKNOWN;

    val label: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    val isDisrupted: Boolean get() = this == DELAYED || this == CANCELLED || this == DIVERTED
    val isAirborne: Boolean get() = this == EN_ROUTE || this == DEPARTED
}

/**
 * A live-ish view of one flight. Produced by a [com.waymark.data.remote.FlightStatusProvider]
 * and cached so the timeline still says something sensible with the radio off.
 */
data class FlightStatus(
    val segmentId: String,
    val designator: String,
    val state: FlightState,
    val scheduledDepartureMillis: Long,
    val estimatedDepartureMillis: Long,
    val scheduledArrivalMillis: Long,
    val estimatedArrivalMillis: Long,
    val departureTerminal: String? = null,
    val departureGate: String? = null,
    val arrivalTerminal: String? = null,
    val arrivalGate: String? = null,
    val baggageBelt: String? = null,
    val boardingMillis: Long? = null,
    val position: Position? = null,
    val progressPercent: Int = 0,
    val observedAtMillis: Long = System.currentTimeMillis(),
    val source: String = "offline",
) {
    /** Positive when late, negative when the flight is running early. */
    val departureDelayMinutes: Int
        get() = ((estimatedDepartureMillis - scheduledDepartureMillis) / 60_000L).toInt()

    val arrivalDelayMinutes: Int
        get() = ((estimatedArrivalMillis - scheduledArrivalMillis) / 60_000L).toInt()

    val isStale: Boolean
        get() = Duration.ofMillis(System.currentTimeMillis() - observedAtMillis).toMinutes() > 30

    fun observedAt(): Instant = Instant.ofEpochMilli(observedAtMillis)

    data class Position(
        val latitude: Double,
        val longitude: Double,
        val altitudeFeet: Int? = null,
        val groundSpeedKnots: Int? = null,
        val headingDegrees: Int? = null,
    )
}

/** What the app tells the traveler about a change it detected. */
data class DisruptionAlert(
    val segmentId: String,
    val designator: String,
    val headline: String,
    val detail: String,
    val severity: Severity,
    val raisedAtMillis: Long = System.currentTimeMillis(),
) {
    enum class Severity { NOTICE, WARNING, CRITICAL }
}
