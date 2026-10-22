package com.waymark.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waymark.WaymarkApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * The safety net under [ReminderAlarm].
 *
 * Reminders are raised on time by an alarm set for the next one. This worker
 * runs the same check every fifteen minutes anyway — WorkManager's floor — so
 * an alarm lost to a reboot, a force-stop or a clock change is put back within
 * a quarter of an hour, and survives anything WorkManager survives.
 */
class DepartureWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WaymarkApplication)?.container
            ?: return Result.success()
        return try {
            ReminderCheck.run(applicationContext, container)
            Result.success()
        } catch (cancelled: CancellationException) {
            // Stopped by WorkManager: not a failure to retry.
            throw cancelled
        } catch (error: Exception) {
            Result.retry()
        }
    }
}

object DepartureWatch {

    private const val WORK_NAME = "waymark.departure-watch"

    fun schedule(context: Context) {
        // No constraints at all: this reads local storage and the clock, so
        // there is nothing to wait for — not a network, not a charger.
        val request = PeriodicWorkRequestBuilder<DepartureWatchWorker>(15, TimeUnit.MINUTES).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // UPDATE rather than KEEP, so a change to the request in a new
            // version replaces the one an existing install already enqueued.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
