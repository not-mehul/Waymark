package com.waymark.domain.logic

import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.chronological

/**
 * Where the trip is at a given moment, read from the bookings around it.
 *
 * A plan or an idea typed as "the bakery on the corner" has no address the app
 * can resolve, but it does have a time — and at that time the traveler is
 * somewhere: inside a hotel stay, just off a train, about to leave for the
 * airport. That place's clock is the right clock for the plan, which the trip's
 * home zone (wherever it was created) almost never is.
 */
object Whereabouts {

    fun at(segments: List<Segment>, atMillis: Long): Place? {
        val ordered = segments.chronological()

        // Inside a stay or a booked visit: there.
        ordered.lastOrNull { segment ->
            (segment is Segment.Lodging || segment is Segment.Experience) &&
                atMillis in segment.startEpochMillis..segment.endEpochMillis
        }?.let { return it.origin }

        // Otherwise wherever the last thing before it ended...
        ordered.filter { it.endEpochMillis <= atMillis }
            .maxByOrNull { it.endEpochMillis }
            ?.let { return it.destination }

        // ...or, before anything has happened, where the first thing starts.
        return ordered.firstOrNull()?.origin
    }

    fun zoneIdAt(segments: List<Segment>, atMillis: Long, fallback: String): String =
        at(segments, atMillis)?.timeZoneId ?: fallback

    /** A position to rank same-named cities against, when the place has one. */
    fun positionAt(segments: List<Segment>, atMillis: Long): LatLon? =
        at(segments, atMillis)?.takeIf { it.hasCoordinates }?.let { LatLon(it.latitude, it.longitude) }
}
