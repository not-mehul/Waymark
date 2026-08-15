package com.waymark.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waymark.WaymarkApplication
import com.waymark.data.local.Mappers
import com.waymark.domain.model.Segment
import java.util.concurrent.TimeUnit

/**
 * Watches the flights the traveler is about to be on.
 *
 * The window is deliberately narrow — from two hours ago to thirty hours out
 * — because a delay on next month's flight is noise, and because polling every
 * flight in the database would spend battery on history.
 */
class DelayWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WaymarkApplication)?.container
            ?: return Result.success()

        val now = System.currentTimeMillis()
        val flights = container.database.segmentDao()
            .flightsInWindow(
                fromMillis = now - LOOK_BACK_MILLIS,
                toMillis = now + LOOK_AHEAD_MILLIS,
            )
            .map(Mappers::toSegment)
            .filterIsInstance<Segment.Flight>()

        if (flights.isEmpty()) return Result.success()

        return runCatching {
            container.flightRepository.refresh(flights).forEach { alert ->
                Notifications.post(applicationContext, alert)
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private companion object {
        const val LOOK_BACK_MILLIS = 2L * 60 * 60 * 1000
        const val LOOK_AHEAD_MILLIS = 30L * 60 * 60 * 1000
    }
}

object DelayWatch {

    private const val WORK_NAME = "waymark.delay-watch"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DelayWatchWorker>(20, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    // The offline model needs nothing, but there is no point
                    // waking for a live refresh with the radio down.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
