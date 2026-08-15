package com.waymark.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val destinationSummary: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val homeZoneId: String,
    val coverPlace: String?,
    val archived: Boolean = false,
)

@Entity(tableName = "travelers")
data class TravelerEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val nickname: String?,
    val seatPreference: String,
    val mealPreference: String?,
    val loyaltyRefs: String?,
    val documentRef: String?,
    val contactPhone: String?,
)

@Entity(
    tableName = "trip_members",
    primaryKeys = ["tripId", "travelerId"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TravelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("travelerId"), Index("tripId")],
)
data class TripMemberEntity(
    val tripId: String,
    val travelerId: String,
    val position: Int = 0,
)

/**
 * One table for every kind of segment. The alternative — a table per kind —
 * buys nothing here and costs four joins on the busiest read in the app.
 * [kind] decides which of the nullable columns are meaningful.
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("startEpochMillis")],
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val kind: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val startZoneId: String,
    val endZoneId: String,
    val travelerIds: String?,
    val reservationId: String?,
    val note: String?,
    val originPlace: String,
    val destinationPlace: String?,

    // Flight
    val carrierCode: String? = null,
    val flightNumber: String? = null,
    val departureTerminal: String? = null,
    val departureGate: String? = null,
    val arrivalTerminal: String? = null,
    val arrivalGate: String? = null,
    val aircraft: String? = null,
    val cabin: String? = null,
    val seats: String? = null,
    val operatedBy: String? = null,

    // Lodging
    val propertyName: String? = null,
    val roomDescription: String? = null,
    val checkInNote: String? = null,

    // Ground
    val groundMode: String? = null,
    val provider: String? = null,
    val pickupInstruction: String? = null,

    // Experience
    val experienceName: String? = null,
    val category: String? = null,
    val curatedBy: String? = null,
)

@Entity(
    tableName = "reservations",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("segmentId")],
)
data class ReservationEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val segmentId: String?,
    val label: String,
    val vendor: String,
    val kind: String,
    val travelerIds: String?,
    /** Sealed blob — never written or read in the clear. */
    val secretsSealed: String?,
    val documentUri: String?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "boarding_passes",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("segmentId"), Index("travelerId")],
)
data class BoardingPassEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val segmentId: String,
    val travelerId: String,
    val passengerName: String,
    val designator: String,
    val origin: String,
    val destination: String,
    val seat: String?,
    val boardingGroup: String?,
    val sequenceNumber: String?,
    val gate: String?,
    val boardingTimeMillis: Long?,
    val cabin: String?,
    val fastTrack: Boolean,
    /** Sealed BCBP payload. */
    val barcodeSealed: String,
    val imageUri: String?,
    val addedAtMillis: Long,
)

/**
 * The unscheduled half of a trip: places to see, food to try, walks to take.
 * Rows survive being promoted onto the timeline — [scheduledSegmentId] links
 * the two — so the list keeps its memory of why something was saved.
 */
@Entity(
    tableName = "ideas",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("status")],
)
data class IdeaEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val kind: String,
    val city: String,
    val place: String?,
    val note: String?,
    val priceBand: String?,
    val typicalMinutes: Int?,
    val bestTime: String?,
    val plannedDateEpochDay: Long?,
    val interestedTravelerIds: String?,
    val status: String,
    val scheduledSegmentId: String?,
    val source: String,
    val addedAtMillis: Long,
)

/**
 * Passports, visas, insurance. The number is sealed; the dates are not,
 * because an expiry warning that needs authentication to fire is a warning
 * that arrives at the airport.
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = TravelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["travelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("travelerId"), Index("expiresOnEpochDay")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val travelerId: String,
    val kind: String,
    val label: String,
    /** Sealed. */
    val numberSealed: String,
    val issuer: String?,
    val issuedOnEpochDay: Long?,
    val expiresOnEpochDay: Long?,
    val note: String?,
    val fileUri: String?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "packing_items",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("travelerId")],
)
data class PackingItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    /** Null means the item belongs to the party rather than one traveler. */
    val travelerId: String?,
    val title: String,
    val category: String,
    val quantity: Int,
    val packed: Boolean,
    val essential: Boolean,
    val note: String?,
    val source: String,
    val addedAtMillis: Long,
)

@Entity(tableName = "flight_status")
data class FlightStatusEntity(
    @PrimaryKey val segmentId: String,
    val designator: String,
    val state: String,
    val scheduledDepartureMillis: Long,
    val estimatedDepartureMillis: Long,
    val scheduledArrivalMillis: Long,
    val estimatedArrivalMillis: Long,
    val departureTerminal: String?,
    val departureGate: String?,
    val arrivalTerminal: String?,
    val arrivalGate: String?,
    val baggageBelt: String?,
    val boardingMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeFeet: Int?,
    val groundSpeedKnots: Int?,
    val headingDegrees: Int?,
    val progressPercent: Int,
    val observedAtMillis: Long,
    val source: String,
)

/**
 * Alerts already raised, so a delay that has not changed does not buzz the
 * traveler's pocket every fifteen minutes.
 */
@Entity(tableName = "raised_alerts")
data class RaisedAlertEntity(
    @PrimaryKey val signature: String,
    val segmentId: String,
    val designator: String,
    val headline: String,
    val detail: String,
    val severity: String,
    val raisedAtMillis: Long,
    val acknowledged: Boolean = false,
)
