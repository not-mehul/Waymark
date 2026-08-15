package com.waymark.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.waymark.R
import com.waymark.domain.model.DisruptionAlert

/**
 * Disruption notices. One channel for schedule changes that cost the traveler
 * something, one for the quieter "your gate is now B14".
 */
object Notifications {

    private const val CHANNEL_DISRUPTION = "waymark.disruption"
    private const val CHANNEL_UPDATES = "waymark.updates"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DISRUPTION,
                "Disruption",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Cancellations and delays that change your day"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                "Flight updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Gate changes and boarding"
            }
        )
    }

    fun post(context: Context, alert: DisruptionAlert) {
        if (!canPost(context)) return
        val channel = when (alert.severity) {
            DisruptionAlert.Severity.CRITICAL, DisruptionAlert.Severity.WARNING -> CHANNEL_DISRUPTION
            DisruptionAlert.Severity.NOTICE -> CHANNEL_UPDATES
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.headline)
            .setContentText(alert.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail))
            .setPriority(
                if (alert.severity == DisruptionAlert.Severity.CRITICAL) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(alert.segmentId.hashCode(), notification)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
