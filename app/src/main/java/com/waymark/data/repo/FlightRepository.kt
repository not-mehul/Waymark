package com.waymark.data.repo

import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.FlightCatalog
import com.waymark.data.local.Mappers
import com.waymark.data.local.RaisedAlertEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.remote.FlightStatusProvider
import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What a flight-number lookup can come back with. */
sealed interface FlightLookup {
    data class Found(val plan: FlightCatalog.FlightPlan) : FlightLookup
    data class Unknown(val designator: FlightDesignator) : FlightLookup
    data object Unparseable : FlightLookup
}

/**
 * Flight schedules and live state.
 *
 * Providers are tried in order and the first answer wins; the offline model is
 * always last and always answers, which is what keeps the timeline populated
 * at 38,000 feet.
 */
class FlightRepository(
    database: WaymarkDatabase,
    private val providers: List<FlightStatusProvider>,
) {

    private val statuses = database.flightStatusDao()
    private val alerts = database.alertDao()

    fun observeStatuses(): Flow<Map<String, FlightStatus>> =
        statuses.observeAll().map { rows ->
            rows.associate { it.segmentId to Mappers.toStatus(it) }
        }

    fun observeAlerts(): Flow<List<DisruptionAlert>> =
        alerts.observeRecent().map { rows -> rows.map(Mappers::toAlert) }

    suspend fun cachedStatus(segmentId: String): FlightStatus? =
        statuses.find(segmentId)?.let(Mappers::toStatus)

    /** Resolve a typed flight number onto a date. */
    suspend fun lookup(input: String, date: LocalDate): FlightLookup {
        val designator = FlightDesignator.parse(input) ?: return FlightLookup.Unparseable
        FlightCatalog.lookup(designator.normalised, date)?.let { return FlightLookup.Found(it) }
        return FlightLookup.Unknown(designator)
    }

    fun searchStations(query: String) = Airports.search(query)

    /**
     * Refresh state for a set of flights and return anything worth telling the
     * traveler about. Alerts are de-duplicated by signature so an unchanged
     * delay is reported once, not once per refresh.
     */
    suspend fun refresh(flights: List<Segment.Flight>): List<DisruptionAlert> {
        if (flights.isEmpty()) return emptyList()
        // `isAvailable` is a suspending check (it may consult the network), so
        // the usable set is resolved once, in order, before the loop.
        val usable = providers.filter { it.isAvailable() }
        if (usable.isEmpty()) return emptyList()
        val raised = mutableListOf<DisruptionAlert>()

        flights.forEach { flight ->
            val previous = statuses.find(flight.id)?.let(Mappers::toStatus)
            val next = usable.firstNotNullOfOrNull { it.fetch(flight) } ?: return@forEach
            statuses.upsert(Mappers.toEntity(next))
            alertFor(flight, previous, next)?.let { alert ->
                val signature = signatureOf(alert, next)
                if (alerts.find(signature) == null) {
                    alerts.upsert(
                        RaisedAlertEntity(
                            signature = signature,
                            segmentId = alert.segmentId,
                            designator = alert.designator,
                            headline = alert.headline,
                            detail = alert.detail,
                            severity = alert.severity.name,
                            raisedAtMillis = alert.raisedAtMillis,
                        )
                    )
                    raised += alert
                }
            }
        }
        alerts.prune(System.currentTimeMillis() - RETENTION_MILLIS)
        return raised
    }

    suspend fun acknowledge(segmentId: String) = alerts.acknowledgeFor(segmentId)

    /**
     * What changed, in the traveler's terms. Silence is the default: only a
     * material change earns a line.
     */
    private fun alertFor(
        flight: Segment.Flight,
        previous: FlightStatus?,
        next: FlightStatus,
    ): DisruptionAlert? {
        val designator = flight.designator
        if (next.state == FlightState.CANCELLED && previous?.state != FlightState.CANCELLED) {
            return DisruptionAlert(
                segmentId = flight.id,
                designator = designator,
                headline = "$designator is cancelled",
                detail = "${flight.origin.shortLabel} → ${flight.destination.shortLabel}. " +
                    "Rebooking is not automatic.",
                severity = DisruptionAlert.Severity.CRITICAL,
            )
        }

        val delay = next.departureDelayMinutes
        val previousDelay = previous?.departureDelayMinutes ?: 0
        if (delay >= MATERIAL_DELAY_MINUTES && delay - previousDelay >= DELAY_STEP_MINUTES) {
            return DisruptionAlert(
                segmentId = flight.id,
                designator = designator,
                headline = "$designator delayed ${TimeText.duration(delay)}",
                detail = "Now leaving ${flight.origin.shortLabel} at " +
                    TimeText.clock(
                        java.time.Instant.ofEpochMilli(next.estimatedDepartureMillis)
                            .atZone(Segment.zoneOrUtc(flight.startZoneId))
                    ) +
                    ". Arrival ${TimeText.duration(next.arrivalDelayMinutes)} late.",
                severity = if (delay >= 90) {
                    DisruptionAlert.Severity.CRITICAL
                } else {
                    DisruptionAlert.Severity.WARNING
                },
            )
        }

        val gateChanged = previous?.departureGate != null &&
            next.departureGate != null &&
            previous.departureGate != next.departureGate
        if (gateChanged) {
            return DisruptionAlert(
                segmentId = flight.id,
                designator = designator,
                headline = "$designator moved to gate ${next.departureGate}",
                detail = "Previously ${previous?.departureGate} at ${flight.origin.shortLabel}.",
                severity = DisruptionAlert.Severity.NOTICE,
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
            )
        }
        return null
    }

    private fun signatureOf(alert: DisruptionAlert, status: FlightStatus): String = listOf(
        alert.segmentId,
        alert.severity.name,
        status.state.name,
        status.departureGate.orEmpty(),
        (status.departureDelayMinutes / DELAY_STEP_MINUTES).toString(),
    ).joinToString("|")

    private companion object {
        const val MATERIAL_DELAY_MINUTES = 15
        const val DELAY_STEP_MINUTES = 15
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
