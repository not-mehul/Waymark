package com.waymark.domain.logic

import com.waymark.domain.model.Place
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.WeatherBasis
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Which weather a trip needs: where it is on each day still to come, as runs
 * of days in one place, and whether each day can have a forecast yet.
 */
object WeatherPlan {

    /** Days, today included, that a forecast reaches. */
    const val FORECAST_DAYS = 16L

    /** The middle of the day, which is where a day's weather is read from. */
    private val MIDDAY: LocalTime = LocalTime.of(13, 0)

    /** A run of consecutive days in one place. */
    data class Stop(val place: Place, val days: List<LocalDate>) {
        val latitude: Double get() = requireNotNull(place.latitude)
        val longitude: Double get() = requireNotNull(place.longitude)

        /** The place to a hundredth of a degree, about a kilometre: what weather is cached under. */
        val key: String get() = keyOf(latitude, longitude)

        val label: String get() = place.city.ifBlank { place.name }
    }

    /**
     * The trip's remaining days, from [today], grouped by where the trip is at
     * midday on each. Days somewhere with no known position are left out:
     * there is nowhere to ask about.
     */
    fun stops(dossier: TripDossier, today: LocalDate): List<Stop> {
        val segments = dossier.segments
        if (segments.isEmpty()) return emptyList()
        val first = maxOf(dossier.trip.startDate(), today)
        val last = dossier.trip.endDate()
        val stops = mutableListOf<Stop>()
        var day = first
        while (!day.isAfter(last) && stops.sumOf { it.days.size } < TripAnalytics.MAX_DAYS) {
            val home = dossier.trip.homeZoneId
            val zone = zoneOf(Whereabouts.zoneIdAt(segments, midday(day, home), home))
            val place = Whereabouts.at(segments, day.atTime(MIDDAY).atZone(zone).toInstant().toEpochMilli())
            if (place?.position != null) {
                val current = stops.lastOrNull()
                if (current != null && sameStop(current.place, place) && current.days.last() == day.minusDays(1)) {
                    stops[stops.lastIndex] = current.copy(days = current.days + day)
                } else {
                    stops += Stop(place, listOf(day))
                }
            }
            day = day.plusDays(1)
        }
        return stops
    }

    /** Forecast for the next two weeks, last year's record after that. */
    fun basisFor(date: LocalDate, today: LocalDate): WeatherBasis =
        if (date.isBefore(today.plusDays(FORECAST_DAYS))) WeatherBasis.FORECAST else WeatherBasis.LAST_YEAR

    fun keyOf(latitude: Double, longitude: Double): String =
        "%.2f,%.2f".format(java.util.Locale.ROOT, latitude, longitude)

    private fun sameStop(a: Place, b: Place): Boolean =
        if (a.city.isNotBlank() && b.city.isNotBlank()) {
            a.city.equals(b.city, ignoreCase = true) && a.country.equals(b.country, ignoreCase = true)
        } else {
            a.position == b.position
        }

    private fun midday(day: LocalDate, zone: String): Long =
        day.atTime(MIDDAY).atZone(zoneOf(zone)).toInstant().toEpochMilli()

    private fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))
}

/** How a day's weather is said. */
object WeatherText {

    /** Fahrenheit for the few places that still read it; set from the phone's region. */
    @Volatile
    var fahrenheit: Boolean = false

    /** The countries whose own weather reports are in Fahrenheit. */
    val FAHRENHEIT_REGIONS = setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    fun temperature(celsius: Double, fahrenheit: Boolean = this.fahrenheit): String =
        if (fahrenheit) "${Math.round(celsius * 9 / 5 + 32)}°" else "${Math.round(celsius)}°"

    /** The WMO weather code, in words. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms, hail"
        else -> "—"
    }

    /** Whether the day is one to pack for: rain, snow or storms. */
    fun isWet(code: Int): Boolean = code in 51..99
}
