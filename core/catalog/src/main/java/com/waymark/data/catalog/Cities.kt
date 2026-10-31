package com.waymark.data.catalog

import com.waymark.domain.logic.LatLon
import com.waymark.domain.model.Place
import java.text.Normalizer

/**
 * Every city of fifteen thousand people or more, with the time zone it keeps.
 *
 * The station directory only knows places with an airport, and a hotel in
 * Kyoto, a dinner in Bruges or a train to Versailles is not one. Without this,
 * a plan typed as "Paris" was matched by substring against airports and given
 * Charles de Gaulle's coordinates, and "Manhattan" became an airfield in
 * Kansas. Matching here is exact — on the name, or on one of the names the
 * city is known by — and never a substring.
 *
 * Loaded from the bundled `cities.txt` (GeoNames, CC BY 4.0) by [CityDirectory].
 */
object Cities {

    class City(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        /** ISO 3166-1 alpha-2. */
        val countryCode: String,
        /** In thousands; only used to rank cities that share a name. */
        val population: Int,
        val timeZoneId: String,
        private val alternates: String,
    ) {
        internal val key: String = normalise(name)

        val country: String get() = Countries.forCode(countryCode)?.name ?: countryCode

        val position: LatLon get() = LatLon(latitude, longitude)

        internal fun knownAs(needle: String): Boolean =
            alternates.isNotEmpty() && alternates.split(';').any { normalise(it) == needle }

        /** A place in this city — [label] is what the traveler typed, kept as the name. */
        fun toPlace(label: String = name): Place = Place(
            name = label,
            city = name,
            country = country,
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZoneId,
        )

        override fun toString(): String = "$name, $countryCode"
    }

    @Volatile
    private var directory: List<City> = emptyList()

    val loaded: Boolean get() = directory.isNotEmpty()

    /** Install the parsed gazetteer. Rows arrive largest first. */
    fun install(parsed: List<City>) {
        directory = parsed
    }

    /**
     * Every city called exactly [name]: those whose own name matches first,
     * then those known by it, each group largest first.
     */
    fun named(name: String): List<City> {
        val needle = normalise(name)
        if (needle.isEmpty()) return emptyList()
        val own = directory.filter { it.key == needle }
        val known = directory.filter { it.key != needle && it.knownAs(needle) }
        return own + known
    }

    /**
     * Case, accents, punctuation and the usual abbreviations do not decide a
     * match: "st. petersburg", "Saint-Petersburg" and "SAINT PETERSBURG" are
     * one city.
     */
    internal fun normalise(value: String): String {
        val folded = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(PUNCTUATION, " ")
            .replace(SPACES, " ")
            .trim()
        return folded
            .removePrefix("the ")
            .replace(Regex("^st "), "saint ")
            .replace(Regex("^ste "), "sainte ")
    }

    private val MARKS = Regex("\\p{M}+")
    private val PUNCTUATION = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")
}

/** Parses the bundled gazetteer; the same zone-table-then-rows shape as the stations. */
object CityDirectory {

    const val ASSET_NAME = "cities.txt"

    /** The bundled file, a classpath resource of this module, line by line. */
    fun <T> readBundled(block: (Sequence<String>) -> T): T =
        requireNotNull(CityDirectory::class.java.getResourceAsStream(ASSET_NAME)) { "$ASSET_NAME is missing" }
            .bufferedReader().use { block(it.lineSequence()) }

    fun parse(lines: Sequence<String>): List<Cities.City> {
        val zones = mutableListOf<String>()
        val cities = mutableListOf<Cities.City>()
        var readingZones = true
        lines.forEach { line ->
            if (readingZones) {
                if (line.isBlank()) readingZones = false else zones += line.trim()
                return@forEach
            }
            if (line.isBlank()) return@forEach
            parseCity(line, zones)?.let { cities += it }
        }
        return cities
    }

    private fun parseCity(line: String, zones: List<String>): Cities.City? {
        val parts = line.split('|')
        if (parts.size != 7) return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        val longitude = parts[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val zone = zones.getOrNull(parts[5].toIntOrNull() ?: return null) ?: return null
        val name = parts[0].trim().ifEmpty { return null }
        return Cities.City(
            name = name,
            latitude = latitude,
            longitude = longitude,
            countryCode = parts[3].trim().uppercase(),
            population = parts[4].toIntOrNull() ?: 0,
            timeZoneId = zone,
            alternates = parts[6].trim(),
        )
    }
}
