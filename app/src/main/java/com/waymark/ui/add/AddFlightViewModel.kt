package com.waymark.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.Airports.Airport
import com.waymark.data.catalog.FlightCatalog
import com.waymark.data.repo.TripRepository
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
    val departTime: LocalTime? = null,
    val arriveTime: LocalTime? = null,

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

    /** Only the four facts a flight cannot exist without are required. */
    val canSave: Boolean
        get() = parsedDesignator != null &&
            originAirport != null &&
            destinationAirport != null &&
            originAirport?.code != destinationAirport?.code &&
            departTime != null &&
            arriveTime != null

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

}

class AddFlightViewModel(
    private val tripId: String,
    private val trips: TripRepository,
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

    fun setDepartTime(time: LocalTime) = mutable.update { it.copy(departTime = time) }

    fun setArriveTime(time: LocalTime) = mutable.update { it.copy(arriveTime = time) }

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
        viewModelScope.launch {
            trips.saveSegment(segment)
            mutable.update { it.copy(saved = true) }
            onSaved()
        }
    }

    private fun segmentOf(ui: AddFlightUiState): Segment.Flight? {
        val designator = ui.parsedDesignator ?: return null
        val origin = ui.originAirport?.toPlace() ?: return null
        val destination = ui.destinationAirport?.toPlace() ?: return null
        val departTime = ui.departTime ?: return null
        val arriveTime = ui.arriveTime ?: return null

        // `zoneOrUtc`, not `ZoneId.of`: the station directory is generated
        // data, and one row with a zone this device's tzdb has never heard of
        // would otherwise throw out of a composable and take the screen with
        // it. A flight in the wrong zone is a bug; a crash is worse.
        val departure = ZonedDateTime.of(ui.date, departTime, Segment.zoneOrUtc(origin.timeZoneId))
        var arrival =
            ZonedDateTime.of(ui.date, arriveTime, Segment.zoneOrUtc(destination.timeZoneId))
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
            ticketNumbers = ui.eTickets.filterValues { it.isNotBlank() },
            confirmationCode = ui.confirmationCode.trim().ifBlank { null },
            bookedWith = FlightCatalog.carrierName(designator.carrier),
            operatedBy = FlightCatalog.carrierName(designator.carrier),
        )
    }
}
