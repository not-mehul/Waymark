package com.waymark.domain.logic

import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment

/**
 * A change to a flight, as the traveler reports it.
 *
 * Waymark has no feed. Everything it knows about a flight came from the person
 * holding the phone — off the boarding pass, the departure board, or the
 * announcement they just heard. This is that report, turned into the same
 * [FlightStatus] the rest of the app already reads, so the timeline, the map
 * and the connection maths all work from one shape.
 *
 * Fields left null mean "unchanged", not "cleared": a traveler noting a new
 * gate should not wipe the terminal they entered yesterday.
 */
data class FlightUpdate(
    val state: FlightState? = null,
    val departureDelayMinutes: Int? = null,
    val arrivalDelayMinutes: Int? = null,
    val departureTerminal: String? = null,
    val departureGate: String? = null,
    val arrivalTerminal: String? = null,
    val arrivalGate: String? = null,
    val baggageBelt: String? = null,
) {
    val isEmpty: Boolean
        get() = state == null &&
            departureDelayMinutes == null &&
            arrivalDelayMinutes == null &&
            listOf(
                departureTerminal,
                departureGate,
                arrivalTerminal,
                arrivalGate,
                baggageBelt,
            ).all { it.isNullOrBlank() }

    companion object {
        const val SOURCE = "Entered by hand"

        /**
         * Fold an update onto what the app already held.
         *
         * A delay entered for departure carries to arrival unless the traveler
         * says otherwise — the aircraft that leaves late generally lands late,
         * and making them type the same number twice would be rude.
         */
        fun apply(
            flight: Segment.Flight,
            previous: FlightStatus?,
            update: FlightUpdate,
            nowMillis: Long,
        ): FlightStatus {
            val departureDelay = update.departureDelayMinutes
                ?: previous?.departureDelayMinutes
                ?: 0
            val arrivalDelay = update.arrivalDelayMinutes
                ?: update.departureDelayMinutes
                ?: previous?.arrivalDelayMinutes
                ?: 0

            val state = update.state
                ?: previous?.state
                ?: if (departureDelay >= MATERIAL_DELAY_MINUTES) {
                    FlightState.DELAYED
                } else {
                    FlightState.SCHEDULED
                }

            return FlightStatus(
                segmentId = flight.id,
                designator = flight.designator,
                state = state,
                scheduledDepartureMillis = flight.startEpochMillis,
                estimatedDepartureMillis = flight.startEpochMillis + departureDelay * 60_000L,
                scheduledArrivalMillis = flight.endEpochMillis,
                estimatedArrivalMillis = flight.endEpochMillis + arrivalDelay * 60_000L,
                departureTerminal = pick(
                    update.departureTerminal,
                    previous?.departureTerminal,
                    flight.departureTerminal,
                ),
                departureGate = pick(
                    update.departureGate,
                    previous?.departureGate,
                    flight.departureGate,
                ),
                arrivalTerminal = pick(
                    update.arrivalTerminal,
                    previous?.arrivalTerminal,
                    flight.arrivalTerminal,
                ),
                arrivalGate = pick(update.arrivalGate, previous?.arrivalGate, flight.arrivalGate),
                baggageBelt = pick(update.baggageBelt, previous?.baggageBelt),
                boardingMillis = previous?.boardingMillis,
                position = null,
                progressPercent = progressOf(
                    state = state,
                    departureMillis = flight.startEpochMillis + departureDelay * 60_000L,
                    arrivalMillis = flight.endEpochMillis + arrivalDelay * 60_000L,
                    nowMillis = nowMillis,
                ),
                observedAtMillis = nowMillis,
                source = SOURCE,
            )
        }

        /**
         * What, if anything, is worth telling the party about.
         *
         * Silence is the default. A traveler who corrects a typo in a gate
         * letter has not had a gate change, and a delay that has not grown by a
         * quarter of an hour is the same delay they already know about.
         */
        fun alertFor(
            flight: Segment.Flight,
            previous: FlightStatus?,
            next: FlightStatus,
        ): DisruptionAlert? {
            val designator = flight.designator
            val route = "${flight.origin.shortLabel} → ${flight.destination.shortLabel}"

            if (next.state == FlightState.CANCELLED && previous?.state != FlightState.CANCELLED) {
                return DisruptionAlert(
                    segmentId = flight.id,
                    designator = designator,
                    headline = "$designator is cancelled",
                    detail = "$route. Rebooking is not automatic.",
                    severity = DisruptionAlert.Severity.CRITICAL,
                    raisedAtMillis = next.observedAtMillis,
                )
            }

            val delay = next.departureDelayMinutes
            val previousDelay = previous?.departureDelayMinutes ?: 0
            if (delay >= MATERIAL_DELAY_MINUTES && delay - previousDelay >= DELAY_STEP_MINUTES) {
                return DisruptionAlert(
                    segmentId = flight.id,
                    designator = designator,
                    headline = "$designator delayed ${TimeText.duration(delay)}",
                    detail = "$route. Arrival ${TimeText.duration(next.arrivalDelayMinutes)} late.",
                    severity = if (delay >= SEVERE_DELAY_MINUTES) {
                        DisruptionAlert.Severity.CRITICAL
                    } else {
                        DisruptionAlert.Severity.WARNING
                    },
                    raisedAtMillis = next.observedAtMillis,
                )
            }

            val gateChanged = !previous?.departureGate.isNullOrBlank() &&
                !next.departureGate.isNullOrBlank() &&
                previous?.departureGate != next.departureGate
            if (gateChanged) {
                return DisruptionAlert(
                    segmentId = flight.id,
                    designator = designator,
                    headline = "$designator moved to gate ${next.departureGate}",
                    detail = "Previously ${previous?.departureGate} at ${flight.origin.shortLabel}.",
                    severity = DisruptionAlert.Severity.NOTICE,
                    raisedAtMillis = next.observedAtMillis,
                )
            }

            if (next.state == FlightState.BOARDING && previous?.state != FlightState.BOARDING) {
                return DisruptionAlert(
                    segmentId = flight.id,
                    designator = designator,
                    headline = "$designator is boarding",
                    detail = buildString {
                        append(flight.origin.shortLabel)
                        next.departureGate?.let { append(", gate $it") }
                        next.departureTerminal?.let { append(", terminal $it") }
                    },
                    severity = DisruptionAlert.Severity.NOTICE,
                    raisedAtMillis = next.observedAtMillis,
                )
            }
            return null
        }

        /** A signature that changes only when the news does. */
        fun signatureOf(alert: DisruptionAlert, status: FlightStatus): String = listOf(
            alert.segmentId,
            alert.severity.name,
            status.state.name,
            status.departureGate.orEmpty(),
            (status.departureDelayMinutes / DELAY_STEP_MINUTES).toString(),
        ).joinToString("|")

        private fun progressOf(
            state: FlightState,
            departureMillis: Long,
            arrivalMillis: Long,
            nowMillis: Long,
        ): Int = when {
            state == FlightState.CANCELLED -> 0
            nowMillis <= departureMillis -> 0
            nowMillis >= arrivalMillis -> 100
            else -> {
                val span = (arrivalMillis - departureMillis).coerceAtLeast(1L)
                (((nowMillis - departureMillis).toDouble() / span) * 100).toInt().coerceIn(0, 100)
            }
        }

        private fun pick(vararg candidates: String?): String? =
            candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

        const val MATERIAL_DELAY_MINUTES = 15
        const val DELAY_STEP_MINUTES = 15
        private const val SEVERE_DELAY_MINUTES = 90
    }
}
