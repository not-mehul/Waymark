package com.waymark.data.catalog

import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.model.Place
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Carrier names, and the handful of real schedules the worked example is built
 * from.
 *
 * This was once the app's flight lookup: type `BA286`, get a populated segment,
 * with the note that "a network provider, when configured, takes precedence".
 * There is no network provider and there will not be one — every detail of a
 * flight is typed in by the person flying — so nothing on the Add flight screen
 * consults the schedules any more. Two things survived that decision and both
 * are still earning their place:
 *
 * - [carrierName], which turns `BA` into `British Airways` for the vault label.
 *   That is a reference table, not a lookup service, in the same sense that the
 *   airport directory is.
 * - [lookup] and [resolve], which the sample itinerary uses to build a
 *   demonstration out of flights that genuinely exist, with real airports,
 *   real terminals and real block times.
 */
object FlightCatalog {

    data class ScheduledFlight(
        val carrier: String,
        val number: Int,
        val originCode: String,
        val destinationCode: String,
        val departureLocal: LocalTime,
        val arrivalLocal: LocalTime,
        val arrivalDayOffset: Int,
        val aircraft: String,
        val departureTerminal: String?,
        val arrivalTerminal: String?,
        val cabins: List<String> = listOf("Economy", "Premium", "Business"),
    ) {
        val designator: String get() = "$carrier$number"
    }

    /** A schedule resolved onto a real date, with both ends in their own zone. */
    data class FlightPlan(
        val designator: String,
        val carrierName: String,
        val origin: Place,
        val destination: Place,
        val departure: ZonedDateTime,
        val arrival: ZonedDateTime,
        val departureTerminal: String?,
        val arrivalTerminal: String?,
        val aircraft: String,
        val cabins: List<String>,
        val source: String,
    ) {
        val blockMinutes: Int
            get() = ((arrival.toInstant().toEpochMilli() - departure.toInstant().toEpochMilli()) / 60_000L).toInt()
    }

    private val carriers = mapOf(
        "AA" to "American Airlines", "AC" to "Air Canada", "AF" to "Air France",
        "AS" to "Alaska Airlines", "AY" to "Finnair", "BA" to "British Airways",
        "CX" to "Cathay Pacific", "DL" to "Delta Air Lines", "EK" to "Emirates",
        "FI" to "Icelandair", "IB" to "Iberia", "JL" to "Japan Airlines",
        "KL" to "KLM", "LH" to "Lufthansa", "LX" to "Swiss", "NH" to "ANA",
        "NZ" to "Air New Zealand", "OS" to "Austrian", "QF" to "Qantas",
        "QR" to "Qatar Airways", "SK" to "SAS", "SQ" to "Singapore Airlines",
        "TK" to "Turkish Airlines", "TP" to "TAP Air Portugal", "UA" to "United Airlines",
        "VS" to "Virgin Atlantic", "WN" to "Southwest Airlines",
    )

    private fun t(text: String): LocalTime = LocalTime.parse(text)

    private val schedules: List<ScheduledFlight> = listOf(
        // North Atlantic
        ScheduledFlight("AA", 100, "JFK", "LHR", t("18:30"), t("06:30"), 1, "Boeing 777-300ER", "8", "3"),
        ScheduledFlight("BA", 112, "JFK", "LHR", t("18:55"), t("06:55"), 1, "Boeing 777-300ER", "7", "5"),
        ScheduledFlight("BA", 178, "JFK", "LHR", t("08:15"), t("20:15"), 0, "Boeing 777-200ER", "7", "5"),
        ScheduledFlight("VS", 20, "JFK", "LHR", t("20:30"), t("08:30"), 1, "Airbus A330-900", "4", "3"),
        ScheduledFlight("DL", 2, "JFK", "LHR", t("21:15"), t("09:20"), 1, "Airbus A330-300", "4", "3"),
        ScheduledFlight("BA", 286, "SFO", "LHR", t("16:20"), t("10:55"), 1, "Airbus A350-1000", "I", "5"),
        ScheduledFlight("UA", 930, "SFO", "LHR", t("15:25"), t("09:50"), 1, "Boeing 787-9", "I", "2"),
        ScheduledFlight("AA", 106, "JFK", "LHR", t("22:00"), t("10:05"), 1, "Boeing 777-200ER", "8", "3"),
        ScheduledFlight("BA", 117, "LHR", "JFK", t("11:00"), t("13:55"), 0, "Boeing 777-300ER", "5", "7"),
        ScheduledFlight("BA", 285, "LHR", "SFO", t("11:15"), t("14:10"), 0, "Airbus A350-1000", "5", "I"),
        ScheduledFlight("VS", 41, "LHR", "SFO", t("11:35"), t("14:40"), 0, "Airbus A350-1000", "3", "I"),
        ScheduledFlight("AF", 83, "SFO", "CDG", t("15:35"), t("11:20"), 1, "Boeing 777-300ER", "I", "2E"),
        ScheduledFlight("AF", 7, "JFK", "CDG", t("19:30"), t("08:55"), 1, "Airbus A350-900", "1", "2E"),
        ScheduledFlight("KL", 606, "LAX", "AMS", t("15:20"), t("10:55"), 1, "Boeing 787-10", "B", "3"),
        ScheduledFlight("LH", 457, "LAX", "FRA", t("15:50"), t("11:35"), 1, "Airbus A380-800", "B", "1"),
        ScheduledFlight("LH", 441, "IAD", "FRA", t("17:45"), t("07:20"), 1, "Airbus A340-600", "Main", "1"),
        ScheduledFlight("FI", 614, "KEF", "JFK", t("17:00"), t("19:00"), 0, "Boeing 737 MAX 9", "Main", "7"),
        ScheduledFlight("FI", 631, "JFK", "KEF", t("20:20"), t("06:20"), 1, "Boeing 757-200", "7", "Main"),
        ScheduledFlight("TP", 202, "JFK", "LIS", t("22:35"), t("10:35"), 1, "Airbus A330-900", "1", "1"),

        // Europe short-haul
        ScheduledFlight("BA", 442, "LHR", "AMS", t("07:05"), t("09:25"), 0, "Airbus A320neo", "5", "3"),
        ScheduledFlight("AF", 1680, "CDG", "FCO", t("10:20"), t("12:30"), 0, "Airbus A220-300", "2F", "1"),
        ScheduledFlight("LH", 1810, "MUC", "FCO", t("09:35"), t("11:20"), 0, "Airbus A320neo", "2", "1"),
        ScheduledFlight("IB", 3170, "MAD", "LHR", t("08:40"), t("10:10"), 0, "Airbus A321neo", "4S", "2"),
        ScheduledFlight("SK", 1462, "CPH", "LHR", t("07:30"), t("08:45"), 0, "Airbus A320neo", "3", "2"),
        ScheduledFlight("LX", 348, "ZRH", "BCN", t("12:15"), t("14:05"), 0, "Airbus A220-300", "A", "1"),

        // Transpacific and Asia
        ScheduledFlight("UA", 875, "SFO", "NRT", t("11:10"), t("14:55"), 1, "Boeing 777-300ER", "I", "1"),
        ScheduledFlight("NH", 7, "HND", "SFO", t("17:05"), t("10:00"), 0, "Boeing 777-300ER", "3", "I"),
        ScheduledFlight("JL", 1, "HND", "SFO", t("17:50"), t("10:50"), 0, "Boeing 777-300ER", "3", "I"),
        ScheduledFlight("JL", 2, "SFO", "HND", t("13:10"), t("17:15"), 1, "Boeing 777-300ER", "I", "3"),
        ScheduledFlight("SQ", 33, "SFO", "SIN", t("21:45"), t("06:30"), 2, "Airbus A350-900ULR", "I", "3"),
        ScheduledFlight("SQ", 25, "SIN", "JFK", t("09:35"), t("15:10"), 0, "Airbus A350-900ULR", "3", "4"),
        ScheduledFlight("CX", 846, "HKG", "JFK", t("00:30"), t("04:20"), 0, "Boeing 777-300ER", "1", "7"),
        ScheduledFlight("CX", 880, "HKG", "LAX", t("00:50"), t("22:15"), -1, "Boeing 777-300ER", "1", "B"),
        ScheduledFlight("EK", 203, "JFK", "DXB", t("22:20"), t("19:35"), 1, "Airbus A380-800", "4", "3"),
        ScheduledFlight("QR", 702, "JFK", "DOH", t("22:00"), t("17:20"), 1, "Boeing 777-300ER", "8", "Main"),
        ScheduledFlight("TK", 12, "JFK", "IST", t("00:45"), t("18:05"), 0, "Boeing 777-300ER", "1", "Main"),
        ScheduledFlight("NH", 175, "NRT", "HND", t("09:00"), t("10:05"), 0, "Boeing 787-8", "1", "2"),

        // Oceania and the long southern hops
        ScheduledFlight("QF", 11, "SYD", "LAX", t("10:45"), t("06:35"), 0, "Airbus A380-800", "1", "B"),
        ScheduledFlight("QF", 12, "LAX", "SYD", t("22:30"), t("07:20"), 2, "Airbus A380-800", "B", "1"),
        ScheduledFlight("NZ", 2, "AKL", "LAX", t("19:15"), t("10:35"), 0, "Boeing 787-9", "I", "B"),

        // US domestic
        ScheduledFlight("AA", 1, "JFK", "LAX", t("08:00"), t("11:34"), 0, "Airbus A321neo", "8", "4"),
        ScheduledFlight("DL", 404, "LAX", "JFK", t("08:10"), t("16:35"), 0, "Airbus A330-900", "3", "4"),
        ScheduledFlight("UA", 523, "SFO", "EWR", t("07:00"), t("15:30"), 0, "Boeing 757-200", "3", "C"),
        ScheduledFlight("AS", 1, "SEA", "SFO", t("06:00"), t("08:15"), 0, "Boeing 737-900ER", "Main", "1"),
        ScheduledFlight("WN", 1042, "DEN", "LAX", t("14:20"), t("15:55"), 0, "Boeing 737-800", "C", "1"),
        ScheduledFlight("AA", 2402, "DFW", "ORD", t("13:05"), t("15:30"), 0, "Boeing 737-800", "C", "3"),

        // The Americas
        ScheduledFlight("AC", 795, "YYZ", "MEX", t("09:35"), t("14:20"), 0, "Boeing 737 MAX 8", "1", "2"),
        ScheduledFlight("LA", 8080, "LIM", "GRU", t("09:00"), t("16:05"), 0, "Airbus A320neo", "Main", "3"),
    )

    private val index: Map<String, List<ScheduledFlight>> =
        schedules.groupBy { "${it.carrier}${it.number}" }

    fun carrierName(code: String): String = carriers[code.uppercase()] ?: code.uppercase()

    /**
     * Resolve a flight number onto a date, or null when the catalog has never
     * heard of it. Used to build the worked example — not by the Add flight
     * screen, which asks for every field.
     */
    fun lookup(input: String, date: LocalDate): FlightPlan? {
        val designator = FlightDesignator.parse(input) ?: return null
        val scheduled = index["${designator.carrier}${designator.number}"]?.firstOrNull()
            ?: return null
        return resolve(scheduled, date)
    }

    fun resolve(scheduled: ScheduledFlight, date: LocalDate): FlightPlan {
        val origin = Airports.place(scheduled.originCode)
        val destination = Airports.place(scheduled.destinationCode)
        val departure = ZonedDateTime.of(date, scheduled.departureLocal, zone(origin))
        val arrival = ZonedDateTime.of(
            date.plusDays(scheduled.arrivalDayOffset.toLong()),
            scheduled.arrivalLocal,
            zone(destination),
        )
        return FlightPlan(
            designator = scheduled.designator,
            carrierName = carrierName(scheduled.carrier),
            origin = origin,
            destination = destination,
            departure = departure,
            arrival = arrival,
            departureTerminal = scheduled.departureTerminal,
            arrivalTerminal = scheduled.arrivalTerminal,
            aircraft = scheduled.aircraft,
            cabins = scheduled.cabins,
            source = "Bundled schedule",
        )
    }

    private fun zone(place: Place): ZoneId =
        runCatching { ZoneId.of(place.timeZoneId) }.getOrDefault(ZoneId.of("UTC"))

    /** Everything the catalog knows for a station. */
    fun departuresFrom(code: String): List<ScheduledFlight> =
        schedules.filter { it.originCode.equals(code, ignoreCase = true) }
            .sortedBy { it.departureLocal }

    fun knows(input: String): Boolean =
        FlightDesignator.parse(input)?.let { index.containsKey("${it.carrier}${it.number}") } == true

    fun size(): Int = schedules.size
}
