package com.waymark.alerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.waymark.R
import com.waymark.domain.logic.LiveJourney

/**
 * The journey under way, as one ongoing notification: a countdown to
 * departure, then a bar that fills as the flight goes, gone on arrival.
 *
 * On Android 16 and later it asks to be promoted to a Live Update — pinned
 * at the top of the shade, on the lock screen, and as a chip in the status
 * bar carrying the time left. Earlier versions show the same notification,
 * ongoing, with an ordinary progress bar.
 */
object LiveUpdates {

    private const val CHANNEL = "waymark.underway"
    private const val NOTIFICATION_ID = 0x1173

    fun createChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Journey under way", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The flight or train you are on, from shortly before it leaves until it arrives"
                // It updates every quarter of an hour; it never needs to make a sound.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    // Checked by granted() first, and a revocation in between is caught below.
    @SuppressLint("MissingPermission")
    fun show(context: Context, journey: LiveJourney?) {
        val manager = NotificationManagerCompat.from(context)
        if (journey == null || !Notifications.granted(context)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val segment = journey.segment
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(LiveJourney.PROGRESS_MAX)))
            .setProgress(journey.progress)
            .setStyledByProgress(true)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(segment.title)
            .setContentText(journey.headline)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(journey.chip)
            // The system counts the time down between updates.
            .setWhen(journey.targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(Notifications.openBooking(context, segment.tripId, segment.id))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (denied: SecurityException) {
            // Revoked between the check and the post; the next check tries again.
        }
    }
}
