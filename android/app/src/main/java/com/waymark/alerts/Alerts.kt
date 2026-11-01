package com.waymark.alerts

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

/**
 * The one routine that posts reminders, wherever it is woken from: the alarm
 * set for the next one, the periodic safety-net worker, or the page sending a
 * new schedule.
 *
 * It posts what is due and only then records it as posted, so one that fell
 * due while notifications were blocked is still shown once they are allowed,
 * if it is still worth it. It brings the journey under way up to date. Then it
 * sets a single alarm for the next reminder or redraw, which is what makes
 * them punctual: a periodic worker can be deferred by Doze past the very
 * departure it was meant to announce.
 *
 * The Kotlin app worked the reminders out here, from its database. The page's
 * IndexedDB cannot be read with the app closed, so the page works them out
 * instead ([Schedule]) and this keeps time.
 */
object Alerts {

    private const val PREFERENCES = "waymark.alerts"
    private const val SCHEDULE = "schedule"
    private const val POSTED = "posted"
    private const val TAG = "Waymark"

    private val lock = Mutex()

    /** Keeps the page's new [schedule], then posts whatever is due under it. */
    suspend fun update(context: Context, schedule: Schedule, nowMillis: Long = System.currentTimeMillis()) {
        lock.withLock {
            preferences(context).edit { putString(SCHEDULE, schedule.toJson().toString()) }
        }
        check(context, nowMillis)
    }

    suspend fun check(context: Context, nowMillis: Long = System.currentTimeMillis()) = lock.withLock {
        val stored = preferences(context)
        val schedule = read(context)
        val posted = stored.getStringSet(POSTED, emptySet()).orEmpty().toMutableSet()

        if (Notifications.granted(context)) {
            schedule.due(nowMillis)
                .filter { it.signature !in posted }
                .forEach { if (Notifications.post(context, it)) posted += it.signature }
        }
        // Only what the schedule still holds: the rest is past.
        stored.edit { putStringSet(POSTED, posted.intersect(schedule.signatures)) }

        LiveUpdates.show(context, schedule.journey(nowMillis))
        ReminderAlarm.set(context, schedule.nextAlarmAt(nowMillis))
    }

    fun read(context: Context): Schedule {
        val text = preferences(context).getString(SCHEDULE, null) ?: return Schedule.EMPTY
        return try {
            Schedule.fromJson(JSONObject(text))
        } catch (malformed: JSONException) {
            Log.w(TAG, "Dropping an unreadable reminder schedule", malformed)
            Schedule.EMPTY
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/** The single alarm for the next reminder or redraw due anywhere in the app. */
object ReminderAlarm {

    /**
     * Exact where the platform allows it without a special permission (below
     * Android 12), and otherwise "while idle", which Doze honours within a few
     * minutes rather than at its next maintenance window.
     */
    // Exact only below Android 12, where it needs no permission; lint cannot
    // see the version check.
    @SuppressLint("MissingPermission")
    fun set(context: Context, atMillis: Long?) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent(context)
        if (atMillis == null) {
            alarms.cancel(pending)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    // The same receiver class as the Kotlin app's, so an alarm it set before
    // the upgrade still lands here.
    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Woken by [ReminderAlarm]; runs the check and lets the process sleep again. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                Alerts.check(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * The safety net under [ReminderAlarm]. Reminders are posted on time by an
 * alarm set for the next one; this runs the same check every fifteen minutes
 * anyway, WorkManager's floor, so an alarm lost to a reboot, a force-stop or a
 * clock change is put back within a quarter of an hour.
 *
 * The same class name and unique work name as the Kotlin app's, so the work it
 * enqueued carries on after the upgrade rather than failing to find a class.
 */
class DepartureWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        Alerts.check(applicationContext)
        Result.success()
    } catch (cancelled: CancellationException) {
        // Stopped by WorkManager: not a failure to retry.
        throw cancelled
    } catch (error: Exception) {
        Log.w("Waymark", "Reminder check failed; WorkManager will try again", error)
        Result.retry()
    }
}

object DepartureWatch {

    private const val WORK_NAME = "waymark.departure-watch"

    fun schedule(context: Context) {
        // No constraints at all: this reads local storage and the clock, so
        // there is nothing to wait for, not a network, not a charger.
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
