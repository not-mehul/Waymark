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

/**
 * The app's reminders: one channel, one kind of message. Something you booked
 * is about to start. The journey under way has a channel of its own, in
 * [LiveUpdates].
 *
 * The channel ids are the Kotlin app's, so whatever the traveler set for them
 * in system settings (silent, off, a sound of their own) carries over the
 * upgrade.
 */
object Notifications {

    private const val CHANNEL_DEPARTURES = "waymark.departures"

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEPARTURES,
                context.getString(R.string.reminders_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminders_channel_description)
            }
        )
        LiveUpdates.createChannel(context)
    }

    /** True when the app may post. Below Android 13 posting needs no permission. */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Show one reminder. False when Android would not show it, so the caller
     * can leave it unposted and try again later. Each reminder has its own
     * notification, keyed on its signature, so a flight's check-in reminder
     * and its departure reminder do not replace each other.
     */
    // Checked by granted() first, and a revocation in between is caught below.
    @SuppressLint("MissingPermission")
    fun post(context: Context, reminder: ScheduledReminder): Boolean {
        if (!granted(context)) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.headline)
            .setContentText(reminder.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(reminder.startMillis)
            .setShowWhen(true)
            .setContentIntent(openBooking(context, reminder.tripId, reminder.segmentId))
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

    /** Tapping a reminder opens the booking it is about, inside its trip. */
    fun openBooking(context: Context, tripId: String, segmentId: String): PendingIntent {
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
