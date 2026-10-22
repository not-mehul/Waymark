package com.waymark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.waymark.MainActivity
import com.waymark.R
import com.waymark.WaymarkApplication
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Next up" on the home screen: the next booking on any trip that is not put
 * away, and how long until it. Tapping it opens that booking.
 */
class NextUpWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Redraw every placed widget; a no-op when none is on the home screen. */
        suspend fun refresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextUpWidget::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as? WaymarkApplication)?.container ?: return
            val next = container.tripRepository.nextAcrossTrips(nowMillis)
            val views = RemoteViews(context.packageName, R.layout.widget_next_up)

            if (next == null) {
                views.setTextViewText(R.id.widget_eyebrow, "Waymark")
                views.setTextViewText(R.id.widget_title, "Nothing coming up")
                views.setTextViewText(R.id.widget_detail, "Add a trip and its next booking shows here.")
                views.setOnClickPendingIntent(R.id.widget_root, open(context, null, null))
            } else {
                val (trip, segment) = next
                val under = nowMillis >= segment.startEpochMillis
                views.setTextViewText(
                    R.id.widget_eyebrow,
                    if (under) "Now · ${trip.name}" else "Next · ${trip.name}",
                )
                views.setTextViewText(R.id.widget_title, segment.title)
                views.setTextViewText(R.id.widget_detail, detail(segment, nowMillis))
                views.setOnClickPendingIntent(R.id.widget_root, open(context, trip.id, segment.id))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun detail(segment: Segment, nowMillis: Long): String = buildString {
            append(TimeText.dayCompact(segment.start.toLocalDate()))
            append(", ")
            append(TimeText.clock(segment.start))
            append(" · ")
            append(segment.origin.shortLabel)
            if (nowMillis < segment.startEpochMillis) {
                append(" · ")
                append(TimeText.relative(segment.startEpochMillis, nowMillis))
            }
        }

        private fun open(context: Context, tripId: String?, segmentId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (tripId != null && segmentId != null) {
                intent.setAction(MainActivity.ACTION_OPEN_BOOKING)
                    .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
                    .putExtra(MainActivity.EXTRA_SEGMENT_ID, segmentId)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val REQUEST_CODE = 4_104
    }
}
