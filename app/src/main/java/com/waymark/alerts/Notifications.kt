package com.waymark.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.waymark.MainActivity
import com.waymark.R
import com.waymark.domain.model.RaisedReminder

/**
 * The app's whole notification surface: one channel, one kind of message.
 *
 * There were two channels once — "Disruption" for cancellations and "Flight
 * updates" for gate changes — from a version of Waymark that watched a
 * schedule feed. It does not watch anything now, so a traveler scrolling their
 * notification settings would have found two switches for a feature that
 * cannot happen and none for the reminder that can. What is left is honest:
 * something you booked is about to start.
 *
 * One channel rather than one per category, because Android's channel switches
 * and Waymark's own lead times would then be two places to turn the same thing
 * off, and the one inside the app is the one that can say "three hours".
 */
object Notifications {

    /**
     * The channel id deliberately does not reuse either old one. A channel's
     * name and importance are fixed once created — the platform will not let an
     * app quietly rewrite them — so an install upgrading from an earlier build
     * gets the new channel rather than the old label with new copy behind it.
     */
    private const val CHANNEL_DEPARTURES = "waymark.departures"

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEPARTURES,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Before a booking you have entered starts"
            }
        )

        // Retire the channels of earlier builds so an upgrade does not leave
        // two dead switches behind in the system settings.
        listOf("waymark.disruption", "waymark.updates").forEach(manager::deleteNotificationChannel)
    }

    /**
     * Show one reminder. False when Android would not show it — notifications
     * are blocked — so the caller can leave it unposted and try again later.
     * Each reminder has its own notification, keyed on its signature, so a
     * flight's check-in reminder and its departure reminder do not replace
     * each other.
     */
    // Checked by granted() first, and a revocation in between is caught below.
    @SuppressLint("MissingPermission")
    fun post(context: Context, reminder: RaisedReminder, tripId: String): Boolean {
        if (!granted(context)) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.headline)
            .setContentText(reminder.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(reminder.startEpochMillis)
            .setShowWhen(true)
            .setContentIntent(openBooking(context, tripId, reminder.segmentId))
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(reminder.signature.hashCode(), notification)
            true
        } catch (denied: SecurityException) {
            // Revoked between the check and the post.
            false
        }
    }

    /**
     * True when the app may post. Below Android 13 posting needs no permission,
     * so the question does not arise.
     */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether the platform will show a request at all, or has stopped asking. */
    fun needsRequest(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context)

    /** Tapping a reminder opens the booking it is about, inside its trip. */
    private fun openBooking(context: Context, tripId: String, segmentId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_BOOKING)
            .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
            .putExtra(MainActivity.EXTRA_SEGMENT_ID, segmentId)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            segmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
