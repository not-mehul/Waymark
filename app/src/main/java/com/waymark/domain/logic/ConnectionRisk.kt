package com.waymark.domain.logic

import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment

enum class RiskLevel { COMFORTABLE, TIGHT, AT_RISK, BROKEN;

    val label: String
        get() = when (this) {
            COMFORTABLE -> "Comfortable"
            TIGHT -> "Tight"
            AT_RISK -> "At risk"
            BROKEN -> "Not connecting"
        }
}

/**
 * The verdict on a connection: how much slack there is once the realistic
 * minimum has been taken out, and what drives the number.
 */
data class ConnectionVerdict(
    val level: RiskLevel,
    val availableMinutes: Int,
    val requiredMinutes: Int,
    val reason: String,
) {
    val slackMinutes: Int get() = availableMinutes - requiredMinutes
}

/**
 * Minimum connection times. Airlines publish these per airport; without that
 * feed these are the industry rules of thumb, which are close enough to warn
 * on and honest about being estimates.
 */
object ConnectionRisk {

    private const val SAME_TERMINAL_DOMESTIC = 40
    private const val CHANGE_TERMINAL_DOMESTIC = 65
    private const val SAME_TERMINAL_INTERNATIONAL = 60
    private const val CHANGE_TERMINAL_INTERNATIONAL = 90
    private const val CHANGE_AIRPORT_BASE = 75
    private const val IMMIGRATION_SURCHARGE = 30
    private const val BAG_RECLAIM_SURCHARGE = 20

    fun requiredMinutes(
        inbound: Segment.Flight,
        onward: Segment.Flight,
        checkedBags: Boolean = true,
    ): Pair<Int, String> {
        val sameAirport = inbound.destination.code != null &&
            inbound.destination.code == onward.origin.code
        val international = inbound.origin.country != inbound.destination.country ||
            onward.origin.country != onward.destination.country
        val entersNewCountry = inbound.origin.country != inbound.destination.country

        if (!sameAirport) {
            val transfer = TransitEstimator.estimateBest(inbound.destination, onward.origin)
            val required = CHANGE_AIRPORT_BASE + transfer.totalMinutes +
                if (entersNewCountry) IMMIGRATION_SURCHARGE else 0
            return required to
                "Airport change via ${transfer.mode.label.lowercase()} (${TimeText.duration(transfer.totalMinutes)}) plus check-in"
        }

        val sameTerminal = inbound.arrivalTerminal != null &&
            inbound.arrivalTerminal == onward.departureTerminal
        var required = when {
            sameTerminal && !international -> SAME_TERMINAL_DOMESTIC
            sameTerminal -> SAME_TERMINAL_INTERNATIONAL
            !international -> CHANGE_TERMINAL_DOMESTIC
            else -> CHANGE_TERMINAL_INTERNATIONAL
        }
        val notes = mutableListOf(
            if (sameTerminal) {
                "Same terminal at ${inbound.destination.shortLabel}"
            } else {
                "Terminal change at ${inbound.destination.shortLabel}"
            }
        )
        if (entersNewCountry) {
            required += IMMIGRATION_SURCHARGE
            notes += "immigration"
        }
        val differentCarrier = inbound.carrierCode != onward.carrierCode
        if (differentCarrier && checkedBags) {
            required += BAG_RECLAIM_SURCHARGE
            notes += "bags re-checked between carriers"
        }
        return required to notes.joinToString(", ")
    }

    /**
     * Judge a connection using live times when they exist — a 90-minute
     * connection with a 70-minute inbound delay is not a 90-minute connection.
     */
    fun assess(
        inbound: Segment.Flight,
        onward: Segment.Flight,
        inboundStatus: FlightStatus? = null,
        onwardStatus: FlightStatus? = null,
        checkedBags: Boolean = true,
    ): ConnectionVerdict {
        val arrival = inboundStatus?.estimatedArrivalMillis ?: inbound.endEpochMillis
        val departure = onwardStatus?.estimatedDepartureMillis ?: onward.startEpochMillis
        val available = ((departure - arrival) / 60_000L).toInt()
        val (required, reason) = requiredMinutes(inbound, onward, checkedBags)
        val slack = available - required
        val level = when {
            available <= 0 -> RiskLevel.BROKEN
            slack < 0 -> RiskLevel.AT_RISK
            slack < 25 -> RiskLevel.TIGHT
            else -> RiskLevel.COMFORTABLE
        }
        val detail = when (level) {
            RiskLevel.BROKEN -> "The onward flight leaves before this one lands"
            RiskLevel.AT_RISK -> "$reason needs ${TimeText.duration(required)}"
            RiskLevel.TIGHT -> "$reason — ${TimeText.duration(slack)} of slack"
            RiskLevel.COMFORTABLE -> reason
        }
        return ConnectionVerdict(level, available, required, detail)
    }

    /** Ground-side version: any two segments with a gap between them. */
    fun assessGap(
        fromPlace: Place,
        toPlace: Place,
        availableMinutes: Int,
        bufferMinutes: Int = 15,
    ): ConnectionVerdict {
        val estimate = TransitEstimator.estimateBest(fromPlace, toPlace)
        val required = estimate.totalMinutes + bufferMinutes
        val slack = availableMinutes - required
        val level = when {
            availableMinutes <= 0 -> RiskLevel.BROKEN
            slack < 0 -> RiskLevel.AT_RISK
            slack < 20 -> RiskLevel.TIGHT
            else -> RiskLevel.COMFORTABLE
        }
        return ConnectionVerdict(level, availableMinutes, required, estimate.summary)
    }
}
