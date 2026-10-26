package com.waymark.ui.segment

import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.Places
import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.copyCommon
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Everything the edit screen can change on a booking, as plain values.
 *
 * Held as one object so the screen keeps a single piece of saved state that
 * survives rotation and process death, and so turning it back into a booking
 * — the part with rules in it — is a function tested on the JVM rather than
 * logic spread across text fields.
 *
 * Times are a date and a clock time **in the booking's own zones**, which is
 * how they were entered and how they read on the timeline. Places are the
 * words as shown; [apply] keeps the stored place when the words are
 * unchanged, so editing a hotel's room never loses the hotel's map pin.
 */
data class SegmentForm(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val travelerIds: Set<String>,
    val note: String = "",
    val confirmationCode: String = "",
    val bookedWith: String = "",
    /** The flight number, the hotel, the experience, or a ground leg's operator. */
    val name: String = "",
    /** Departure airport code, or where a stay, a booking or a ground leg is. */
    val where: String = "",
    /** Arrival airport code, or where a ground leg ends. */
    val destination: String = "",
    val address: String = "",
    val departureTerminal: String = "",
    val departureGate: String = "",
    val arrivalTerminal: String = "",
    val arrivalGate: String = "",
    val aircraft: String = "",
    val cabin: String = "",
    val operatedBy: String = "",
    val seats: Map<String, String> = emptyMap(),
    val tickets: Map<String, String> = emptyMap(),
    val room: String = "",
    val checkInNote: String = "",
    val mode: GroundMode = GroundMode.TRAIN,
    val pickup: String = "",
    val category: String = "",
    val tips: String = "",
) : Serializable {

    /** Why the form cannot be saved yet, or null when it can. */
    sealed interface Problem {
        data object NoName : Problem
        data object NoTraveler : Problem
        data class UnknownAirport(val code: String) : Problem
        data object NotAFlightNumber : Problem
        data object EndsBeforeStart : Problem
        data object TooLongForAFlight : Problem
    }

    sealed interface Outcome {
        data class Saved(val segment: Segment) : Outcome
        data class Refused(val problem: Problem) : Outcome
    }

    /**
     * The booking as edited, or the first reason it cannot be.
     *
     * An end that is not after the start but would be a day later is read as
     * running past midnight, the same rule Add plan uses; anything further out
     * of order is refused rather than moved by days. A traveler deselected
     * from everyone is refused too — a booking with no passenger is a mistake
     * nobody means.
     */
    fun apply(original: Segment, party: List<Traveler>): Outcome {
        if (party.isNotEmpty() && travelerIds.isEmpty()) return Outcome.Refused(Problem.NoTraveler)
        val named = original is Segment.Lodging || original is Segment.Experience
        if (named && name.isBlank()) return Outcome.Refused(Problem.NoName)

        val origin: Place
        val arrival: Place
        when (original) {
            is Segment.Flight -> {
                origin = airport(where, original.origin) ?: return Outcome.Refused(Problem.UnknownAirport(where.trim()))
                arrival = airport(destination, original.destination)
                    ?: return Outcome.Refused(Problem.UnknownAirport(destination.trim()))
            }

            is Segment.Ground -> {
                origin = place(where, original.origin, near = original.origin)
                arrival = place(destination, original.destination, near = origin)
            }

            else -> {
                origin = place(where, original.origin, near = original.origin)
                arrival = origin
            }
        }

        val start = ZonedDateTime.of(startDate, startTime, origin.zone)
        val end = LegTimes.endAfter(start, LocalDateTime.of(endDate, endTime), arrival.zone)
        if (!LegTimes.isOrdered(start, end)) return Outcome.Refused(Problem.EndsBeforeStart)

        val common = original.copyCommon(
            startEpochMillis = start.toInstant().toEpochMilli(),
            endEpochMillis = end.toInstant().toEpochMilli(),
            startZoneId = origin.timeZoneId,
            endZoneId = arrival.timeZoneId,
            travelerIds = travelerIds,
            confirmationCode = confirmationCode.clean(),
            bookedWith = bookedWith.clean(),
            note = note.clean(),
        )

        val edited = when (common) {
            is Segment.Flight -> {
                val designator = FlightDesignator.parse(name) ?: return Outcome.Refused(Problem.NotAFlightNumber)
                val minutes = (common.endEpochMillis - common.startEpochMillis) / 60_000
                if (minutes > LegTimes.MAX_FLIGHT_MINUTES) return Outcome.Refused(Problem.TooLongForAFlight)
                val onBoard = travelerIds.ifEmpty { party.map { it.id }.toSet() }
                common.copy(
                    carrierCode = designator.carrier,
                    flightNumber = "${designator.number}${designator.suffix.orEmpty()}",
                    origin = origin,
                    destination = arrival,
                    departureTerminal = departureTerminal.clean(),
                    departureGate = departureGate.clean()?.uppercase(),
                    arrivalTerminal = arrivalTerminal.clean(),
                    arrivalGate = arrivalGate.clean()?.uppercase(),
                    aircraft = aircraft.clean(),
                    cabin = cabin.clean(),
                    operatedBy = operatedBy.clean(),
                    // Only the people on the flight keep a seat or a ticket.
                    seats = seats.cleaned().filterKeys { it in onBoard },
                    ticketNumbers = tickets.cleaned().filterKeys { it in onBoard },
                )
            }

            is Segment.Lodging -> common.copy(
                propertyName = name.trim(),
                origin = origin.copy(address = address.clean()),
                endZoneId = origin.timeZoneId,
                roomDescription = room.clean(),
                checkInNote = checkInNote.clean(),
            )

            is Segment.Ground -> common.copy(
                mode = mode,
                origin = origin,
                destination = arrival,
                provider = name.clean(),
                pickupInstruction = pickup.clean(),
            )

            is Segment.Experience -> common.copy(
                name = name.trim(),
                category = category.clean() ?: common.category,
                origin = origin.copy(address = address.clean()),
                endZoneId = origin.timeZoneId,
                curatedBy = tips.clean(),
            )
        }
        return Outcome.Saved(edited)
    }

    companion object {
        private const val serialVersionUID = 1L

        /** The form for [segment], with "everyone" spelled out so the chips show it. */
        fun of(segment: Segment, party: List<Traveler>): SegmentForm {
            val base = SegmentForm(
                startDate = segment.start.toLocalDate(),
                startTime = segment.start.toLocalTime(),
                endDate = segment.end.toLocalDate(),
                endTime = segment.end.toLocalTime(),
                travelerIds = segment.travelerIds.ifEmpty { party.map { it.id }.toSet() },
                note = segment.note.orEmpty(),
                confirmationCode = segment.confirmationCode.orEmpty(),
                bookedWith = segment.bookedWith.orEmpty(),
                where = placeText(segment.origin),
                destination = placeText(segment.destination),
                address = segment.origin.address.orEmpty(),
            )
            return when (segment) {
                is Segment.Flight -> base.copy(
                    name = segment.designator,
                    departureTerminal = segment.departureTerminal.orEmpty(),
                    departureGate = segment.departureGate.orEmpty(),
                    arrivalTerminal = segment.arrivalTerminal.orEmpty(),
                    arrivalGate = segment.arrivalGate.orEmpty(),
                    aircraft = segment.aircraft.orEmpty(),
                    cabin = segment.cabin.orEmpty(),
                    operatedBy = segment.operatedBy.orEmpty(),
                    seats = segment.seats,
                    tickets = segment.ticketNumbers,
                )

                is Segment.Lodging -> base.copy(
                    name = segment.propertyName,
                    room = segment.roomDescription.orEmpty(),
                    checkInNote = segment.checkInNote.orEmpty(),
                )

                is Segment.Ground -> base.copy(
                    name = segment.provider.orEmpty(),
                    mode = segment.mode,
                    pickup = segment.pickupInstruction.orEmpty(),
                )

                is Segment.Experience -> base.copy(
                    name = segment.name,
                    category = segment.category,
                    tips = segment.curatedBy.orEmpty(),
                )
            }
        }

        /** An airport is shown, and typed, as its code. */
        private fun placeText(place: Place): String = place.code ?: place.name

        private fun airport(text: String, original: Place): Place? {
            val code = text.trim().uppercase()
            if (code == original.code) return original
            return Airports.find(code)?.toPlace()
        }

        /** The stored place while the words are unchanged; otherwise what the words now mean. */
        private fun place(text: String, original: Place, near: Place): Place {
            val typed = text.trim()
            if (typed.isEmpty() || typed == placeText(original)) return original
            return Places.resolve(typed, near.position, near.timeZoneId).place
        }

        private fun String.clean(): String? = trim().ifBlank { null }

        private fun Map<String, String>.cleaned(): Map<String, String> =
            mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    }
}
