package com.waymark

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.ui.WaymarkApp
import com.waymark.ui.theme.WaymarkTheme
import java.util.Locale

/**
 * A plain [ComponentActivity].
 *
 * This was a `FragmentActivity` for one reason: `BiometricPrompt` requires
 * one. With the vault gone there is no prompt, no biometric dependency, and no
 * `androidx.fragment` on the classpath — which is worth being rid of on its own
 * terms, because the version biometric 1.1.0 dragged in predates the activity
 * library this app uses by several years.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readDeviceConventions()
        val container = (application as WaymarkApplication).container
        setContent {
            // First load honours the system setting; the toggle overrides it.
            WaymarkTheme {
                WaymarkApp(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Either can be changed in system settings while the app is backgrounded.
        readDeviceConventions()
    }

    /** The clock and the units the rest of the phone already uses. */
    private fun readDeviceConventions() {
        TimeText.use24Hour = DateFormat.is24HourFormat(this)
        Geo.metric = Locale.getDefault().country.uppercase() !in Geo.MILE_REGIONS
    }
}
