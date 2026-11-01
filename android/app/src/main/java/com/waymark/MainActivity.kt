package com.waymark

import android.content.Intent
import android.os.Bundle
import com.getcapacitor.BridgeActivity

/**
 * The one activity: Capacitor's WebView host for the Svelte app. Besides
 * hosting it, it is the door for everything that arrives from outside (a
 * reminder or the widget tapped, an email shared in, a trip file or a trip
 * code opened), which [WaymarkPlugin] hands to the page.
 */
class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(WaymarkPlugin::class.java)
        // A restored instance already handled its launch intent; don't deliver
        // it to the page a second time.
        if (savedInstanceState != null) intent = Intent(Intent.ACTION_MAIN)
        super.onCreate(savedInstanceState)
    }

    companion object {
        // The Kotlin app's, so a reminder it posted before the upgrade still opens its booking.
        const val ACTION_OPEN_BOOKING = "com.waymark.action.OPEN_BOOKING"
        const val EXTRA_TRIP_ID = "com.waymark.extra.TRIP_ID"
        const val EXTRA_SEGMENT_ID = "com.waymark.extra.SEGMENT_ID"

        /** `waymark://import?d=…`: what a trip's QR code carries. */
        const val SHARE_SCHEME = "waymark"
    }
}
