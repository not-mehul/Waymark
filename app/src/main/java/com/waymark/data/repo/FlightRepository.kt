package com.waymark.data.repo

import com.waymark.data.catalog.Airports
import com.waymark.data.local.Mappers
import com.waymark.data.local.RaisedAlertEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.FlightUpdate
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flight state, as the party reports it.
 *
 * There is no feed behind this. Waymark never calls a flight-data service, so
 * the only thing that can change a flight's state is a traveler typing what
 * the departure board says. That keeps every number on screen traceable to a
 * person who saw it, and it is why the app works identically on a plane, in a
 * foreign SIM-less airport, and at home.
 */
class FlightRepository(database: WaymarkDatabase) {

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

    fun searchStations(query: String) = Airports.search(query)

    /**
     * Record what the traveler saw, and return anything the rest of the party
     * ought to hear about. Alerts are de-duplicated by signature so re-entering
     * an unchanged delay stays quiet.
     */
    suspend fun record(
        flight: Segment.Flight,
        update: FlightUpdate,
        nowMillis: Long = System.currentTimeMillis(),
    ): DisruptionAlert? {
        if (update.isEmpty) return null

        val previous = statuses.find(flight.id)?.let(Mappers::toStatus)
        val next = FlightUpdate.apply(flight, previous, update, nowMillis)
        statuses.upsert(Mappers.toEntity(next))

        val alert = FlightUpdate.alertFor(flight, previous, next) ?: return null
        val signature = FlightUpdate.signatureOf(alert, next)
        if (alerts.find(signature) != null) return null

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
        alerts.prune(nowMillis - RETENTION_MILLIS)
        return alert
    }

    /** Forget a hand-entered status, putting the flight back on its booked times. */
    suspend fun clearStatus(segmentId: String) {
        statuses.delete(segmentId)
        alerts.acknowledgeFor(segmentId)
    }

    /**
     * Raise a one-off reminder that is not a change of state — an imminent
     * departure, a passport about to expire. Deduplicated on the same table as
     * disruption alerts, so a reminder is posted once and not once per check.
     */
    suspend fun raiseOnce(
        signature: String,
        alert: DisruptionAlert,
        nowMillis: Long = System.currentTimeMillis(),
    ): DisruptionAlert? {
        if (alerts.find(signature) != null) return null
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
        alerts.prune(nowMillis - RETENTION_MILLIS)
        return alert
    }

    suspend fun acknowledge(segmentId: String) = alerts.acknowledgeFor(segmentId)

    private companion object {
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
