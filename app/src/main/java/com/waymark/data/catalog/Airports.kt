package com.waymark.data.catalog

import com.waymark.domain.model.Place

/**
 * Station reference data, bundled so lookup works with the radio off.
 * Coordinates are the terminal complex, not the runway threshold.
 */
object Airports {

    data class Airport(
        val code: String,
        val name: String,
        val city: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val timeZoneId: String,
        val terminals: List<String> = emptyList(),
    ) {
        fun toPlace(): Place = Place(
            name = name,
            code = code,
            city = city,
            country = country,
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZoneId,
        )
    }

    private val all: List<Airport> = listOf(
        Airport("JFK", "John F. Kennedy International", "New York", "United States", 40.6413, -73.7781, "America/New_York", listOf("1", "4", "5", "7", "8")),
        Airport("EWR", "Newark Liberty International", "New York", "United States", 40.6895, -74.1745, "America/New_York", listOf("A", "B", "C")),
        Airport("LGA", "LaGuardia", "New York", "United States", 40.7769, -73.8740, "America/New_York", listOf("A", "B", "C", "D")),
        Airport("BOS", "Logan International", "Boston", "United States", 42.3656, -71.0096, "America/New_York", listOf("A", "B", "C", "E")),
        Airport("IAD", "Washington Dulles International", "Washington", "United States", 38.9531, -77.4565, "America/New_York", listOf("Main")),
        Airport("ATL", "Hartsfield–Jackson", "Atlanta", "United States", 33.6407, -84.4277, "America/New_York", listOf("N", "S", "I")),
        Airport("MIA", "Miami International", "Miami", "United States", 25.7959, -80.2870, "America/New_York", listOf("N", "C", "S")),
        Airport("ORD", "O'Hare International", "Chicago", "United States", 41.9742, -87.9073, "America/Chicago", listOf("1", "2", "3", "5")),
        Airport("DFW", "Dallas/Fort Worth International", "Dallas", "United States", 32.8998, -97.0403, "America/Chicago", listOf("A", "B", "C", "D", "E")),
        Airport("DEN", "Denver International", "Denver", "United States", 39.8561, -104.6737, "America/Denver", listOf("A", "B", "C")),
        Airport("LAX", "Los Angeles International", "Los Angeles", "United States", 33.9416, -118.4085, "America/Los_Angeles", listOf("1", "2", "3", "4", "5", "6", "7", "B")),
        Airport("SFO", "San Francisco International", "San Francisco", "United States", 37.6213, -122.3790, "America/Los_Angeles", listOf("1", "2", "3", "I")),
        Airport("SEA", "Seattle–Tacoma International", "Seattle", "United States", 47.4502, -122.3088, "America/Los_Angeles", listOf("Main", "S")),
        Airport("YVR", "Vancouver International", "Vancouver", "Canada", 49.1967, -123.1815, "America/Vancouver", listOf("D", "E", "M")),
        Airport("YYZ", "Toronto Pearson International", "Toronto", "Canada", 43.6777, -79.6248, "America/Toronto", listOf("1", "3")),
        Airport("MEX", "Benito Juárez International", "Mexico City", "Mexico", 19.4363, -99.0721, "America/Mexico_City", listOf("1", "2")),
        Airport("GRU", "São Paulo–Guarulhos", "São Paulo", "Brazil", -23.4356, -46.4731, "America/Sao_Paulo", listOf("1", "2", "3")),
        Airport("LIM", "Jorge Chávez International", "Lima", "Peru", -12.0219, -77.1143, "America/Lima", listOf("Main")),
        Airport("EZE", "Ministro Pistarini", "Buenos Aires", "Argentina", -34.8222, -58.5358, "America/Argentina/Buenos_Aires", listOf("A", "B", "C")),
        Airport("LHR", "Heathrow", "London", "United Kingdom", 51.4700, -0.4543, "Europe/London", listOf("2", "3", "4", "5")),
        Airport("LGW", "Gatwick", "London", "United Kingdom", 51.1537, -0.1821, "Europe/London", listOf("N", "S")),
        Airport("DUB", "Dublin", "Dublin", "Ireland", 53.4264, -6.2499, "Europe/Dublin", listOf("1", "2")),
        Airport("CDG", "Charles de Gaulle", "Paris", "France", 49.0097, 2.5479, "Europe/Paris", listOf("1", "2A", "2E", "2F", "3")),
        Airport("AMS", "Schiphol", "Amsterdam", "Netherlands", 52.3105, 4.7683, "Europe/Amsterdam", listOf("1", "2", "3")),
        Airport("FRA", "Frankfurt", "Frankfurt", "Germany", 50.0379, 8.5622, "Europe/Berlin", listOf("1", "2")),
        Airport("MUC", "Munich", "Munich", "Germany", 48.3538, 11.7861, "Europe/Berlin", listOf("1", "2")),
        Airport("ZRH", "Zurich", "Zurich", "Switzerland", 47.4647, 8.5492, "Europe/Zurich", listOf("A", "B", "E")),
        Airport("VIE", "Vienna International", "Vienna", "Austria", 48.1103, 16.5697, "Europe/Vienna", listOf("1", "2", "3")),
        Airport("CPH", "Copenhagen", "Copenhagen", "Denmark", 55.6180, 12.6560, "Europe/Copenhagen", listOf("2", "3")),
        Airport("OSL", "Oslo Gardermoen", "Oslo", "Norway", 60.1939, 11.1004, "Europe/Oslo", listOf("Main")),
        Airport("ARN", "Stockholm Arlanda", "Stockholm", "Sweden", 59.6519, 17.9186, "Europe/Stockholm", listOf("2", "4", "5")),
        Airport("KEF", "Keflavík International", "Reykjavík", "Iceland", 63.9850, -22.6056, "Atlantic/Reykjavik", listOf("Main")),
        Airport("MAD", "Adolfo Suárez Madrid–Barajas", "Madrid", "Spain", 40.4839, -3.5680, "Europe/Madrid", listOf("1", "2", "3", "4", "4S")),
        Airport("BCN", "Josep Tarradellas Barcelona–El Prat", "Barcelona", "Spain", 41.2974, 2.0833, "Europe/Madrid", listOf("1", "2")),
        Airport("LIS", "Humberto Delgado", "Lisbon", "Portugal", 38.7742, -9.1342, "Europe/Lisbon", listOf("1", "2")),
        Airport("FCO", "Leonardo da Vinci–Fiumicino", "Rome", "Italy", 41.8003, 12.2389, "Europe/Rome", listOf("1", "3")),
        Airport("ATH", "Athens International", "Athens", "Greece", 37.9364, 23.9445, "Europe/Athens", listOf("Main", "Satellite")),
        Airport("PRG", "Václav Havel", "Prague", "Czechia", 50.1008, 14.2600, "Europe/Prague", listOf("1", "2")),
        Airport("IST", "Istanbul", "Istanbul", "Türkiye", 41.2753, 28.7519, "Europe/Istanbul", listOf("Main")),
        Airport("CAI", "Cairo International", "Cairo", "Egypt", 30.1219, 31.4056, "Africa/Cairo", listOf("1", "2", "3")),
        Airport("JNB", "O. R. Tambo International", "Johannesburg", "South Africa", -26.1392, 28.2460, "Africa/Johannesburg", listOf("A", "B")),
        Airport("DXB", "Dubai International", "Dubai", "United Arab Emirates", 25.2532, 55.3657, "Asia/Dubai", listOf("1", "2", "3")),
        Airport("DOH", "Hamad International", "Doha", "Qatar", 25.2731, 51.6081, "Asia/Qatar", listOf("Main")),
        Airport("DEL", "Indira Gandhi International", "Delhi", "India", 28.5562, 77.1000, "Asia/Kolkata", listOf("1", "2", "3")),
        Airport("BOM", "Chhatrapati Shivaji Maharaj", "Mumbai", "India", 19.0896, 72.8656, "Asia/Kolkata", listOf("1", "2")),
        Airport("BKK", "Suvarnabhumi", "Bangkok", "Thailand", 13.6900, 100.7501, "Asia/Bangkok", listOf("Main")),
        Airport("SIN", "Changi", "Singapore", "Singapore", 1.3644, 103.9915, "Asia/Singapore", listOf("1", "2", "3", "4")),
        Airport("HKG", "Hong Kong International", "Hong Kong", "Hong Kong SAR", 22.3080, 113.9185, "Asia/Hong_Kong", listOf("1")),
        Airport("TPE", "Taoyuan International", "Taipei", "Taiwan", 25.0777, 121.2328, "Asia/Taipei", listOf("1", "2")),
        Airport("ICN", "Incheon International", "Seoul", "South Korea", 37.4602, 126.4407, "Asia/Seoul", listOf("1", "2")),
        Airport("NRT", "Narita International", "Tokyo", "Japan", 35.7720, 140.3929, "Asia/Tokyo", listOf("1", "2", "3")),
        Airport("HND", "Haneda", "Tokyo", "Japan", 35.5494, 139.7798, "Asia/Tokyo", listOf("1", "2", "3")),
        Airport("KIX", "Kansai International", "Osaka", "Japan", 34.4347, 135.2440, "Asia/Tokyo", listOf("1", "2")),
        Airport("SYD", "Kingsford Smith", "Sydney", "Australia", -33.9399, 151.1753, "Australia/Sydney", listOf("1", "2", "3")),
        Airport("MEL", "Melbourne", "Melbourne", "Australia", -37.6690, 144.8410, "Australia/Melbourne", listOf("1", "2", "3", "4")),
        Airport("AKL", "Auckland", "Auckland", "New Zealand", -37.0082, 174.7850, "Pacific/Auckland", listOf("I", "D")),
    )

    private val byCode: Map<String, Airport> = all.associateBy { it.code }

    fun find(code: String?): Airport? = code?.trim()?.uppercase()?.let { byCode[it] }

    fun place(code: String): Place =
        find(code)?.toPlace() ?: Place(name = code.uppercase(), code = code.uppercase())

    fun all(): List<Airport> = all

    /** Type-ahead over code, city and name. */
    fun search(query: String, limit: Int = 8): List<Airport> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return all.asSequence()
            .map { airport ->
                val score = when {
                    airport.code.lowercase() == needle -> 0
                    airport.code.lowercase().startsWith(needle) -> 1
                    airport.city.lowercase().startsWith(needle) -> 2
                    airport.name.lowercase().startsWith(needle) -> 3
                    airport.city.lowercase().contains(needle) -> 4
                    airport.name.lowercase().contains(needle) -> 5
                    else -> Int.MAX_VALUE
                }
                airport to score
            }
            .filter { it.second != Int.MAX_VALUE }
            .sortedWith(compareBy({ it.second }, { it.first.code }))
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun terminalsFor(code: String): List<String> = find(code)?.terminals ?: emptyList()
}
