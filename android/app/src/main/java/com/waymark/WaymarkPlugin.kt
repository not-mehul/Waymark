package com.waymark

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.IntentCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.WebViewListener
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.waymark.alerts.Alerts
import com.waymark.alerts.DepartureWatch
import com.waymark.alerts.Notifications
import com.waymark.alerts.Schedule
import com.waymark.share.Barcodes
import com.waymark.share.Exports
import com.waymark.widget.NextUpWidget
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Waymark's native features, exposed to the page as the `Waymark` Capacitor
 * plugin (web side: src/platform/native.ts and src/state/shell.svelte.ts).
 *
 * Every method resolves `{ value }`, so the page's `callNative(name, params)`
 * stays generic. Events, each kept until the page subscribes so nothing from a
 * cold start is lost: `back`, `openBooking` (a reminder or the widget),
 * `sharedText` (an email), `openFile` (a trip file or a backup) and
 * `openCode` (a trip's QR code, read by the camera app).
 */
@CapacitorPlugin(
    name = "Waymark",
    permissions = [Permission(alias = WaymarkPlugin.NOTIFICATIONS, strings = [Manifest.permission.POST_NOTIFICATIONS])],
)
class WaymarkPlugin : Plugin() {

    private val scope = MainScope()

    // Enabled only while the page has something to dismiss or go back from.
    // Otherwise the system owns Back, which keeps the predictive
    // back-to-home animation working.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = notifyListeners("back", JSObject())
    }

    override fun load() {
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        bridge.addWebViewListener(object : WebViewListener() {
            override fun onRenderProcessGone(webView: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The renderer crashed or was killed for memory. The WebView is
                // unusable now; rebuild the activity instead of crashing the app.
                activity.recreate()
                return true
            }
        })
        Notifications.createChannels(context)
        DepartureWatch.schedule(context)
        scope.launch { NextUpWidget.publishPreview(context) }
        handleIntent(activity.intent)
    }

    override fun handleOnNewIntent(intent: Intent) {
        super.handleOnNewIntent(intent)
        handleIntent(intent)
    }

    override fun handleOnDestroy() {
        scope.cancel()
        super.handleOnDestroy()
    }

    // ── From outside ────────────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            MainActivity.ACTION_OPEN_BOOKING -> {
                val tripId = intent.getStringExtra(MainActivity.EXTRA_TRIP_ID) ?: return
                val segmentId = intent.getStringExtra(MainActivity.EXTRA_SEGMENT_ID) ?: return
                emit("openBooking", JSObject().put("tripId", tripId).put("segmentId", segmentId))
            }

            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> openFile(stream)
                    !text.isNullOrBlank() -> emit("sharedText", JSObject().put("text", text.take(MAX_SHARED_CHARS)))
                }
            }

            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == MainActivity.SHARE_SCHEME) {
                    data.getQueryParameter("d")?.let { emit("openCode", JSObject().put("code", it)) }
                } else {
                    openFile(data)
                }
            }
        }
    }

    /** A trip file or a backup, read here because the page cannot open a content URI. */
    private fun openFile(uri: Uri) {
        scope.launch {
            val event = try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                if (text == null) {
                    JSObject().put("problem", "It is too large to be a Waymark trip.")
                } else {
                    JSObject().put("text", text)
                }
            } catch (e: IOException) {
                JSObject().put("problem", "It may have moved, or no longer be there. (${e.message})")
            } catch (e: SecurityException) {
                JSObject().put("problem", "Waymark was not allowed to read it. (${e.message})")
            }
            emit("openFile", event)
        }
    }

    /** The file's text, or null when it is larger than any trip could be. */
    private fun readText(uri: Uri): String? {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Nothing to read")
        return stream.use { input ->
            val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) null else bytes.toString(Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun emit(name: String, data: JSObject) = notifyListeners(name, data, true)

    // ── Calls from the page ─────────────────────────────────────────

    /** Run [block] and resolve the call with `{ value }`. */
    private fun respond(call: PluginCall, block: suspend () -> Any?) {
        scope.launch {
            try {
                val result = JSObject()
                block()?.let { result.put("value", it) }
                call.resolve(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                call.reject(e.message ?: e.toString())
            }
        }
    }

    private fun PluginCall.require(key: String): String =
        getString(key) ?: throw IllegalArgumentException("Missing \"$key\"")

    /**
     * The page has drawn the shelf: the moment the app is usable, which
     * Android reports as "Fully drawn" and the cold start check in CI times.
     */
    @PluginMethod
    fun ready(call: PluginCall) = respond(call) {
        activity.reportFullyDrawn()
        null
    }

    @PluginMethod
    fun setBackHandler(call: PluginCall) = respond(call) {
        backCallback.isEnabled = call.getBoolean("enabled", false) == true
        null
    }

    // The Kotlin app's trips, once.

    @PluginMethod
    fun readHandover(call: PluginCall) = respond(call) { withContext(Dispatchers.IO) { Handover.read(context) } }

    @PluginMethod
    fun removeHandover(call: PluginCall) = respond(call) {
        withContext(Dispatchers.IO) { Handover.remove(context) }
        true
    }

    @PluginMethod
    fun handoverStranded(call: PluginCall) = respond(call) {
        withContext(Dispatchers.IO) { Handover.stranded(context) }
    }

    // Codes, files and printing.

    @PluginMethod
    fun readCode(call: PluginCall) = respond(call) {
        val image = call.require("image")
        withContext(Dispatchers.Default) { Barcodes.read(Base64.decode(image, Base64.DEFAULT)) }
    }

    @PluginMethod
    fun shareFile(call: PluginCall) = respond(call) {
        Exports.share(
            activity,
            name = call.require("name"),
            text = call.require("text"),
            mime = call.getString("type")?.ifEmpty { null } ?: "application/octet-stream",
            title = call.getString("title") ?: call.require("name"),
        )
        true
    }

    @PluginMethod
    fun print(call: PluginCall) = respond(call) {
        suspendCancellableCoroutine { done ->
            Exports.print(activity, bridge.webView, call.getString("name") ?: "Waymark") {
                if (done.isActive) done.resume(true)
            }
        }
    }

    // Reminders, the journey under way, and the widget.

    @PluginMethod
    fun schedule(call: PluginCall) = respond(call) {
        val schedule = Schedule.fromJson(call.data)
        withContext(Dispatchers.IO) { Alerts.update(context, schedule) }
        schedule.reminders.size
    }

    @PluginMethod
    fun updateWidget(call: PluginCall) = respond(call) {
        val entries = call.data.optJSONArray("entries") ?: JSONArray()
        NextUpWidget.save(context, entries)
        true
    }

    /** True if reminders may be posted, asking on Android 13 and later if they may not yet. */
    @PluginMethod
    fun requestNotifications(call: PluginCall) {
        when {
            Notifications.granted(context) -> respond(call) { true }

            // Below Android 13 there is no prompt: they are off in system settings.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> respond(call) { false }

            else -> requestPermissionForAlias(NOTIFICATIONS, call, "onNotificationPermission")
        }
    }

    @PermissionCallback
    fun onNotificationPermission(call: PluginCall) = respond(call) {
        val granted = Notifications.granted(context)
        // Anything that fell due while they were off is posted now.
        if (granted) withContext(Dispatchers.IO) { Alerts.check(context) }
        granted
    }

    companion object {
        const val NOTIFICATIONS = "notifications"

        /** An email is kilobytes; cap absurdly large shares. */
        private const val MAX_SHARED_CHARS = 200_000

        /** As MAX_FILE_BYTES in src/share/files.ts: larger than any real archive by far. */
        private const val MAX_FILE_BYTES = 16 * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
    }
}
