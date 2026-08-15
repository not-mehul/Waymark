package com.waymark.domain.logic

import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** One row of a chart: a label, a value, and the text that goes beside the mark. */
data class Measure(
    val label: String,
    val value: Double,
    val display: String,
    val emphasis: Boolean = false,
)

/** How a single day is spent, in hours. The four parts sum to 24. */
data class DayLoad(
    val date: LocalDate,
    val movingHours: Double,
    val bookedHours: Double,
    val freeHours: Double,
) {
    val busyHours: Double get() = movingHours + bookedHours
}

data class TripAnalyticsReport(
    val totalDistanceKm: Double,
    val flownKm: Double,
    val groundKm: Double,
    val distanceByMode: List<Measure>,
    val hoursMoving: Double,
    val hoursBooked: Double,
    val hoursAtRest: Double,
    val nightsByCity: List<Measure>,
    val dayLoads: List<DayLoad>,
    val busiestDay: DayLoad?,
    val quietestDay: DayLoad?,
    val ideasDone: Int,
    val ideasOutstanding: Int,
    val ideasScheduled: Int,
    val timeZonesCrossed: Int,
    val countriesVisited: List<String>,
    val longestLegKm: Double,
    val longestLegLabel: String?,
    val carbonKg: Double,
) {
    val hasDistance: Boolean get() = totalDistanceKm > 0
    /** Share of the trip's waking hours with something on them. */
    val bookedShare: Double
        get() = (hoursMoving + hoursBooked).let { busy ->
            val waking = (dayLoads.size * WAKING_HOURS).coerceAtLeast(1.0)
            (busy / waking).coerceIn(0.0, 1.0)
        }

    companion object {
        const val WAKING_HOURS = 16.0
    }
}

/**
 * The trip, counted.
 *
 * Everything here is derived from segments the traveler actually entered — no
 * estimates dressed as measurements. The one modelled figure, the carbon
 * estimate, carries its factors in the open (see [EmissionFactors]) because a
 * number nobody can check is a number nobody should trust.
 */
object TripAnalytics {

    /**
     * Kilograms of CO₂ equivalent per passenger-kilometre. Mid-range figures
     * from published national inventories; short flights are worse per km
     * because the climb is the expensive part.
     */
    object EmissionFactors {
        const val SHORT_HAUL_FLIGHT = 0.246
        const val LONG_HAUL_FLIGHT = 0.150
        const val TRAIN = 0.035
        const val BUS = 0.097
        const val CAR = 0.171
        const val FERRY = 0.113
        const val LONG_HAUL_THRESHOLD_KM = 3700.0
    }

    fun report(
        dossier: TripDossier,
        ideas: List<Idea> = emptyList(),
    ): TripAnalyticsReport {
        val segments = dossier.segments.chronological()
        val flights = segments.filterIsInstance<Segment.Flight>()
        val ground = segments.filterIsInstance<Segment.Ground>()

        val flownKm = flights.sumOf { legKm(it) }
        val groundKm = ground.sumOf { legKm(it) }

        val byMode = buildList {
            if (flownKm > 0) {
                add(Measure("Air", flownKm, Geo.formatDistance(flownKm), emphasis = true))
            }
            ground
                .groupBy { it.mode }
                .mapValues { (_, legs) -> legs.sumOf { legKm(it) } }
                .filterValues { it > 0 }
                .entries
                .sortedByDescending { it.value }
                .forEach { (mode, km) -> add(Measure(mode.label, km, Geo.formatDistance(km))) }
        }

        val hoursMoving = segments
            .filter { it is Segment.Flight || it is Segment.Ground }
            .sumOf { hours(it) }
        val hoursBooked = segments.filterIsInstance<Segment.Experience>().sumOf { hours(it) }

        val nights = segments.filterIsInstance<Segment.Lodging>()
            .groupBy { it.origin.city.ifBlank { it.propertyName } }
            .mapValues { (_, stays) -> stays.sumOf { it.nights }.toDouble() }
            .entries
            .sortedByDescending { it.value }
            .map { (city, count) ->
                Measure(city, count, "${count.roundToInt()} night" + if (count == 1.0) "" else "s")
            }

        val loads = dayLoads(segments)
        val longest = flights.maxByOrNull { legKm(it) }

        return TripAnalyticsReport(
            totalDistanceKm = flownKm + groundKm,
            flownKm = flownKm,
            groundKm = groundKm,
            distanceByMode = byMode,
            hoursMoving = hoursMoving,
            hoursBooked = hoursBooked,
            hoursAtRest = (loads.size * 24.0 - hoursMoving - hoursBooked).coerceAtLeast(0.0),
            nightsByCity = nights,
            dayLoads = loads,
            busiestDay = loads.maxByOrNull { it.busyHours },
            quietestDay = loads.filter { it.busyHours > 0 }.minByOrNull { it.busyHours },
            ideasDone = ideas.count { it.status == IdeaStatus.DONE },
            ideasOutstanding = ideas.count { it.status == IdeaStatus.SAVED },
            ideasScheduled = ideas.count { it.status == IdeaStatus.SCHEDULED },
            timeZonesCrossed = segments
                .flatMap { listOf(it.startZoneId, it.endZoneId) }
                .distinct()
                .size,
            countriesVisited = segments
                .flatMap { listOf(it.origin.country, it.destination.country) }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            longestLegKm = longest?.let { legKm(it) } ?: 0.0,
            longestLegLabel = longest?.let {
                "${it.origin.shortLabel} → ${it.destination.shortLabel}"
            },
            carbonKg = carbonKg(segments),
        )
    }

    /** Hours per day with something on them, over the trip's own calendar. */
    fun dayLoads(segments: List<Segment>): List<DayLoad> {
        if (segments.isEmpty()) return emptyList()
        val first = segments.minOf { it.start.toLocalDate() }
        val last = segments.maxOf { it.end.toLocalDate() }
        val days = ChronoUnit.DAYS.between(first, last).toInt().coerceIn(0, 120)

        return (0..days).map { offset ->
            val date = first.plusDays(offset.toLong())
            val onThisDay = segments.filter { segment ->
                val from = segment.start.toLocalDate()
                val to = segment.end.toLocalDate()
                !date.isBefore(from) && !date.isAfter(to)
            }
            val moving = onThisDay
                .filter { it is Segment.Flight || it is Segment.Ground }
                .sumOf { hoursOnDay(it, date) }
            val booked = onThisDay
                .filterIsInstance<Segment.Experience>()
                .sumOf { hoursOnDay(it, date) }
            DayLoad(
                date = date,
                movingHours = moving,
                bookedHours = booked,
                freeHours = (TripAnalyticsReport.WAKING_HOURS - moving - booked).coerceAtLeast(0.0),
            )
        }
    }

    /**
     * A modelled figure, not a measurement: distance times a published factor
     * per mode. Lodging and meals are excluded rather than guessed at.
     */
    fun carbonKg(segments: List<Segment>): Double = segments.sumOf { segment ->
        val km = legKm(segment)
        when (segment) {
            is Segment.Flight -> if (km >= EmissionFactors.LONG_HAUL_THRESHOLD_KM) {
                km * EmissionFactors.LONG_HAUL_FLIGHT
            } else {
                km * EmissionFactors.SHORT_HAUL_FLIGHT
            }

            is Segment.Ground -> km * when (segment.mode) {
                GroundMode.TRAIN, GroundMode.TRANSIT -> EmissionFactors.TRAIN
                GroundMode.BUS, GroundMode.SHUTTLE -> EmissionFactors.BUS
                GroundMode.TAXI, GroundMode.RIDESHARE, GroundMode.RENTAL_CAR -> EmissionFactors.CAR
                GroundMode.FERRY -> EmissionFactors.FERRY
                GroundMode.WALK -> 0.0
            }

            else -> 0.0
        }
    }

    private fun legKm(segment: Segment): Double =
        if (segment.origin.hasCoordinates && segment.destination.hasCoordinates) {
            Geo.distanceKm(segment.origin, segment.destination)
        } else {
            0.0
        }

    private fun hours(segment: Segment): Double =
        (segment.endEpochMillis - segment.startEpochMillis) / 3_600_000.0

    /** The part of a segment that falls on one local day. */
    private fun hoursOnDay(segment: Segment, date: LocalDate): Double {
        val zone = segment.start.zone
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val from = maxOf(segment.startEpochMillis, dayStart)
        val to = minOf(segment.endEpochMillis, dayEnd)
        return ((to - from).coerceAtLeast(0L)) / 3_600_000.0
    }
}
