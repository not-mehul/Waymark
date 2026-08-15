package com.waymark.domain.model

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
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timeZoneId: String = "UTC",
    val address: String? = null,
) {
    val zone: ZoneId
        get() = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.of("UTC"))

    /** Short label for dense rows: the code when there is one, else the city. */
    val shortLabel: String
        get() = code ?: city.ifBlank { name }

    val hasCoordinates: Boolean
        get() = latitude != 0.0 || longitude != 0.0
}
