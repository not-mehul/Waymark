package com.waymark.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
import org.json.JSONArray
import org.json.JSONException

/**
 * "Next up" on the home screen: the next booking on any trip that is not put
 * away, and how long until it. Tapping a booking opens it.
 *
 * Three shapes, chosen by the space the launcher gives it: a small one with
 * the booking and the time left, the wide one with its day, time and place,
 * and a tall one that also lists the two bookings after it. On Android 15
 * and later the widget picker shows it drawn, not as a placeholder.
 *
 * The page hands over the next dozen bookings whenever a trip changes
 * ([save]); the widget picks what is current from them each time it draws, so
 * "in 3h" stays true between visits to the app. What it shows is kept in
 * Glance's own per-widget state and the composition only reads it, so a
 * redraw while a Glance session is still open shows the new booking rather
 * than the one the session started with.
 *
 * The same class names as the Kotlin app's widget, so one already on the home
 * screen keeps working after the upgrade.
 */
class NextUpWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(SIZES)

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        write(context, id, NextUp.of(entries(context), System.currentTimeMillis()))
        provideContent { Content(read(currentState())) }
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
        val open = actionStartActivity(intent(LocalContext.current, next))
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
        val open = actionStartActivity(intent(LocalContext.current, next))
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
                .clickable(actionStartActivity(intent(LocalContext.current, next))),
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

        private const val ENTRIES_PREFERENCES = "waymark.widget.entries"
        private const val ENTRIES = "entries"
        private const val PREVIEW_PREFERENCES = "waymark.widget"
        private const val PREVIEW_VERSION = "preview_version"

        /** Keeps the bookings the page sent, then redraws every placed widget. */
        suspend fun save(context: Context, entries: JSONArray) {
            context.getSharedPreferences(ENTRIES_PREFERENCES, Context.MODE_PRIVATE)
                .edit { putString(ENTRIES, entries.toString()) }
            refresh(context)
        }

        /** Redraw every placed widget; a no-op when none is on the home screen. */
        suspend fun refresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(NextUpWidget::class.java)
            if (ids.isEmpty()) return
            val next = NextUp.of(entries(context), nowMillis)
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

        private fun entries(context: Context): List<WidgetEntry> {
            val text = context.getSharedPreferences(ENTRIES_PREFERENCES, Context.MODE_PRIVATE)
                .getString(ENTRIES, null) ?: return emptyList()
            return try {
                WidgetEntry.listFromJson(JSONArray(text))
            } catch (malformed: JSONException) {
                Log.w("Waymark", "Dropping the widget's unreadable bookings", malformed)
                emptyList()
            }
        }

        private fun intent(context: Context, next: NextUp): Intent {
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (next.tripId != null && next.segmentId != null) {
                intent.setAction(MainActivity.ACTION_OPEN_BOOKING)
                    .putExtra(MainActivity.EXTRA_TRIP_ID, next.tripId)
                    .putExtra(MainActivity.EXTRA_SEGMENT_ID, next.segmentId)
            }
            return intent
        }

        private val COUNT = intPreferencesKey("count")
        private fun key(name: String, index: Int) = stringPreferencesKey("$name.$index")

        private suspend fun write(context: Context, id: GlanceId, next: List<NextUp>) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                state.toMutablePreferences().apply { write(next, this) }
            }
        }

        private fun write(next: List<NextUp>, state: MutablePreferences) {
            state[COUNT] = next.size
            for (index in 0 until NextUp.SHOWN) {
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

        private fun read(state: Preferences): List<NextUp> {
            val count = state[COUNT] ?: 0
            return (0 until count.coerceAtMost(NextUp.SHOWN)).mapNotNull { index ->
                NextUp(
                    eyebrow = state[key("eyebrow", index)] ?: return@mapNotNull null,
                    title = state[key("title", index)] ?: return@mapNotNull null,
                    detail = state[key("detail", index)].orEmpty(),
                    soon = state[key("soon", index)].orEmpty(),
                    tripId = state[key("trip", index)],
                    segmentId = state[key("segment", index)],
                )
            }.ifEmpty { listOf(NextUp.NOTHING) }
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
