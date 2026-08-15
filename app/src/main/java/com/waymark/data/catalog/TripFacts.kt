package com.waymark.data.catalog

import com.waymark.domain.logic.PackingContext
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Reads a trip and states the handful of facts the packing planner needs.
 *
 * Climate is inferred from latitude and month rather than from a weather feed:
 * it needs no network, it is right often enough to pack by, and it is honest
 * about being a rule of thumb. Everything else is read straight off the
 * itinerary.
 */
object TripFacts {

    private const val TROPICS = 23.5
    private const val LONG_HAUL_HOURS = 6

    fun packingContext(dossier: TripDossier, ideas: List<Idea> = emptyList()): PackingContext {
        val segments = dossier.segments.chronological()
        val flights = segments.filterIsInstance<Segment.Flight>()
        val lodging = segments.filterIsInstance<Segment.Lodging>()

        val nights = lodging.sumOf { it.nights }.toInt().takeIf { it > 0 }
            ?: ChronoUnit.DAYS.between(dossier.trip.startDate(), dossier.trip.endDate())
                .toInt().coerceAtLeast(1)

        val homeCountry = segments.firstOrNull()?.origin?.country.orEmpty()
        val destinations = segments
            .map { it.destination }
            .filter { it.country.isNotBlank() && it.country != homeCountry }

        val month = dossier.trip.startDate().month
        val climate = destinations
            .map { climateFor(it.latitude, month) }
            .maxByOrNull { severity(it) }
            ?: PackingContext.Climate.MILD

        val text = (segments.map { it.title } + ideas.map { it.title + " " + it.note.orEmpty() })
            .joinToString(" ")
            .lowercase()

        return PackingContext(
            tripId = dossier.trip.id,
            nights = nights,
            crossesBorder = destinations.isNotEmpty(),
            hasFlights = flights.isNotEmpty(),
            longHaul = flights.any {
                ChronoUnit.HOURS.between(it.start, it.end) >= LONG_HAUL_HOURS
            },
            climate = climate,
            rainLikely = rainLikely(destinations.map { it.latitude }, month) ||
                destinations.any { insightSaysRain(it.city) },
            homePlugTypes = plugTypes(segments.firstOrNull()?.origin?.city),
            destinationPlugTypes = destinations
                .flatMap { plugTypes(it.city) }
                .toSet(),
            swimming = WATER_WORDS.any { it in text } ||
                ideas.any { it.kind == IdeaKind.ACTIVITY && WATER_WORDS.any { word -> word in it.title.lowercase() } },
            walking = ideas.any { it.kind == IdeaKind.WALK } || "walk" in text,
            formalDinner = segments.filterIsInstance<Segment.Experience>()
                .any { it.category.equals("Restaurant", ignoreCase = true) },
        )
    }

    private val WATER_WORDS = listOf("swim", "pool", "beach", "baths", "onsen", "sentō", "lido")

    /**
     * A rule of thumb, not a forecast: the tropics are always warm, and
     * elsewhere the season decides, flipped for the southern hemisphere.
     */
    fun climateFor(latitude: Double, month: Month): PackingContext.Climate {
        val magnitude = abs(latitude)
        if (magnitude <= TROPICS) return PackingContext.Climate.TROPICAL

        val northern = latitude >= 0
        val season = when (month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> if (northern) WINTER else SUMMER
            Month.MARCH, Month.APRIL, Month.MAY -> SHOULDER
            Month.JUNE, Month.JULY, Month.AUGUST -> if (northern) SUMMER else WINTER
            else -> SHOULDER
        }

        return when {
            magnitude >= 55 -> when (season) {
                SUMMER -> PackingContext.Climate.MILD
                else -> PackingContext.Climate.COLD
            }

            magnitude >= 40 -> when (season) {
                WINTER -> PackingContext.Climate.COLD
                SUMMER -> PackingContext.Climate.WARM
                else -> PackingContext.Climate.MILD
            }

            else -> when (season) {
                WINTER -> PackingContext.Climate.MILD
                else -> PackingContext.Climate.WARM
            }
        }
    }

    private const val WINTER = 0
    private const val SHOULDER = 1
    private const val SUMMER = 2

    /** Pack for the harshest destination on the trip, not the average one. */
    private fun severity(climate: PackingContext.Climate): Int = when (climate) {
        PackingContext.Climate.MILD -> 0
        PackingContext.Climate.WARM -> 1
        PackingContext.Climate.TROPICAL -> 2
        PackingContext.Climate.COLD -> 3
    }

    private fun rainLikely(latitudes: List<Double>, month: Month): Boolean =
        latitudes.any { abs(it) >= 45 } && month !in setOf(Month.JUNE, Month.JULY)

    private fun insightSaysRain(city: String): Boolean =
        DestinationInsights.forCity(city)?.seasonNote?.lowercase()?.contains("rain") == true

    /** Socket types for a city, from the bundled notes. */
    fun plugTypes(city: String?): Set<String> {
        val insight = DestinationInsights.forCity(city) ?: return emptySet()
        return insight.plugTypes
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
