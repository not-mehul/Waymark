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
    /** Put away: off the shelf, kept whole, and back with one tap. */
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
    val confirmationCode: String?,
    val bookedWith: String?,
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
    val ticketNumbers: String? = null,
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
    val addedAtMillis: Long,
)

/**
 * Reminders already raised, so a flight that is still four hours out does not
 * buzz the traveler's pocket on every check.
 *
 * Raised and posted are different facts. A reminder is raised the first time
 * it falls due; it is posted once Android has actually shown it. One raised
 * while notifications were blocked stays unposted, and is posted on the next
 * check after they are allowed — if the booking has not started by then.
 */
@Entity(tableName = "raised_alerts", indices = [Index("segmentId")])
data class RaisedAlertEntity(
    @PrimaryKey val signature: String,
    val segmentId: String,
    /** What the booking is called: `BA286`, `The Bloomsbury Rooms`. */
    val label: String,
    val headline: String,
    val detail: String,
    /** When the booking starts, so the banner can count down rather than freeze. */
    val startEpochMillis: Long,
    val raisedAtMillis: Long,
    val posted: Boolean,
    /** Dismissed from the trip screen's banner by the traveler. */
    val acknowledged: Boolean,
)

/** Something one traveler paid for and the party shares. Amounts in the currency's smallest unit. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    /** A traveler id; not a foreign key, so a payer who leaves the trip is still owed. */
    val paidBy: String,
    /** Traveler ids; empty for the whole party. */
    val splitAmong: String?,
    val spentOnEpochDay: Long,
    val category: String,
    val addedAtMillis: Long,
)

/** The currency a trip's costs are settled in, once somebody has chosen one. */
@Entity(
    tableName = "trip_costs",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TripCostsEntity(
    @PrimaryKey val tripId: String,
    val settleCurrency: String,
)

/** How much of the trip's settling currency one unit of [currency] is worth. */
@Entity(
    tableName = "trip_rates",
    primaryKeys = ["tripId", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripRateEntity(
    val tripId: String,
    val currency: String,
    val perUnit: Double,
    val asOfEpochDay: Long,
    /** Typed by the traveler: a live rate never replaces it. */
    val manual: Boolean,
)

/**
 * A day's weather at a place, kept so the trip shows it with the radio off.
 * Places are keyed to a hundredth of a degree; nothing ties a row to a trip.
 */
@Entity(tableName = "weather_days", primaryKeys = ["placeKey", "epochDay"])
data class WeatherDayEntity(
    val placeKey: String,
    val epochDay: Long,
    val code: Int,
    val highC: Double,
    val lowC: Double,
    val rainChance: Int?,
    val rainMm: Double?,
    /** `FORECAST` or `LAST_YEAR`. */
    val basis: String,
    val fetchedAtMillis: Long,
)
