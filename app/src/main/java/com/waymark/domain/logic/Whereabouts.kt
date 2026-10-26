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

    /**
     * Where the trip is at [atMillis], or — before anything has happened —
     * where it is about to start: the best guess for resolving a place.
     */
    fun at(segments: List<Segment>, atMillis: Long): Place? =
        before(segments, atMillis) ?: segments.chronological().firstOrNull()?.origin

    /**
     * Where the trip is known to be at [atMillis]: inside a stay or a booked
     * visit, or wherever the last thing before it ended. Null before anything
     * has happened, when "where the traveler is" is genuinely not known.
     */
    fun before(segments: List<Segment>, atMillis: Long): Place? {
        val ordered = segments.chronological()

        ordered.lastOrNull { segment ->
            (segment is Segment.Lodging || segment is Segment.Experience) &&
                atMillis in segment.startEpochMillis..segment.endEpochMillis
        }?.let { return it.origin }

        return ordered.filter { it.endEpochMillis <= atMillis }
            .maxByOrNull { it.endEpochMillis }
            ?.destination
    }

    fun zoneIdAt(segments: List<Segment>, atMillis: Long, fallback: String): String =
        at(segments, atMillis)?.timeZoneId ?: fallback

    /** A position to rank same-named cities against, when the place has one. */
    fun positionAt(segments: List<Segment>, atMillis: Long): LatLon? =
        at(segments, atMillis)?.position
}
