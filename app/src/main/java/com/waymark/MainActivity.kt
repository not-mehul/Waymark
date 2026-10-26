package com.waymark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.waymark.di.Inbox
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.ui.WaymarkApp
import com.waymark.ui.theme.WaymarkTheme
import java.util.Locale

/**
 * The one activity. Besides hosting the app, it is the door for everything
 * that arrives from outside: a reminder or the widget tapped, an email shared
 * in, a trip file opened. Each becomes a request in the [Inbox] for the
 * navigation graph to act on.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readDeviceConventions()
        val container = (application as WaymarkApplication).container
        // Only on a fresh start: a rotation re-delivers the original intent.
        if (savedInstanceState == null) receive(intent)
        setContent {
            WaymarkTheme(store = container.themeStore) {
                WaymarkApp(container = container)
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
        val inbox = (application as WaymarkApplication).container.inbox
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
