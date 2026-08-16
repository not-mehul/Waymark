package com.waymark.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waymark.WaymarkApplication
import com.waymark.data.local.Mappers
import com.waymark.domain.logic.ReminderPlanner
import com.waymark.domain.model.DisruptionAlert
import java.util.concurrent.TimeUnit

/**
 * The one thing a travel app can honestly tell you without a network: that
 * something you booked is about to happen.
 *
 * Waymark has no feed to poll, so this worker does not go looking for delays —
 * it reads the clock against the itinerary already on the device and posts a
 * reminder as each booking comes into range. It used to do that for flights
 * only, which made "reminders" a promise the app kept for a quarter of what it
 * stored: a Eurostar, a hotel check-in and a booked table all passed unremarked.
 * Every kind of booking is watched now, each on its own lead time, and any of
 * them can be switched off.
 *
 * The deciding is in [ReminderPlanner], which is plain Kotlin and tested. What
 * is left here is the plumbing: read the window, ask what is due, and post the
 * ones that have not been posted before.
 */
class DepartureWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WaymarkApplication)?.container
            ?: return Result.success()

        val preferences = container.reminderStore.current()
        val lookAhead = ReminderPlanner.lookAheadMillis(preferences)
        if (lookAhead <= 0L) return Result.success()

        return runCatching {
            val now = System.currentTimeMillis()
            val segments = container.database.segmentDao()
                .startingInWindow(fromMillis = now, toMillis = now + lookAhead)
                .map(Mappers::toSegment)

            ReminderPlanner.due(segments, preferences, now).forEach { reminder ->
                val alert = DisruptionAlert(
                    segmentId = reminder.segmentId,
                    label = reminder.label,
                    headline = reminder.headline,
                    detail = reminder.detail,
                    severity = DisruptionAlert.Severity.NOTICE,
                    raisedAtMillis = now,
                )
                container.alertRepository
                    .raiseOnce(reminder.signature, alert, now)
                    ?.let { Notifications.post(applicationContext, it) }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object DepartureWatch {

    private const val WORK_NAME = "waymark.departure-watch"

    /**
     * Every fifteen minutes, which is WorkManager's floor for periodic work and
     * the reason the shortest lead time offered is also fifteen minutes: a
     * reminder cannot be more punctual than the check that raises it.
     */
    fun schedule(context: Context) {
        // No constraints at all: this reads local storage and the clock, so
        // there is nothing to wait for — not a network, not a charger.
        val request = PeriodicWorkRequestBuilder<DepartureWatchWorker>(15, TimeUnit.MINUTES).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // REPLACE rather than KEEP: the period changed from thirty minutes
            // to fifteen, and an install that already has the old request
            // enqueued would otherwise keep it forever.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
