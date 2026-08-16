package com.waymark.domain.logic

import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological

/** Somewhere the trip stops, and how long it stops there. */
data class Stay(
    val place: Place,
    val minutes: Int,
) {
    val hours: Double get() = minutes / 60.0
}

/**
 * Where a trip actually spends its time.
 *
 * The destination notes used to pick a destination by taking the last segment's
 * arrival city, which is wrong in the ordinary case and absurd in the common
 * one: a trip to Japan that connects through Doha for two hours was a trip to
 * Qatar, and a trip home ended at the airport you started from. What a traveler
 * means by "where am I going" is where the days are.
 *
 * So this measures. Between landing somewhere and leaving it again, the party
 * is there; those gaps are summed per place and ranked. The place with the most
 * hours is the destination. Everywhere else with a day or more in it is also a
 * destination — a two-city trip has two — and a two-hour connection has none of
 * the properties of a destination and does not become one.
 *
 * Lodging is deliberately not counted separately. A hotel sits inside the gap
 * between the flight that brought you and the train that takes you on; counting
 * both would double the city it is in.
 */
object TimeAtPlace {

    /** A day. Below this, somewhere is a connection rather than a destination. */
    const val DESTINATION_MINUTES = 24 * 60

    /**
     * Every place the trip pauses, longest first.
     *
     * [travelerFilter] narrows it to one person's itinerary, because a party
     * that splits does not all spend the same days in the same city.
     */
    fun stays(dossier: TripDossier, travelerFilter: String? = null): List<Stay> {
        val moves = dossier.segments
            .asSequence()
            .filter { it is Segment.Flight || it is Segment.Ground }
            .filter { travelerFilter == null || it.travelerIds.isEmpty() || travelerFilter in it.travelerIds }
            .toList()
            .chronological()
        if (moves.size < 2) return emptyList()

        val totals = LinkedHashMap<String, Pair<Place, Int>>()
        for (index in 1 until moves.size) {
            val arrived = moves[index - 1]
            val leaves = moves[index]
            val minutes = ((leaves.startEpochMillis - arrived.endEpochMillis) / 60_000L).toInt()
            if (minutes <= 0) continue

            val place = arrived.destination
            val key = keyOf(place)
            if (key.isEmpty()) continue
            val (existing, sum) = totals[key] ?: (place to 0)
            totals[key] = existing to (sum + minutes)
        }

        return totals.values
            .map { (place, minutes) -> Stay(place, minutes) }
            .sortedWith(compareByDescending<Stay> { it.minutes }.thenBy { keyOf(it.place) })
    }

    /** The one place a trip is *about*, or null when it never settles anywhere. */
    fun primary(dossier: TripDossier, travelerFilter: String? = null): Stay? =
        stays(dossier, travelerFilter).firstOrNull()

    /**
     * The destination, plus everywhere else worth a day. Always includes the
     * longest stay even when the whole trip is shorter than a day, because a
     * long weekend still has somewhere it went.
     */
    fun destinations(dossier: TripDossier, travelerFilter: String? = null): List<Stay> {
        val ranked = stays(dossier, travelerFilter)
        if (ranked.isEmpty()) return emptyList()
        val worthIt = ranked.filter { it.minutes >= DESTINATION_MINUTES }
        return worthIt.ifEmpty { listOf(ranked.first()) }
    }

    /**
     * Places are merged on city where there is one, because arriving at an
     * airport and leaving from a different station in the same city is one
     * stay, not two. Airports with no municipality fall back to their own name.
     */
    private fun keyOf(place: Place): String =
        place.city.trim().lowercase().ifEmpty { place.name.trim().lowercase() }
}
