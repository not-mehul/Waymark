package com.waymark.ui.nav

import androidx.navigation3.runtime.NavKey
import com.waymark.data.share.BookingText
import com.waymark.ui.add.FlightDraft
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/**
 * Where the app can be, as types: a destination is a class, its arguments are
 * its fields, and the back stack is a plain list of them that the app owns
 * and edits directly. Each is serializable, so the list survives the process
 * being stopped in the background.
 *
 * Every screen of one trip carries the trip's id; [OfTrip] is how the
 * navigation finds the one view model they share.
 */
@Serializable
sealed interface Screen : NavKey {

    /** A screen inside a trip. */
    sealed interface OfTrip : Screen {
        val tripId: String
    }

    @Serializable
    data object Shelf : Screen

    @Serializable
    data object People : Screen

    @Serializable
    data class Trip(override val tripId: String) : OfTrip

    /** Add flight, optionally started from a flight read elsewhere. Dates and times as ISO text. */
    @Serializable
    data class AddFlight(
        override val tripId: String,
        val designator: String? = null,
        val date: String? = null,
        val origin: String? = null,
        val destination: String? = null,
        val departs: String? = null,
        val arrives: String? = null,
        val reference: String? = null,
    ) : OfTrip {
        fun draft(): FlightDraft? = if (designator == null && origin == null) {
            null
        } else {
            FlightDraft(
                designator = designator,
                date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                origin = origin,
                destination = destination,
                departs = departs?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                arrives = arrives?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                confirmationCode = reference,
            )
        }

        companion object {
            fun of(tripId: String, found: BookingText.FoundFlight, reference: String?) = AddFlight(
                tripId = tripId,
                designator = found.designator,
                date = found.date?.toString(),
                origin = found.origin,
                destination = found.destination,
                departs = found.departs?.toString(),
                arrives = found.arrives?.toString(),
                reference = reference,
            )
        }
    }

    @Serializable
    data class Booking(override val tripId: String, val segmentId: String) : OfTrip

    @Serializable
    data class Insights(override val tripId: String) : OfTrip

    @Serializable
    data class Numbers(override val tripId: String) : OfTrip

    @Serializable
    data class TripMap(override val tripId: String) : OfTrip
}
