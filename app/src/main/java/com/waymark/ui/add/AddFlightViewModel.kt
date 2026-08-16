package com.waymark.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.Airports.Airport
import com.waymark.data.catalog.FlightCatalog
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository
import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Everything on this screen was typed by the person flying. Nothing is fetched,
 * guessed, or filled in from a schedule — the only thing the app contributes is
 * the coordinates and time zone behind an airport code, which is what lets the
 * same two letters put the flight on the map and on the right day.
 */
data class AddFlightUiState(
    val designator: String = "",
    val date: LocalDate = LocalDate.now(),
    val origin: String = "",
    val destination: String = "",
    val departTime: String = "",
    val arriveTime: String = "",

    // Everything below is optional detail, off the ticket.
    val departureTerminal: String = "",
    val arrivalTerminal: String = "",
    val aircraft: String = "",
    val cabin: String = "",

    val party: List<Traveler> = emptyList(),
    val selectedTravelers: Set<String> = emptySet(),
    val confirmationCode: String = "",
    val eTickets: Map<String, String> = emptyMap(),
    val seats: Map<String, String> = emptyMap(),
    val saved: Boolean = false,
) {
    val parsedDesignator: FlightDesignator? get() = FlightDesignator.parse(designator)
    val originAirport: Airport? get() = Airports.find(origin)
    val destinationAirport: Airport? get() = Airports.find(destination)
    val departure: LocalTime? get() = parseClock(departTime)
    val arrival: LocalTime? get() = parseClock(arriveTime)

    /** Only the four facts a flight cannot exist without are required. */
    val canSave: Boolean
        get() = parsedDesignator != null &&
            originAirport != null &&
            destinationAirport != null &&
            originAirport?.code != destinationAirport?.code &&
            departure != null &&
            arrival != null

    /** Station suggestions for whichever end is being typed into. */
    fun suggestionsFor(field: Field): List<Airport> {
        val query = if (field == Field.ORIGIN) origin else destination
        if (query.length < 2) return emptyList()
        val exact = Airports.find(query)
        // Once the code resolves exactly there is nothing left to suggest.
        if (exact != null && query.trim().length == 3) return emptyList()
        return Airports.search(query, limit = 4)
    }

    enum class Field { ORIGIN, DESTINATION }

    companion object {
        /** "0815", "08:15", "8:15" all mean the same thing to a tired traveler. */
        fun parseClock(text: String): LocalTime? {
            val digits = text.filter { it.isDigit() }
            if (digits.length !in 3..4) return null
            val padded = digits.padStart(4, '0')
            val hour = padded.substring(0, 2).toIntOrNull() ?: return null
            val minute = padded.substring(2, 4).toIntOrNull() ?: return null
            if (hour > 23 || minute > 59) return null
            return LocalTime.of(hour, minute)
        }
    }
}

class AddFlightViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val vault: VaultRepository,
) : ViewModel() {

    private val mutable = MutableStateFlow(AddFlightUiState())

    val state: StateFlow<AddFlightUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val party = trips.observeParty(tripId).map { it.travelers }.first()
            mutable.update {
                it.copy(party = party, selectedTravelers = party.map { p -> p.id }.toSet())
            }
        }
    }

    fun setDesignator(text: String) = mutable.update { it.copy(designator = text.uppercase()) }

    fun setDate(date: LocalDate) = mutable.update { it.copy(date = date) }

    fun setOrigin(text: String) = mutable.update { it.copy(origin = text.uppercase()) }

    fun setDestination(text: String) = mutable.update { it.copy(destination = text.uppercase()) }

    fun setDepartTime(text: String) = mutable.update { it.copy(departTime = text) }

    fun setArriveTime(text: String) = mutable.update { it.copy(arriveTime = text) }

    fun setDepartureTerminal(text: String) = mutable.update { it.copy(departureTerminal = text) }

    fun setArrivalTerminal(text: String) = mutable.update { it.copy(arrivalTerminal = text) }

    fun setAircraft(text: String) = mutable.update { it.copy(aircraft = text) }

    fun setCabin(text: String) = mutable.update { it.copy(cabin = text) }

    fun toggleTraveler(travelerId: String) {
        mutable.update { current ->
            val next = current.selectedTravelers.toMutableSet()
            if (!next.add(travelerId)) next.remove(travelerId)
            current.copy(selectedTravelers = next)
        }
    }

    fun setConfirmationCode(code: String) = mutable.update { it.copy(confirmationCode = code) }

    fun setETicket(travelerId: String, value: String) = mutable.update {
        it.copy(eTickets = it.eTickets + (travelerId to value))
    }

    fun setSeat(travelerId: String, value: String) = mutable.update {
        it.copy(seats = it.seats + (travelerId to value.uppercase()))
    }

    fun save(onSaved: () -> Unit) {
        val segment = segmentOf(mutable.value) ?: return
        val current = mutable.value

        viewModelScope.launch {
            // The vault record is written first so the segment can be stored
            // once, already pointing at it.
            val reservation = vault.recordFor(
                segment = segment,
                label = "${segment.designator} · ${segment.origin.shortLabel} → " +
                    segment.destination.shortLabel,
                vendor = FlightCatalog.carrierName(segment.carrierCode),
                confirmationCode = current.confirmationCode.ifBlank { null },
                eTicketNumbers = current.eTickets.filterValues { it.isNotBlank() },
            )
            trips.saveSegment(segment.copy(reservationId = reservation.id))
            mutable.update { it.copy(saved = true) }
            onSaved()
        }
    }

    private fun segmentOf(ui: AddFlightUiState): Segment.Flight? {
        val designator = ui.parsedDesignator ?: return null
        val origin = ui.originAirport?.toPlace() ?: return null
        val destination = ui.destinationAirport?.toPlace() ?: return null
        val departTime = ui.departure ?: return null
        val arriveTime = ui.arrival ?: return null

        val departure = ZonedDateTime.of(ui.date, departTime, ZoneId.of(origin.timeZoneId))
        var arrival = ZonedDateTime.of(ui.date, arriveTime, ZoneId.of(destination.timeZoneId))
        // An arrival at or before departure means it lands the next day. A
        // westbound crossing of the date line can legitimately land "before"
        // it left in local terms, so only the instant comparison is trusted.
        if (!arrival.toInstant().isAfter(departure.toInstant())) arrival = arrival.plusDays(1)

        return Segment.Flight(
            id = TripRepository.newId("seg"),
            tripId = tripId,
            carrierCode = designator.carrier,
            flightNumber = designator.number.toString(),
            origin = origin,
            destination = destination,
            startEpochMillis = departure.toInstant().toEpochMilli(),
            endEpochMillis = arrival.toInstant().toEpochMilli(),
            startZoneId = origin.timeZoneId,
            endZoneId = destination.timeZoneId,
            travelerIds = ui.selectedTravelers,
            departureTerminal = ui.departureTerminal.trim().ifBlank { null },
            arrivalTerminal = ui.arrivalTerminal.trim().ifBlank { null },
            aircraft = ui.aircraft.trim().ifBlank { null },
            cabin = ui.cabin.trim().ifBlank { null },
            seats = ui.seats.filterValues { it.isNotBlank() },
            operatedBy = FlightCatalog.carrierName(designator.carrier),
        )
    }
}
