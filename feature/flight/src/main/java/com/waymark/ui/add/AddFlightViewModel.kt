package com.waymark.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Airlines
import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.Airports.Airport
import com.waymark.data.repo.TripRepository
import com.waymark.domain.logic.FlightDesignator
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Null while the arrival day is inferred from the clock times. */
    val arrivalDayOffset: Int? = null,

    // Everything below is optional detail, off the ticket.
    val departureTerminal: String = "",
    val arrivalTerminal: String = "",
    val aircraft: String = "",
    val cabin: String = "",
    val operatedBy: String = "",

    val party: List<Traveler> = emptyList(),
    val selectedTravelers: Set<String> = emptySet(),
    val confirmationCode: String = "",
    val eTickets: Map<String, String> = emptyMap(),
    val seats: Map<String, String> = emptyMap(),
    val originSuggestions: List<Airport> = emptyList(),
    val destinationSuggestions: List<Airport> = emptyList(),
    val saving: Boolean = false,
) {
    val parsedDesignator: FlightDesignator? get() = FlightDesignator.parse(designator)
    val originAirport: Airport? get() = Airports.find(origin)
    val destinationAirport: Airport? get() = Airports.find(destination)

    /** The flight as the times describe it, or null while they cannot. */
    val leg: LegTimes.Leg?
        get() {
            val from = originAirport ?: return null
            val to = destinationAirport ?: return null
            return LegTimes.flight(
                date = date,
                departs = departTime ?: return null,
                departureZone = Segment.zoneOrUtc(from.timeZoneId),
                arrives = arriveTime ?: return null,
                arrivalZone = Segment.zoneOrUtc(to.timeZoneId),
                dayOffset = arrivalDayOffset,
            )
        }

    /** Why the times do not make a flight, once there are enough of them to judge. */
    val timeProblem: String?
        get() = when {
            originAirport == null || destinationAirport == null -> null
            departTime == null || arriveTime == null -> null
            leg != null -> null
            arrivalDayOffset != null -> "That arrival day lands before it leaves, or after a day in the air."
            else -> "These times do not make a flight between these airports. Check the zones."
        }

    /** Only the four facts a flight cannot exist without are required. */
    val canSave: Boolean
        get() = !saving &&
            parsedDesignator != null &&
            originAirport != null &&
            destinationAirport != null &&
            originAirport?.code != destinationAirport?.code &&
            leg != null

    enum class Field { ORIGIN, DESTINATION }
}

@HiltViewModel(assistedFactory = AddFlightViewModel.Factory::class)
class AddFlightViewModel @AssistedInject constructor(
    @Assisted private val tripId: String,
    private val trips: TripRepository,
    /** A flight read from an email or a boarding pass, to start from. */
    @Assisted initial: FlightDraft?,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(tripId: String, initial: FlightDraft?): AddFlightViewModel
    }

    private val mutable = MutableStateFlow(AddFlightUiState())

    val state: StateFlow<AddFlightUiState> = mutable.asStateFlow()

    private var originSearch: Job? = null
    private var destinationSearch: Job? = null

    init {
        viewModelScope.launch {
            val dossier = trips.observeDossier(tripId).first() ?: return@launch
            val party = dossier.party.travelers
            // The trip's first day, or today once the trip is under way: a
            // flight being added is almost never one that has already gone.
            val today = LocalDate.now()
            val trip = dossier.trip
            val date = when {
                today.isBefore(trip.startDate()) -> trip.startDate()
                today.isAfter(trip.endDate()) -> trip.startDate()
                else -> today
            }
            mutable.update {
                it.copy(party = party, selectedTravelers = party.map { p -> p.id }.toSet(), date = date)
            }
            // After the trip's defaults, so a date read from the email wins
            // over the trip's first day.
            initial?.let(::prefill)
        }
    }

    fun setDesignator(text: String) = mutable.update { it.copy(designator = text.uppercase()) }

    fun setDate(date: LocalDate) = mutable.update { it.copy(date = date) }

    fun setOrigin(text: String) {
        mutable.update { it.copy(origin = text.uppercase()) }
        originSearch?.cancel()
        originSearch = search(text) { found -> copy(originSuggestions = found) }
    }

    fun setDestination(text: String) {
        mutable.update { it.copy(destination = text.uppercase()) }
        destinationSearch?.cancel()
        destinationSearch = search(text) { found -> copy(destinationSuggestions = found) }
    }

    /**
     * Station suggestions, a beat after typing stops and off the main thread:
     * the directory is nine thousand rows, and a keystroke should not wait on
     * a scan of all of them.
     */
    private fun search(
        query: String,
        apply: AddFlightUiState.(List<Airport>) -> AddFlightUiState,
    ): Job = viewModelScope.launch {
        val trimmed = query.trim()
        val found = if (trimmed.length < 2 ||
            (trimmed.length == 3 && Airports.find(trimmed) != null)
        ) {
            // Once the code resolves exactly there is nothing left to suggest.
            emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MILLIS)
            withContext(Dispatchers.Default) { Airports.search(trimmed, limit = 4) }
        }
        mutable.update { it.apply(found) }
    }

    fun setDepartTime(time: LocalTime) = mutable.update { it.copy(departTime = time) }

    fun setArriveTime(time: LocalTime) = mutable.update { it.copy(arriveTime = time) }

    /** Tapping the inferred day again hands the choice back to the inference. */
    fun setArrivalDayOffset(days: Int?) = mutable.update {
        it.copy(arrivalDayOffset = if (it.arrivalDayOffset == days) null else days)
    }

    fun setDepartureTerminal(text: String) = mutable.update { it.copy(departureTerminal = text) }

    fun setArrivalTerminal(text: String) = mutable.update { it.copy(arrivalTerminal = text) }

    fun setAircraft(text: String) = mutable.update { it.copy(aircraft = text) }

    fun setCabin(text: String) = mutable.update { it.copy(cabin = text) }

    fun setOperatedBy(text: String) = mutable.update { it.copy(operatedBy = text) }

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

    /** Pre-fill from something parsed elsewhere — a pasted email, a boarding pass. */
    fun prefill(draft: FlightDraft) {
        mutable.update { current ->
            current.copy(
                designator = draft.designator ?: current.designator,
                date = draft.date ?: current.date,
                origin = draft.origin ?: current.origin,
                destination = draft.destination ?: current.destination,
                departTime = draft.departs ?: current.departTime,
                arriveTime = draft.arrives ?: current.arriveTime,
                confirmationCode = draft.confirmationCode ?: current.confirmationCode,
                cabin = draft.cabin ?: current.cabin,
                seats = draft.seat?.let { seat ->
                    current.selectedTravelers.singleOrNull()?.let { mapOf(it to seat) }
                } ?: current.seats,
            )
        }
    }

    /** Saved once: a second tap while the first write is in flight does nothing. */
    fun save(onSaved: () -> Unit) {
        val current = mutable.value
        if (current.saving) return
        val segment = segmentOf(current) ?: return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            trips.saveSegment(segment)
            onSaved()
        }
    }

    private fun segmentOf(ui: AddFlightUiState): Segment.Flight? {
        val designator = ui.parsedDesignator ?: return null
        val origin = ui.originAirport?.toPlace() ?: return null
        val destination = ui.destinationAirport?.toPlace() ?: return null
        val leg = ui.leg ?: return null
        val travelers = ui.party.map { it.id }.toSet()

        return Segment.Flight(
            id = TripRepository.newId("seg"),
            tripId = tripId,
            carrierCode = designator.carrier,
            // The suffix is part of the number: LH1234A is not LH1234.
            flightNumber = "${designator.number}${designator.suffix.orEmpty()}",
            origin = origin,
            destination = destination,
            startEpochMillis = leg.departure.toInstant().toEpochMilli(),
            endEpochMillis = leg.arrival.toInstant().toEpochMilli(),
            startZoneId = origin.timeZoneId,
            endZoneId = destination.timeZoneId,
            // Everyone ticked is stored as everyone, so a traveler added to the
            // trip later is on this flight too unless somebody says otherwise.
            travelerIds = if (ui.selectedTravelers == travelers) emptySet() else ui.selectedTravelers,
            departureTerminal = ui.departureTerminal.trim().ifBlank { null },
            arrivalTerminal = ui.arrivalTerminal.trim().ifBlank { null },
            aircraft = ui.aircraft.trim().ifBlank { null },
            cabin = ui.cabin.trim().ifBlank { null },
            seats = ui.seats.filterValues { it.isNotBlank() },
            ticketNumbers = ui.eTickets.filterValues { it.isNotBlank() },
            confirmationCode = ui.confirmationCode.trim().ifBlank { null },
            bookedWith = Airlines.name(designator.carrier),
            // Only when the traveler says so: the marketing carrier is not
            // evidence of who flies the aircraft.
            operatedBy = ui.operatedBy.trim().ifBlank { null },
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 150L
    }
}

/** A flight half-known from somewhere else, waiting to be checked and saved. */
data class FlightDraft(
    val designator: String? = null,
    val date: LocalDate? = null,
    val origin: String? = null,
    val destination: String? = null,
    val departs: LocalTime? = null,
    val arrives: LocalTime? = null,
    val confirmationCode: String? = null,
    val cabin: String? = null,
    val seat: String? = null,
)
