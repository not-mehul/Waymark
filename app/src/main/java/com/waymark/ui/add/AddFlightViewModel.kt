package com.waymark.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.FlightCatalog
import com.waymark.data.repo.FlightLookup
import com.waymark.data.repo.FlightRepository
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

data class AddFlightUiState(
    val input: String = "",
    val date: LocalDate = LocalDate.now(),
    val plan: FlightCatalog.FlightPlan? = null,
    val unknownDesignator: FlightDesignator? = null,
    val parseError: Boolean = false,
    val searching: Boolean = false,
    val party: List<Traveler> = emptyList(),
    val selectedTravelers: Set<String> = emptySet(),
    val confirmationCode: String = "",
    val eTickets: Map<String, String> = emptyMap(),
    val seats: Map<String, String> = emptyMap(),
    val saved: Boolean = false,

    // Manual entry, for a flight the catalog has never met.
    val manualOrigin: String = "",
    val manualDestination: String = "",
    val manualDepartTime: String = "",
    val manualArriveTime: String = "",
) {
    val canLookUp: Boolean get() = FlightDesignator.looksComplete(input)
    val canSaveManually: Boolean
        get() = unknownDesignator != null &&
            Airports.find(manualOrigin) != null &&
            Airports.find(manualDestination) != null &&
            parseClock(manualDepartTime) != null &&
            parseClock(manualArriveTime) != null

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

/**
 * Type a flight number, get a flight. The catalog answers instantly and
 * offline; the network provider refines the result when it is available.
 */
class AddFlightViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val vault: VaultRepository,
    private val flights: FlightRepository,
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

    fun onInputChange(text: String) {
        mutable.update {
            it.copy(
                input = text.uppercase(),
                plan = null,
                unknownDesignator = null,
                parseError = false,
                saved = false,
            )
        }
    }

    fun onDateChange(date: LocalDate) {
        mutable.update { it.copy(date = date, plan = null) }
        if (mutable.value.canLookUp) lookUp()
    }

    fun lookUp() {
        val current = mutable.value
        viewModelScope.launch {
            mutable.update { it.copy(searching = true) }
            when (val result = flights.lookup(current.input, current.date)) {
                is FlightLookup.Found -> mutable.update {
                    it.copy(
                        plan = result.plan,
                        unknownDesignator = null,
                        parseError = false,
                        searching = false,
                    )
                }

                is FlightLookup.Unknown -> mutable.update {
                    it.copy(
                        plan = null,
                        unknownDesignator = result.designator,
                        parseError = false,
                        searching = false,
                    )
                }

                FlightLookup.Unparseable -> mutable.update {
                    it.copy(plan = null, unknownDesignator = null, parseError = true, searching = false)
                }
            }
        }
    }

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

    fun setManual(
        origin: String? = null,
        destination: String? = null,
        departTime: String? = null,
        arriveTime: String? = null,
    ) = mutable.update {
        it.copy(
            manualOrigin = origin?.uppercase() ?: it.manualOrigin,
            manualDestination = destination?.uppercase() ?: it.manualDestination,
            manualDepartTime = departTime ?: it.manualDepartTime,
            manualArriveTime = arriveTime ?: it.manualArriveTime,
        )
    }

    /** Save the looked-up plan, or the hand-entered one, as a segment. */
    fun save(onSaved: () -> Unit) {
        val current = mutable.value
        val segment = current.plan?.let { toSegment(it, current) } ?: manualSegment(current)
        if (segment == null) return

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
            val stored = segment.copy(reservationId = reservation.id)
            trips.saveSegment(stored)
            flights.refresh(listOf(stored))
            mutable.update { it.copy(saved = true) }
            onSaved()
        }
    }

    private fun toSegment(plan: FlightCatalog.FlightPlan, ui: AddFlightUiState): Segment.Flight =
        Segment.Flight(
            id = TripRepository.newId("seg"),
            tripId = tripId,
            carrierCode = plan.designator.takeWhile { !it.isDigit() },
            flightNumber = plan.designator.dropWhile { !it.isDigit() },
            origin = plan.origin,
            destination = plan.destination,
            startEpochMillis = plan.departure.toInstant().toEpochMilli(),
            endEpochMillis = plan.arrival.toInstant().toEpochMilli(),
            startZoneId = plan.origin.timeZoneId,
            endZoneId = plan.destination.timeZoneId,
            travelerIds = ui.selectedTravelers,
            departureTerminal = plan.departureTerminal,
            arrivalTerminal = plan.arrivalTerminal,
            aircraft = plan.aircraft,
            cabin = plan.cabins.firstOrNull(),
            seats = ui.seats.filterValues { it.isNotBlank() },
            operatedBy = plan.carrierName,
        )

    private fun manualSegment(ui: AddFlightUiState): Segment.Flight? {
        val designator = ui.unknownDesignator ?: return null
        val origin = Airports.find(ui.manualOrigin)?.toPlace() ?: return null
        val destination = Airports.find(ui.manualDestination)?.toPlace() ?: return null
        val departTime = AddFlightUiState.parseClock(ui.manualDepartTime) ?: return null
        val arriveTime = AddFlightUiState.parseClock(ui.manualArriveTime) ?: return null

        val departure = ZonedDateTime.of(ui.date, departTime, ZoneId.of(origin.timeZoneId))
        var arrival = ZonedDateTime.of(ui.date, arriveTime, ZoneId.of(destination.timeZoneId))
        // An arrival before departure means it lands the next day.
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
            seats = ui.seats.filterValues { it.isNotBlank() },
            operatedBy = FlightCatalog.carrierName(designator.carrier),
        )
    }
}
