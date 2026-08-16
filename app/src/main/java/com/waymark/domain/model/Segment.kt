package com.waymark.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SegmentKind { FLIGHT, LODGING, GROUND, EXPERIENCE }

enum class GroundMode {
    TRAIN, TRANSIT, BUS, FERRY, TAXI, RIDESHARE, RENTAL_CAR, SHUTTLE, WALK;

    val label: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * One reserved thing on a trip. Every segment carries its own start and end
 * zone: a red-eye that leaves on the 3rd and lands on the 4th is only legible
 * if both ends keep their own local clock.
 */
sealed class Segment {
    abstract val id: String
    abstract val tripId: String
    abstract val startEpochMillis: Long
    abstract val endEpochMillis: Long
    abstract val startZoneId: String
    abstract val endZoneId: String

    /** Traveler ids on this segment — the whole party, or a subset when itineraries split. */
    abstract val travelerIds: Set<String>

    /**
     * The booking reference, as printed on whatever confirmed it.
     *
     * This used to live in a separate encrypted `reservations` table behind a
     * biometric prompt. A code you cannot read without authenticating is a code
     * you cannot read at a check-in desk with your hands full, and the app has
     * no network to leak it to in the first place — so it is a field on the
     * booking now, like the terminal or the seat.
     */
    abstract val confirmationCode: String?

    /** Who it was booked through: an airline, an agent, "Direct booking". */
    abstract val bookedWith: String?

    abstract val note: String?

    val kind: SegmentKind
        get() = when (this) {
            is Flight -> SegmentKind.FLIGHT
            is Lodging -> SegmentKind.LODGING
            is Ground -> SegmentKind.GROUND
            is Experience -> SegmentKind.EXPERIENCE
        }

    val start: ZonedDateTime
        get() = Instant.ofEpochMilli(startEpochMillis).atZone(zoneOrUtc(startZoneId))

    val end: ZonedDateTime
        get() = Instant.ofEpochMilli(endEpochMillis).atZone(zoneOrUtc(endZoneId))

    val duration: Duration get() = Duration.ofMillis(endEpochMillis - startEpochMillis)

    /** Where this segment begins — an airport, a hotel, a trailhead. */
    abstract val origin: Place

    /** Where it ends. Same as [origin] for stationary segments (lodging, experiences). */
    abstract val destination: Place

    /** One-line title used in the timeline rail. */
    abstract val title: String

    data class Flight(
        override val id: String,
        override val tripId: String,
        val carrierCode: String,
        val flightNumber: String,
        override val origin: Place,
        override val destination: Place,
        override val startEpochMillis: Long,
        override val endEpochMillis: Long,
        override val startZoneId: String,
        override val endZoneId: String,
        override val travelerIds: Set<String> = emptySet(),
        override val confirmationCode: String? = null,
        override val bookedWith: String? = null,
        override val note: String? = null,
        /** Ticket number per traveler — an e-ticket is issued to a person. */
        val ticketNumbers: Map<String, String> = emptyMap(),
        val departureTerminal: String? = null,
        val departureGate: String? = null,
        val arrivalTerminal: String? = null,
        val arrivalGate: String? = null,
        val aircraft: String? = null,
        val cabin: String? = null,
        val seats: Map<String, String> = emptyMap(),
        val operatedBy: String? = null,
    ) : Segment() {
        val designator: String get() = "$carrierCode$flightNumber"
        override val title: String get() = "$designator · ${origin.shortLabel} → ${destination.shortLabel}"
    }

    data class Lodging(
        override val id: String,
        override val tripId: String,
        val propertyName: String,
        override val origin: Place,
        override val startEpochMillis: Long,
        override val endEpochMillis: Long,
        override val startZoneId: String,
        override val endZoneId: String,
        override val travelerIds: Set<String> = emptySet(),
        override val confirmationCode: String? = null,
        override val bookedWith: String? = null,
        override val note: String? = null,
        val roomDescription: String? = null,
        val checkInNote: String? = null,
    ) : Segment() {
        override val destination: Place get() = origin
        override val title: String get() = propertyName
        val nights: Long get() = maxOf(1L, Duration.ofMillis(endEpochMillis - startEpochMillis).toDays())
    }

    data class Ground(
        override val id: String,
        override val tripId: String,
        val mode: GroundMode,
        override val origin: Place,
        override val destination: Place,
        override val startEpochMillis: Long,
        override val endEpochMillis: Long,
        override val startZoneId: String,
        override val endZoneId: String,
        override val travelerIds: Set<String> = emptySet(),
        override val confirmationCode: String? = null,
        override val bookedWith: String? = null,
        override val note: String? = null,
        val provider: String? = null,
        val pickupInstruction: String? = null,
    ) : Segment() {
        override val title: String get() =
            "${mode.label} · ${origin.shortLabel} → ${destination.shortLabel}"
    }

    data class Experience(
        override val id: String,
        override val tripId: String,
        val name: String,
        val category: String,
        override val origin: Place,
        override val startEpochMillis: Long,
        override val endEpochMillis: Long,
        override val startZoneId: String,
        override val endZoneId: String,
        override val travelerIds: Set<String> = emptySet(),
        override val confirmationCode: String? = null,
        override val bookedWith: String? = null,
        override val note: String? = null,
        val curatedBy: String? = null,
    ) : Segment() {
        override val destination: Place get() = origin
        override val title: String get() = name
    }

    companion object {
        fun zoneOrUtc(id: String): ZoneId =
            runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))
    }
}

/** Sorted by clock instant — the only ordering that survives time-zone changes. */
fun <T : Segment> List<T>.chronological(): List<T> = sortedWith(
    compareBy({ it.startEpochMillis }, { it.endEpochMillis }, { it.id })
)

fun Segment.involves(travelerId: String): Boolean =
    travelerIds.isEmpty() || travelerId in travelerIds
