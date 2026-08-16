package com.waymark.data.catalog

/**
 * Parses the bundled world directory of IATA stations.
 *
 * The file is generated, not hand-maintained, and its shape reflects that: a
 * table of IANA time-zone names first, one per line, then a blank line, then
 * one airport per line referring to a zone by index. Zone names repeat across
 * thousands of rows — interning them costs one small table and saves about a
 * fifth of the file.
 *
 * ```
 * Europe/London
 * America/Los_Angeles
 *
 * LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
 * SFO|San Francisco International Airport|San Francisco|US|37.6188|-122.3750|1
 * ```
 *
 * Time zones were resolved from each airport's coordinates when the file was
 * built, against the same boundary data a server would use. The app therefore
 * carries an exact zone per station and applies no heuristic of its own —
 * which matters, because getting a zone wrong moves a flight to the wrong day,
 * not merely to the wrong hour.
 *
 * Parsing is pure Kotlin over a sequence of lines so it can be tested on the
 * JVM without an Android asset manager, and so a malformed line is skipped
 * rather than taking the whole directory down with it.
 */
object AirportDirectory {

    const val ASSET_NAME = "airports.txt"

    private const val FIELDS = 7

    fun parse(lines: Sequence<String>): List<Airports.Airport> {
        val zones = mutableListOf<String>()
        val airports = mutableListOf<Airports.Airport>()
        var readingZones = true

        lines.forEach { line ->
            if (readingZones) {
                // The blank line is the only separator; everything before it is
                // the zone table.
                if (line.isBlank()) readingZones = false else zones += line.trim()
                return@forEach
            }
            if (line.isBlank()) return@forEach
            parseAirport(line, zones)?.let { airports += it }
        }
        return airports
    }

    private fun parseAirport(line: String, zones: List<String>): Airports.Airport? {
        val parts = line.split('|')
        if (parts.size != FIELDS) return null

        val code = parts[0].trim().uppercase()
        if (code.length != 3) return null

        val latitude = parts[4].toDoubleOrNull() ?: return null
        val longitude = parts[5].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val zone = zones.getOrNull(parts[6].trim().toIntOrNull() ?: return null) ?: return null

        val name = parts[1].trim()
        // Roughly one row in twelve has no municipality — a remote strip, an
        // island. The airport's own name is the only thing left to call it.
        val city = parts[2].trim().ifEmpty { shortNameOf(name) }

        return Airports.Airport(
            code = code,
            name = name.ifEmpty { code },
            city = city,
            country = parts[3].trim().uppercase(),
            latitude = latitude,
            longitude = longitude,
            timeZoneId = zone,
        )
    }

    /**
     * "Utirik Airport" → "Utirik". The suffixes carry no information in a list
     * where every row is an airport, and they push the useful word off the end
     * of a chip on a phone.
     */
    private fun shortNameOf(name: String): String {
        var trimmed = name
        listOf(
            " International Airport", " Regional Airport", " Municipal Airport",
            " Airport", " Airstrip", " Airfield", " Airpark", " Air Base",
            " Seaplane Base", " Heliport",
        ).forEach { suffix ->
            if (trimmed.endsWith(suffix, ignoreCase = true)) {
                trimmed = trimmed.dropLast(suffix.length)
                return@forEach
            }
        }
        return trimmed.trim().ifEmpty { name }
    }
}
