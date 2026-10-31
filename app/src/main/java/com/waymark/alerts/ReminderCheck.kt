package com.waymark.alerts

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.waymark.di.Graph
import com.waymark.di.graph
import com.waymark.domain.logic.LiveJourney
import com.waymark.domain.logic.ReminderPlanner
import com.waymark.domain.model.RaisedReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one routine that raises reminders, wherever it is woken from: the alarm
 * set for the next one, the periodic safety-net worker, or a booking being
 * saved.
 *
 * It raises what is due, posts what Android will show, and only then records a
 * reminder as posted — so one that fell due while notifications were blocked is
 * still shown once they are allowed, if its booking has not started. It
 * brings the journey under way, if there is one, up to date. Then it sets a
 * single alarm for the next reminder or redraw anywhere in the app, which is what
 * makes them punctual: a periodic worker can be deferred by Doze past the very
 * departure it was meant to announce.
 */
object ReminderCheck {

    private val lock = Mutex()

    suspend fun run(context: Context, graph: Graph, nowMillis: Long = System.currentTimeMillis()) =
        lock.withLock {
            val itinerary = graph.tripRepository().reminderItinerary(nowMillis)
            val preferences = graph.reminderStore().current()
            val canPost = Notifications.granted(context)

            ReminderPlanner.due(itinerary, preferences, nowMillis).forEach { due ->
                val raised = graph.alertRepository().raise(
                    RaisedReminder(
                        signature = due.signature,
                        segmentId = due.segmentId,
                        label = due.label,
                        headline = due.headline,
                        detail = due.detail,
                        startEpochMillis = due.startEpochMillis,
                        raisedAtMillis = nowMillis,
                    ),
                    nowMillis,
                )
                if (!raised.posted && canPost &&
                    Notifications.post(context, raised.copy(headline = due.headline), due.tripId)
                ) {
                    graph.alertRepository().markPosted(raised.signature)
                }
            }

            LiveUpdates.show(context, LiveJourney.of(itinerary, preferences, nowMillis))

            // One alarm for whichever comes first: the next reminder, or the
            // next redraw of the journey under way.
            val next = listOfNotNull(
                ReminderPlanner.nextDueAt(itinerary, preferences, nowMillis),
                LiveJourney.nextChangeAt(itinerary, preferences, nowMillis),
            ).minOrNull()
            ReminderAlarm.set(context, next)
        }
}

/** The single alarm for the next reminder due anywhere in the app. */
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
        val graph = context.graph
        val pending = goAsync()
        scope.launch {
            try {
                ReminderCheck.run(context.applicationContext, graph)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
