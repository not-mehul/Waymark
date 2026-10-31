package com.waymark.data.catalog

import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.model.Place

/**
 * What a typed place means: a station, a city, or just the words.
 *
 * The order is strict and every step is an exact match. A three-letter code is
 * a station. Otherwise the text — or any comma-separated part of it, so
 * "Bloomsbury, London" finds London — is looked up as a city name. Where
 * several cities share the name, the one nearest where the trip is at that
 * moment wins; with nothing to be near, the largest does. Text that matches
 * nothing stays as typed, with no coordinates and the zone the caller
 * supplies, because an honest blank on the map is better than a confident pin
 * in the wrong country.
 */
object Places {

    enum class Match { STATION, CITY, TYPED }

    data class Resolved(val place: Place, val match: Match) {
        /** "London, United Kingdom", "LHR · Heathrow", or the words as typed. */
        val label: String
            get() = when (match) {
                Match.STATION -> "${place.code} · ${place.name}"
                Match.CITY -> listOf(place.city, place.country).filter { it.isNotBlank() }.joinToString(", ")
                Match.TYPED -> place.name
            }
    }

    /** Within this, a same-named city is assumed to be the one meant. */
    private const val NEARBY_KM = 300.0

    /** The best reading of [text]; [fallbackZoneId] is used when nothing matches. */
    fun resolve(text: String, near: LatLon?, fallbackZoneId: String): Resolved =
        candidates(text, near, fallbackZoneId).first()

    /**
     * Every reading worth offering, best first, always ending with the text as
     * typed so a traveler can refuse the guesses.
     */
    fun candidates(
        text: String,
        near: LatLon?,
        fallbackZoneId: String,
        limit: Int = 4,
    ): List<Resolved> {
        val query = text.trim()
        val typed = Resolved(
            Place(name = query.ifEmpty { "Unnamed place" }, timeZoneId = fallbackZoneId),
            Match.TYPED,
        )
        if (query.isEmpty()) return listOf(typed)

        if (query.length == 3 && query.all { it.isLetter() }) {
            Airports.find(query)?.let { return listOf(Resolved(it.toPlace(), Match.STATION), typed) }
        }

        // The whole text first, then each part from the end — addresses run
        // from the specific to the general, and the city is usually last.
        val parts = (listOf(query) + query.split(',').map { it.trim() }.reversed())
            .filter { it.isNotEmpty() }
            .distinct()
        val cities = parts.asSequence()
            .map { Cities.named(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

        val ranked = rank(cities, near).take(limit)
        return ranked.map { Resolved(it.toPlace(label = query), Match.CITY) } + typed
    }

    private fun rank(cities: List<Cities.City>, near: LatLon?): List<Cities.City> {
        if (near == null || cities.size < 2) return cities
        // A same-named city close to where the trip is beats a bigger one far
        // away; beyond that, size decides, as it does with no context at all.
        val (close, far) = cities.partition { Geo.distanceKm(near, it.position) <= NEARBY_KM }
        return close.sortedBy { Geo.distanceKm(near, it.position) } + far
    }
}
