package com.waymark.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Airports
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
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

data class AddPlanUiState(
    val kind: SegmentKind = SegmentKind.LODGING,
    val title: String = "",
    val vendor: String = "",
    val originQuery: String = "",
    val destinationQuery: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(1),
    val startTime: String = "15:00",
    val endTime: String = "11:00",
    val mode: GroundMode = GroundMode.TRAIN,
    val category: String = "",
    val note: String = "",
    val confirmationCode: String = "",
    val party: List<Traveler> = emptyList(),
    val selectedTravelers: Set<String> = emptySet(),
    val zoneId: String = ZoneId.systemDefault().id,
) {
    val needsDestination: Boolean get() = kind == SegmentKind.GROUND
    val canSave: Boolean
        get() = title.isNotBlank() &&
            AddFlightUiState.parseClock(startTime) != null &&
            AddFlightUiState.parseClock(endTime) != null
}

/**
 * Everything that is not a flight: a hotel, a train, a table booked for
 * Thursday. The same three questions each time — what, where, when — with the
 * fields that do not apply left out rather than greyed out.
 */
class AddPlanViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val vault: VaultRepository,
) : ViewModel() {

    private val mutable = MutableStateFlow(AddPlanUiState())
    val state: StateFlow<AddPlanUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val party = trips.observeParty(tripId).map { it.travelers }.first()
            mutable.update {
                it.copy(party = party, selectedTravelers = party.map { p -> p.id }.toSet())
            }
        }
    }

    fun setKind(kind: SegmentKind) = mutable.update {
        it.copy(
            kind = kind,
            startTime = if (kind == SegmentKind.LODGING) "15:00" else "09:00",
            endTime = if (kind == SegmentKind.LODGING) "11:00" else "11:00",
            endDate = if (kind == SegmentKind.LODGING) it.startDate.plusDays(1) else it.startDate,
        )
    }

    fun setTitle(value: String) = mutable.update { it.copy(title = value) }
    fun setVendor(value: String) = mutable.update { it.copy(vendor = value) }
    fun setOrigin(value: String) = mutable.update { it.copy(originQuery = value) }
    fun setDestination(value: String) = mutable.update { it.copy(destinationQuery = value) }
    fun setStartDate(value: LocalDate) = mutable.update {
        it.copy(startDate = value, endDate = if (it.endDate < value) value else it.endDate)
    }
    fun setEndDate(value: LocalDate) = mutable.update { it.copy(endDate = value) }
    fun setStartTime(value: String) = mutable.update { it.copy(startTime = value) }
    fun setEndTime(value: String) = mutable.update { it.copy(endTime = value) }
    fun setMode(mode: GroundMode) = mutable.update { it.copy(mode = mode) }
    fun setCategory(value: String) = mutable.update { it.copy(category = value) }
    fun setNote(value: String) = mutable.update { it.copy(note = value) }
    fun setConfirmationCode(value: String) = mutable.update { it.copy(confirmationCode = value) }

    fun toggleTraveler(travelerId: String) = mutable.update { current ->
        val next = current.selectedTravelers.toMutableSet()
        if (!next.add(travelerId)) next.remove(travelerId)
        current.copy(selectedTravelers = next)
    }

    fun save(onSaved: () -> Unit) {
        val ui = mutable.value
        if (!ui.canSave) return
        val origin = resolvePlace(ui.originQuery, ui.title, ui.zoneId)
        val destination = if (ui.needsDestination) {
            resolvePlace(ui.destinationQuery, ui.destinationQuery, ui.zoneId)
        } else {
            origin
        }
        val zone = runCatching { ZoneId.of(origin.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val endZone = runCatching { ZoneId.of(destination.timeZoneId) }.getOrDefault(zone)

        val start = ZonedDateTime.of(
            ui.startDate,
            AddFlightUiState.parseClock(ui.startTime) ?: LocalTime.NOON,
            zone,
        )
        var end = ZonedDateTime.of(
            if (ui.kind == SegmentKind.LODGING) ui.endDate else ui.startDate,
            AddFlightUiState.parseClock(ui.endTime) ?: LocalTime.NOON,
            endZone,
        )
        if (!end.toInstant().isAfter(start.toInstant())) end = end.plusDays(1)

        val id = TripRepository.newId("seg")
        val segment: Segment = when (ui.kind) {
            SegmentKind.LODGING -> Segment.Lodging(
                id = id,
                tripId = tripId,
                propertyName = ui.title.trim(),
                origin = origin,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = zone.id,
                endZoneId = zone.id,
                travelerIds = ui.selectedTravelers,
                note = ui.note.ifBlank { null },
            )

            SegmentKind.GROUND -> Segment.Ground(
                id = id,
                tripId = tripId,
                mode = ui.mode,
                origin = origin,
                destination = destination,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = zone.id,
                endZoneId = endZone.id,
                travelerIds = ui.selectedTravelers,
                note = ui.note.ifBlank { null },
                provider = ui.vendor.ifBlank { null },
            )

            SegmentKind.EXPERIENCE, SegmentKind.FLIGHT -> Segment.Experience(
                id = id,
                tripId = tripId,
                name = ui.title.trim(),
                category = ui.category.ifBlank { "Experience" },
                origin = origin,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = zone.id,
                endZoneId = zone.id,
                travelerIds = ui.selectedTravelers,
                note = ui.note.ifBlank { null },
            )
        }

        viewModelScope.launch {
            val reservation = if (ui.confirmationCode.isNotBlank()) {
                vault.recordFor(
                    segment = segment,
                    label = ui.title.trim(),
                    vendor = ui.vendor.ifBlank { "Direct booking" },
                    confirmationCode = ui.confirmationCode,
                )
            } else {
                null
            }
            trips.saveSegment(withReservation(segment, reservation?.id))
            onSaved()
        }
    }

    private fun withReservation(segment: Segment, reservationId: String?): Segment =
        if (reservationId == null) {
            segment
        } else {
            when (segment) {
                is Segment.Flight -> segment.copy(reservationId = reservationId)
                is Segment.Lodging -> segment.copy(reservationId = reservationId)
                is Segment.Ground -> segment.copy(reservationId = reservationId)
                is Segment.Experience -> segment.copy(reservationId = reservationId)
            }
        }

    /**
     * A station code resolves to a real airport; anything else becomes a named
     * place in the trip's zone. Coordinates stay empty rather than guessed —
     * the map draws what it knows and says nothing about what it does not.
     */
    private fun resolvePlace(query: String, fallbackName: String, zoneId: String): Place {
        Airports.find(query)?.let { return it.toPlace() }
        val match = Airports.search(query, limit = 1).firstOrNull()
        if (match != null && query.length >= 3) return match.toPlace()
        return Place(
            name = query.ifBlank { fallbackName }.trim().ifBlank { "Unnamed place" },
            city = "",
            timeZoneId = zoneId,
        )
    }
}
