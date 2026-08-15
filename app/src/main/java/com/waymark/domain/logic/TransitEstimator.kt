package com.waymark.domain.logic

import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Place
import kotlin.math.roundToInt

/**
 * How long it takes to get from one place to another, and how much of that is
 * the part nobody schedules — the walk to the rank, the wait for the train.
 */
data class TransitEstimate(
    val mode: GroundMode,
    val distanceKm: Double,
    val travelMinutes: Int,
    val overheadMinutes: Int,
    val confidence: Confidence,
) {
    val totalMinutes: Int get() = travelMinutes + overheadMinutes

    enum class Confidence { HIGH, MEDIUM, LOW;

        val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
    }

    val summary: String
        get() = "${TimeText.duration(totalMinutes)} by ${mode.label.lowercase()}"
}

object TransitEstimator {

    /**
     * Door-to-door speeds in km/h, deliberately conservative: these are average
     * speeds over a whole journey, not vehicle top speeds.
     */
    private val averageSpeedKmh = mapOf(
        GroundMode.WALK to 4.6,
        GroundMode.TRANSIT to 24.0,
        GroundMode.BUS to 20.0,
        GroundMode.TAXI to 30.0,
        GroundMode.RIDESHARE to 30.0,
        GroundMode.RENTAL_CAR to 45.0,
        GroundMode.SHUTTLE to 26.0,
        GroundMode.FERRY to 32.0,
        GroundMode.TRAIN to 95.0,
    )

    /** Fixed minutes spent not moving: finding the rank, queueing, buying a ticket. */
    private val overheadMinutes = mapOf(
        GroundMode.WALK to 0,
        GroundMode.TRANSIT to 12,
        GroundMode.BUS to 10,
        GroundMode.TAXI to 8,
        GroundMode.RIDESHARE to 7,
        GroundMode.RENTAL_CAR to 25,
        GroundMode.SHUTTLE to 15,
        GroundMode.FERRY to 15,
        GroundMode.TRAIN to 18,
    )

    /**
     * Straight-line distance understates road distance. This is the usual
     * detour factor: dense cities wander more than open country.
     */
    private fun detourFactor(distanceKm: Double, mode: GroundMode): Double = when {
        mode == GroundMode.FERRY -> 1.10
        mode == GroundMode.TRAIN -> 1.15
        distanceKm < 3 -> 1.45
        distanceKm < 15 -> 1.32
        distanceKm < 60 -> 1.22
        else -> 1.15
    }

    fun estimate(from: Place, to: Place, mode: GroundMode): TransitEstimate {
        val straightKm = Geo.distanceKm(from, to)
        val routeKm = straightKm * detourFactor(straightKm, mode)
        val speed = averageSpeedKmh[mode] ?: 30.0
        val travel = if (speed <= 0) 0 else ((routeKm / speed) * 60.0).roundToInt()
        val confidence = when {
            !from.hasCoordinates || !to.hasCoordinates -> TransitEstimate.Confidence.LOW
            straightKm > 400 -> TransitEstimate.Confidence.LOW
            straightKm > 80 -> TransitEstimate.Confidence.MEDIUM
            else -> TransitEstimate.Confidence.HIGH
        }
        return TransitEstimate(
            mode = mode,
            distanceKm = routeKm,
            travelMinutes = travel.coerceAtLeast(if (straightKm > 0.05) 1 else 0),
            overheadMinutes = overheadMinutes[mode] ?: 10,
            confidence = confidence,
        )
    }

    /** The mode a traveler would actually pick for this hop, absent a booking. */
    fun suggestMode(from: Place, to: Place): GroundMode {
        val km = Geo.distanceKm(from, to)
        val airportInvolved = from.code != null || to.code != null
        return when {
            km < 1.2 -> GroundMode.WALK
            airportInvolved && km < 60 -> GroundMode.TRANSIT
            km < 12 -> GroundMode.TRANSIT
            km < 90 -> GroundMode.TAXI
            else -> GroundMode.TRAIN
        }
    }

    /** Best guess without a booked mode, used to fill gaps in the timeline. */
    fun estimateBest(from: Place, to: Place): TransitEstimate =
        estimate(from, to, suggestMode(from, to))

    /** Every plausible option, cheapest-effort first, for the gap detail sheet. */
    fun options(from: Place, to: Place): List<TransitEstimate> {
        val km = Geo.distanceKm(from, to)
        val modes = buildList {
            if (km < 4) add(GroundMode.WALK)
            add(GroundMode.TRANSIT)
            add(GroundMode.TAXI)
            if (km > 40) add(GroundMode.TRAIN)
        }
        return modes.distinct().map { estimate(from, to, it) }.sortedBy { it.totalMinutes }
    }
}
