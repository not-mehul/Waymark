package com.waymark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.local.ThemeStore
import com.waymark.di.Inbox
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.ui.WaymarkApp
import com.waymark.ui.theme.WaymarkTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The one activity. Besides hosting the app, it is the door for everything
 * that arrives from outside: a reminder or the widget tapped, an email shared
 * in, a trip file opened. Each becomes a request in the [Inbox] for the
 * navigation graph to act on.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    @Inject
    lateinit var inbox: Inbox

    /** Becomes true on the first frame drawn in the stored theme. */
    @Volatile
    private var drawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash stays up until the stored theme has been read,
        // so the first frame is never the other one.
        installSplashScreen().setKeepOnScreenCondition { !drawn }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readDeviceConventions()
        // Only on a fresh start: a rotation re-delivers the original intent.
        if (savedInstanceState == null) receive(intent)
        setContent {
            val stored by themeStore.mode.collectAsStateWithLifecycle(initialValue = null)
            val mode = stored ?: return@setContent
            val scope = rememberCoroutineScope()
            SideEffect { drawn = true }
            WaymarkTheme(stored = mode, onModeChange = { scope.launch { themeStore.set(it) } }) {
                WaymarkApp(inbox = inbox)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    override fun onResume() {
        super.onResume()
        // Either can be changed in system settings while the app is backgrounded.
        readDeviceConventions()
    }

    private fun receive(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_BOOKING -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: return
                val segmentId = intent.getStringExtra(EXTRA_SEGMENT_ID) ?: return
                inbox.post(Inbox.Request.OpenBooking(tripId, segmentId))
            }

            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> inbox.post(Inbox.Request.ImportFile(stream.toString()))
                    !text.isNullOrBlank() -> inbox.post(Inbox.Request.SharedText(text))
                }
            }

            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == SHARE_SCHEME) {
                    data.getQueryParameter("d")?.let { inbox.post(Inbox.Request.ImportCode(it)) }
                } else {
                    inbox.post(Inbox.Request.ImportFile(data.toString()))
                }
            }
        }
    }

    /** The clock and the units the rest of the phone already uses. */
    private fun readDeviceConventions() {
        TimeText.use24Hour = DateFormat.is24HourFormat(this)
        Geo.metric = Locale.getDefault().country.uppercase() !in Geo.MILE_REGIONS
    }

    companion object {
        const val ACTION_OPEN_BOOKING = "com.waymark.action.OPEN_BOOKING"
        const val EXTRA_TRIP_ID = "com.waymark.extra.TRIP_ID"
        const val EXTRA_SEGMENT_ID = "com.waymark.extra.SEGMENT_ID"

        /** `waymark://import?d=…` — what a trip's QR code carries. */
        const val SHARE_SCHEME = "waymark"
    }
}
