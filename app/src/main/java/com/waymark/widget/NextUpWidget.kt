package com.waymark.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.waymark.BuildConfig
import com.waymark.MainActivity
import com.waymark.R
import com.waymark.di.graph
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment

/**
 * "Next up" on the home screen: the next booking on any trip that is not put
 * away, and how long until it. Tapping a booking opens it.
 *
 * Three shapes, chosen by the space the launcher gives it: a small one with
 * the booking and the time left, the wide one with its day, time and place,
 * and a tall one that also lists the two bookings after it. On Android 15
 * and later the widget picker shows it drawn, not as a placeholder.
 *
 * Drawn with Glance, so the widget is written as composables rather than as a
 * RemoteViews layout patched by id. What it shows is kept in Glance's own
 * per-widget state: [refresh] works it out and writes it there, and the
 * composition only reads it, so a redraw while a Glance session is still open
 * shows the new booking rather than the one the session started with.
 */
class NextUpWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(SIZES)

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        write(context, id, NextUp.of(context, System.currentTimeMillis()))
        provideContent { Content(NextUp.read(currentState())) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Content(NextUp.PREVIEW) }
    }

    @Composable
    private fun Content(next: List<NextUp>) {
        val size = LocalSize.current
        val first = next.firstOrNull() ?: NextUp.NOTHING
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(if (size.height < WIDE.height) 10.dp else 14.dp),
        ) {
            when {
                size.height >= TALL.height -> {
                    Wide(first)
                    val then = next.drop(1)
                    if (then.isNotEmpty()) {
                        Spacer(GlanceModifier.height(10.dp))
                        Text(text = "Then", style = TextStyle(color = ACCENT, fontSize = 11.sp), maxLines = 1)
                        then.forEach { Then(it) }
                    }
                }

                size.width >= WIDE.width && size.height >= WIDE.height -> Wide(first)

                else -> Small(first)
            }
        }
    }

    @Composable
    private fun Small(next: NextUp) {
        val open = actionStartActivity(next.intent(LocalContext.current))
        Column(modifier = GlanceModifier.fillMaxSize().clickable(open)) {
            Text(
                text = next.title,
                style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(text = next.soon, style = TextStyle(color = DIM, fontSize = 12.sp), maxLines = 1)
        }
    }

    @Composable
    private fun Wide(next: NextUp) {
        val open = actionStartActivity(next.intent(LocalContext.current))
        Column(modifier = GlanceModifier.fillMaxWidth().clickable(open)) {
            Text(text = next.eyebrow, style = TextStyle(color = ACCENT, fontSize = 11.sp), maxLines = 1)
            Text(
                text = next.title,
                style = TextStyle(color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(text = next.detail, style = TextStyle(color = DIM, fontSize = 13.sp), maxLines = 2)
        }
    }

    @Composable
    private fun Then(next: NextUp) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(actionStartActivity(next.intent(LocalContext.current))),
        ) {
            Text(
                text = next.title,
                style = TextStyle(color = TEXT, fontSize = 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(text = next.soon, style = TextStyle(color = DIM, fontSize = 12.sp), maxLines = 1)
        }
    }

    companion object {
        /** A small widget, a wide one, and a tall one listing what follows. */
        private val SMALL = DpSize(110.dp, 40.dp)
        private val WIDE = DpSize(220.dp, 70.dp)
        private val TALL = DpSize(220.dp, 150.dp)
        private val SIZES = setOf(SMALL, WIDE, TALL)

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

        /**
         * Give the widget picker a drawn preview, on Android 15 and later. The
         * platform limits how often an app may, so it is done once a version.
         */
        suspend fun publishPreview(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
            val published = context.getSharedPreferences(PREVIEW_PREFERENCES, Context.MODE_PRIVATE)
            if (published.getInt(PREVIEW_VERSION, -1) == BuildConfig.VERSION_CODE) return
            runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(NextUpWidgetReceiver::class) }
                .onSuccess { published.edit { putInt(PREVIEW_VERSION, BuildConfig.VERSION_CODE) } }
        }

        private const val PREVIEW_PREFERENCES = "waymark.widget"
        private const val PREVIEW_VERSION = "preview_version"

        private suspend fun write(context: Context, id: GlanceId, next: List<NextUp>) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                state.toMutablePreferences().apply { NextUp.write(next, this) }
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

/** One booking as the widget says it, as plain values that fit in its state. */
private data class NextUp(
    val eyebrow: String,
    val title: String,
    val detail: String,
    /** "in 3h", "now", or a day and time when it is further off. */
    val soon: String,
    val tripId: String?,
    val segmentId: String?,
) {
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
        /** The first booking and the two after it, for the tall widget. */
        private const val SHOWN = 3

        private val COUNT = intPreferencesKey("count")
        private fun key(name: String, index: Int) = stringPreferencesKey("$name.$index")

        val NOTHING = NextUp(
            eyebrow = "Waymark",
            title = "Nothing coming up",
            detail = "Add a trip and its next booking shows here.",
            soon = "Nothing coming up",
            tripId = null,
            segmentId = null,
        )

        /** What the widget picker shows: a booking of the kind the widget is for. */
        val PREVIEW = listOf(
            NextUp(
                eyebrow = "Next · Lisbon",
                title = "TP 1351 · LHR → LIS",
                detail = "Thu 14 May, 09:40 · London Heathrow · in 3h",
                soon = "in 3h",
                tripId = null,
                segmentId = null,
            ),
            NextUp("", "Memmo Alfama", "", "Thu 15:00", null, null),
            NextUp("", "Tram 28 · Graça → Estrela", "", "Fri 10:30", null, null),
        )

        fun write(next: List<NextUp>, state: MutablePreferences) {
            state[COUNT] = next.size
            for (index in 0 until SHOWN) {
                val item = next.getOrNull(index)
                fun put(name: String, value: String?) {
                    if (value != null) state[key(name, index)] = value else state.remove(key(name, index))
                }
                put("eyebrow", item?.eyebrow)
                put("title", item?.title)
                put("detail", item?.detail)
                put("soon", item?.soon)
                put("trip", item?.tripId)
                put("segment", item?.segmentId)
            }
        }

        fun read(state: Preferences): List<NextUp> {
            val count = state[COUNT] ?: 0
            return (0 until count.coerceAtMost(SHOWN)).mapNotNull { index ->
                NextUp(
                    eyebrow = state[key("eyebrow", index)] ?: return@mapNotNull null,
                    title = state[key("title", index)] ?: return@mapNotNull null,
                    detail = state[key("detail", index)].orEmpty(),
                    soon = state[key("soon", index)].orEmpty(),
                    tripId = state[key("trip", index)],
                    segmentId = state[key("segment", index)],
                )
            }.ifEmpty { listOf(NOTHING) }
        }

        suspend fun of(context: Context, nowMillis: Long): List<NextUp> {
            val upcoming = context.graph.tripRepository().nextAcrossTrips(nowMillis, limit = SHOWN)
            if (upcoming.isEmpty()) return listOf(NOTHING)
            return upcoming.map { (trip, segment) ->
                val under = nowMillis >= segment.startEpochMillis
                NextUp(
                    eyebrow = if (under) "Now · ${trip.name}" else "Next · ${trip.name}",
                    title = segment.title,
                    detail = detail(segment, nowMillis),
                    soon = soon(segment, nowMillis),
                    tripId = trip.id,
                    segmentId = segment.id,
                )
            }
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

        /** Within a day, how long; further off, the day and the time. */
        private fun soon(segment: Segment, nowMillis: Long): String =
            if (segment.startEpochMillis - nowMillis < DAY_MILLIS) {
                TimeText.relative(segment.startEpochMillis, nowMillis)
            } else {
                "${TimeText.dayCompact(segment.start.toLocalDate())} ${TimeText.clock(segment.start)}"
            }

        private const val DAY_MILLIS = 24 * 60 * 60_000L
    }
}
