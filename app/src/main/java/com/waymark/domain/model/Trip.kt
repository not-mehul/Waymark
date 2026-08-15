package com.waymark.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Trip(
    val id: String,
    val name: String,
    val destinationSummary: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val homeZoneId: String = ZoneId.systemDefault().id,
    val coverPlace: Place? = null,
    val archived: Boolean = false,
) {
    fun startDate(): LocalDate =
        Instant.ofEpochMilli(startEpochMillis).atZone(Segment.zoneOrUtc(homeZoneId)).toLocalDate()

    fun endDate(): LocalDate =
        Instant.ofEpochMilli(endEpochMillis).atZone(Segment.zoneOrUtc(homeZoneId)).toLocalDate()

    fun status(now: Instant = Instant.now()): TripStatus = when {
        now.toEpochMilli() < startEpochMillis -> TripStatus.UPCOMING
        now.toEpochMilli() > endEpochMillis -> TripStatus.PAST
        else -> TripStatus.ACTIVE
    }

    /** Whole days until departure; negative once under way. */
    fun daysUntilStart(now: Instant = Instant.now()): Long {
        val today = now.atZone(Segment.zoneOrUtc(homeZoneId)).toLocalDate()
        return ChronoUnit.DAYS.between(today, startDate())
    }
}

enum class TripStatus { UPCOMING, ACTIVE, PAST }

/** A trip with everything needed to render it, assembled by the repository. */
data class TripDossier(
    val trip: Trip,
    val party: TripParty,
    val segments: List<Segment>,
    val statuses: Map<String, FlightStatus> = emptyMap(),
) {
    fun statusFor(segment: Segment): FlightStatus? = statuses[segment.id]
}
