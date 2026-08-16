package com.waymark.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.waymark.alerts.Notifications

/**
 * Permission to post the one notification Waymark sends.
 *
 * The manifest has declared `POST_NOTIFICATIONS` since the departure reminder
 * was written, and [Notifications] has always checked it before posting — but
 * nothing ever asked for it. On Android 13 and later a declared permission is
 * denied until the user grants it, so the reminder was being composed, written
 * to the alert table as raised, and then dropped on the floor. Every install on
 * a modern phone had the feature quietly switched off.
 *
 * Asking is in context: the request goes up when a traveler saves their first
 * flight, which is the moment a reminder about a departure starts to mean
 * anything. Android shows the system dialog twice at most and then stops
 * asking, so a second attempt after a refusal opens the app's own settings page
 * rather than launching a request that would return denied without any UI.
 */
@Stable
class ReminderPermission internal constructor(
    /** True when the app may post — always true below Android 13. */
    val granted: Boolean,
    private val onRequest: () -> Unit,
) {
    /** Ask, or send the traveler where the switch lives if asking is spent. */
    fun request() = onRequest()
}

@Composable
fun rememberReminderPermission(): ReminderPermission {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(Notifications.granted(context)) }
    var refused by remember { mutableStateOf(false) }

    // The switch can be moved from outside the app, so the answer is re-read
    // whenever the app comes back to the foreground rather than cached for the
    // lifetime of the composition.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = Notifications.granted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        granted = allowed
        refused = !allowed
    }

    return ReminderPermission(granted = granted) {
        when {
            granted -> Unit
            refused || !Notifications.needsRequest(context) -> {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
