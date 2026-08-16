package com.waymark.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waymark.WaymarkApplication
import com.waymark.data.local.Mappers
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Segment
import java.util.concurrent.TimeUnit

/**
 * The one thing a travel app can honestly tell you without a network: that
 * something you booked is about to happen.
 *
 * Waymark has no feed to poll, so this worker does not go looking for delays —
 * it reads the clock against the itinerary already on the device and posts a
 * single reminder as each departure comes into range. Reminders are recorded
 * on the same table as disruption alerts, so each one fires once no matter how
 * often the worker runs.
 */
class DepartureWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WaymarkApplication)?.container
            ?: return Result.success()

        val now = System.currentTimeMillis()
        val flights = container.database.segmentDao()
            .flightsInWindow(fromMillis = now, toMillis = now + LOOK_AHEAD_MILLIS)
            .map(Mappers::toSegment)
            .filterIsInstance<Segment.Flight>()

        return runCatching {
            flights.forEach { flight ->
                val minutesOut = ((flight.startEpochMillis - now) / 60_000L).toInt()
                val alert = DisruptionAlert(
                    segmentId = flight.id,
                    designator = flight.designator,
                    headline = "${flight.designator} departs in ${TimeText.duration(minutesOut)}",
                    detail = buildString {
                        append(flight.origin.shortLabel)
                        flight.departureTerminal?.let { append(", terminal $it") }
                        flight.departureGate?.let { append(", gate $it") }
                        append(" → ${flight.destination.shortLabel}")
                    },
                    severity = DisruptionAlert.Severity.NOTICE,
                    raisedAtMillis = now,
                )
                // One reminder per flight per departure date: the signature
                // deliberately omits the countdown so a later run does not
                // announce the same flight again with a smaller number.
                container.flightRepository
                    .raiseOnce("departure|${flight.id}|${flight.startEpochMillis}", alert, now)
                    ?.let { Notifications.post(applicationContext, it) }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private companion object {
        const val LOOK_AHEAD_MILLIS = 4L * 60 * 60 * 1000
    }
}

object DepartureWatch {

    private const val WORK_NAME = "waymark.departure-watch"

    fun schedule(context: Context) {
        // No constraints at all: this reads local storage and the clock, so
        // there is nothing to wait for — not a network, not a charger.
        val request = PeriodicWorkRequestBuilder<DepartureWatchWorker>(30, TimeUnit.MINUTES).build()

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
