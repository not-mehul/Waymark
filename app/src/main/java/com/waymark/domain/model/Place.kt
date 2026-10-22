package com.waymark.domain.model

import com.waymark.domain.logic.LatLon
import java.io.Serializable
import java.time.ZoneId

/**
 * A point on the earth with enough context to render it, sort it into a local
 * day, and estimate travel to or from it.
 *
 * [code] is the IATA station code for airports and stations, null for anything
 * that has no code (a hotel, a restaurant, a trailhead).
 */
data class Place(
    val name: String,
    val code: String? = null,
    val city: String = "",
    val country: String = "",
    /** Null when nobody knows where it is — a hotel typed by name, say. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeZoneId: String = "UTC",
    val address: String? = null,
) : Serializable {
    val zone: ZoneId
        get() = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.of("UTC"))

    /** Short label for dense rows: the code when there is one, else the city. */
    val shortLabel: String
        get() = code ?: city.ifBlank { name }

    val position: LatLon?
        get() = if (latitude != null && longitude != null) LatLon(latitude, longitude) else null

    val hasCoordinates: Boolean get() = position != null

    private companion object {
        /** Saved in screen state across process death; bump if the fields change shape. */
        private const val serialVersionUID = 1L
    }
}
