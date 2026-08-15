package com.waymark

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.waymark.ui.WaymarkApp
import com.waymark.ui.theme.WaymarkTheme

/**
 * A [FragmentActivity] rather than a plain ComponentActivity because
 * BiometricPrompt — the gate in front of the vault — needs one.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WaymarkApplication).container
        setContent {
            // First load honours the system setting; the toggle overrides it.
            WaymarkTheme {
                WaymarkApp(container = container)
            }
        }
    }
}
