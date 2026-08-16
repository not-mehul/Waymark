package com.waymark.alerts

import android.Manifest
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
import com.waymark.domain.model.DisruptionAlert

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    fun post(context: Context, alert: DisruptionAlert) {
        if (!granted(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.headline)
            .setContentText(alert.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(alert.segmentId.hashCode(), notification)
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

    /** Tapping the reminder opens the app rather than dismissing into nothing. */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
