package com.waymark.data.remote

import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment

/**
 * Where live flight state comes from. Implementations are ordered by the
 * repository: the first one that answers wins, and the offline model always
 * answers, so the timeline is never blank.
 */
interface FlightStatusProvider {

    /** Shown to the traveler next to the data, because provenance matters. */
    val sourceName: String

    /** False when the provider needs a network or a key it does not have. */
    suspend fun isAvailable(): Boolean

    /** Null when this provider has nothing to say about the flight. */
    suspend fun fetch(flight: Segment.Flight): FlightStatus?
}
