package com.waymark.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.waymark.MainActivity
import com.waymark.R
import com.waymark.WaymarkApplication
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment

/**
 * "Next up" on the home screen: the next booking on any trip that is not put
 * away, and how long until it. Tapping it opens that booking.
 *
 * Drawn with Glance, so the widget is written as composables rather than as a
 * RemoteViews layout patched by id. What it shows is kept in Glance's own
 * per-widget state: [refresh] works it out and writes it there, and the
 * composition only reads it, so a redraw while a Glance session is still open
 * shows the new booking rather than the one the session started with.
 */
class NextUpWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        write(context, id, NextUp.of(context, System.currentTimeMillis()))
        provideContent { Content(NextUp.read(currentState())) }
    }

    @Composable
    private fun Content(next: NextUp) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(14.dp)
                .clickable(actionStartActivity(next.intent(LocalContext.current))),
        ) {
            Text(
                text = next.eyebrow,
                style = TextStyle(color = ACCENT, fontSize = 11.sp),
                maxLines = 1,
            )
            Text(
                text = next.title,
                style = TextStyle(
                    color = TEXT,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = next.detail,
                style = TextStyle(color = DIM, fontSize = 13.sp),
                maxLines = 2,
            )
        }
    }

    companion object {
        /** Redraw every placed widget; a no-op when none is on the home screen. */
        suspend fun refresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(NextUpWidget::class.java)
            if (ids.isEmpty()) return
            val next = NextUp.of(context, nowMillis)
            val widget = NextUpWidget()
            ids.forEach { id ->
                write(context, id, next)
                widget.update(context, id)
            }
        }

        private suspend fun write(context: Context, id: GlanceId, next: NextUp) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                state.toMutablePreferences().apply { next.writeTo(this) }
            }
        }
    }
}

// The widget's ink, Dawn by day and Dusk by night, following the launcher.
private val TEXT = ColorProvider(day = Color(0xFF2A221B), night = Color(0xFFF5ECD9))
private val DIM = ColorProvider(day = Color(0xFF6E6255), night = Color(0xFFA89C8C))
private val ACCENT = ColorProvider(day = Color(0xFFA8763E), night = Color(0xFFD4A574))

/** The launcher's door to [NextUpWidget]. */
class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

/** What the widget says, as plain values that fit in its state. */
private data class NextUp(
    val eyebrow: String,
    val title: String,
    val detail: String,
    val tripId: String?,
    val segmentId: String?,
) {
    fun writeTo(state: MutablePreferences) {
        state[EYEBROW] = eyebrow
        state[TITLE] = title
        state[DETAIL] = detail
        if (tripId != null) state[TRIP] = tripId else state.remove(TRIP)
        if (segmentId != null) state[SEGMENT] = segmentId else state.remove(SEGMENT)
    }

    fun intent(context: Context): Intent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (tripId != null && segmentId != null) {
            intent.setAction(MainActivity.ACTION_OPEN_BOOKING)
                .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
                .putExtra(MainActivity.EXTRA_SEGMENT_ID, segmentId)
        }
        return intent
    }

    companion object {
        private val EYEBROW = stringPreferencesKey("eyebrow")
        private val TITLE = stringPreferencesKey("title")
        private val DETAIL = stringPreferencesKey("detail")
        private val TRIP = stringPreferencesKey("trip")
        private val SEGMENT = stringPreferencesKey("segment")

        private val NOTHING = NextUp(
            eyebrow = "Waymark",
            title = "Nothing coming up",
            detail = "Add a trip and its next booking shows here.",
            tripId = null,
            segmentId = null,
        )

        fun read(state: Preferences): NextUp = NextUp(
            eyebrow = state[EYEBROW] ?: NOTHING.eyebrow,
            title = state[TITLE] ?: NOTHING.title,
            detail = state[DETAIL] ?: NOTHING.detail,
            tripId = state[TRIP],
            segmentId = state[SEGMENT],
        )

        suspend fun of(context: Context, nowMillis: Long): NextUp {
            val container = (context.applicationContext as? WaymarkApplication)?.container ?: return NOTHING
            val (trip, segment) = container.tripRepository.nextAcrossTrips(nowMillis) ?: return NOTHING
            val under = nowMillis >= segment.startEpochMillis
            return NextUp(
                eyebrow = if (under) "Now · ${trip.name}" else "Next · ${trip.name}",
                title = segment.title,
                detail = detail(segment, nowMillis),
                tripId = trip.id,
                segmentId = segment.id,
            )
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
    }
}
