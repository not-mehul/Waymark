package com.waymark.data.repo

import com.waymark.data.local.Mappers
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.model.RaisedReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The record of reminders raised, which is what stops a booking being
 * announced on every check — and what the trip screen's banner reads.
 */
class AlertRepository(database: WaymarkDatabase) {

    private val alerts = database.alertDao()

    fun observe(): Flow<List<RaisedReminder>> =
        alerts.observeRecent().map { rows -> rows.map(Mappers::toReminder) }

    /**
     * The stored reminder for [reminder]'s signature, recording it first if
     * this is the first time it has fallen due. Recording and posting are
     * separate: a reminder raised while notifications are blocked is still
     * raised — the banner shows it — and is posted later if it can be.
     */
    suspend fun raise(reminder: RaisedReminder, nowMillis: Long = System.currentTimeMillis()): RaisedReminder {
        alerts.find(reminder.signature)?.let { return Mappers.toReminder(it) }
        alerts.upsert(Mappers.toEntity(reminder.copy(posted = false, acknowledged = false)))
        alerts.prune(nowMillis - RETENTION_MILLIS)
        return reminder.copy(posted = false, acknowledged = false)
    }

    suspend fun markPosted(signature: String) = alerts.markPosted(signature)

    /** Dismissed from the trip screen. */
    suspend fun acknowledge(segmentId: String) = alerts.acknowledgeFor(segmentId)

    private companion object {
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
