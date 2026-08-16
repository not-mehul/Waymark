package com.waymark.data.repo

import com.waymark.data.local.Mappers
import com.waymark.data.local.RaisedAlertEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.model.DisruptionAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one thing Waymark tells a traveler without being asked.
 *
 * There is no feed to watch and no status to track — the app knows only what
 * is on the itinerary and what time it is — so the entire alert surface is
 * "something you booked is about to happen". Each reminder is written once
 * against a signature, so the worker can run every half hour without a flight
 * being announced twice.
 */
class AlertRepository(database: WaymarkDatabase) {

    private val alerts = database.alertDao()

    fun observe(): Flow<List<DisruptionAlert>> =
        alerts.observeRecent().map { rows -> rows.map(Mappers::toAlert) }

    /** Returns the alert when it was new, null when it had already been raised. */
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
