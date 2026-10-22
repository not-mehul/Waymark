package com.waymark.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What arrived from outside the app's own screens and is waiting to be acted
 * on: a reminder tapped in the notification shade, an email shared in, a file
 * opened with Waymark. The activity posts; the navigation graph takes.
 */
class Inbox {

    sealed interface Request {
        data class OpenBooking(val tripId: String, val segmentId: String) : Request

        /** Text shared from another app — usually a confirmation email. */
        data class SharedText(val text: String) : Request

        /** A trip or backup file opened with Waymark, or a scanned trip code. */
        data class ImportFile(val uri: String) : Request
        data class ImportCode(val payload: String) : Request
    }

    private val pending = MutableStateFlow<Request?>(null)

    val requests: StateFlow<Request?> = pending.asStateFlow()

    fun post(request: Request) {
        pending.value = request
    }

    /** Taken once: a rotation does not open the same booking twice. */
    fun take(): Request? = pending.value.also { pending.value = null }
}
