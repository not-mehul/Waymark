package com.waymark.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Places
import com.waymark.data.local.ReminderStore
import com.waymark.data.repo.AlertRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.TripRepository
import com.waymark.domain.logic.DayPlan
import com.waymark.domain.logic.CityGroup
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.logic.IdeaSection
import com.waymark.domain.logic.IdeaTally
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.Lead
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.ReminderCategory
import com.waymark.domain.logic.ReminderPreferences
import com.waymark.domain.logic.SplitWindow
import com.waymark.domain.logic.TimelineBuilder
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.logic.TripAnalytics
import com.waymark.domain.logic.TripAnalyticsReport
import com.waymark.domain.logic.Whereabouts
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Three tabs.
 *
 * The map and the numbers went to the menu — things you open to look at, not
 * views you move between while planning. The vault went entirely: with nothing
 * encrypted any more, a booking's reference belongs on the booking, and a
 * passport belongs to the traveler who carries it.
 */
enum class TripTab(val label: String) {
    TIMELINE("Timeline"),
    IDEAS("Ideas"),
    PARTY("Party"),
}

/** Where the trip is on a day: what typed places are resolved against. */
data class PlanContext(val near: LatLon?, val zoneId: String)

data class TripUiState(
    val dossier: TripDossier? = null,
    val timeline: List<TimelineEntry> = emptyList(),
    val splits: List<SplitWindow> = emptyList(),
    val partyNotes: List<String> = emptyList(),
    val alerts: List<DisruptionAlert> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val ideaSections: List<IdeaSection> = emptyList(),
    val ideaTally: IdeaTally = IdeaTally(0, 0, 0, 0),
    val ideasByCity: List<CityGroup> = emptyList(),
    val dayPlan: DayPlan = DayPlan(emptyList(), emptyList()),
    val analytics: TripAnalyticsReport? = null,
    val reminders: ReminderPreferences = ReminderPreferences.DEFAULT,
    val travelerFilter: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val flights: List<Segment.Flight>
        get() = dossier?.segments?.filterIsInstance<Segment.Flight>().orEmpty()

    /** Every city this trip touches, in visiting order — the guide's key. */
    val cities: List<String>
        get() = dossier?.segments
            ?.sortedBy { it.startEpochMillis }
            ?.flatMap { listOf(it.destination.city, it.origin.city) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
}

/**
 * One view model behind all three tabs: they are views of the same dossier, and
 * splitting them would mean three subscriptions to the same tables.
 */
class TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val alerts: AlertRepository,
    private val ideas: IdeaRepository,
    private val reminders: ReminderStore,
) : ViewModel() {

    private val travelerFilter = MutableStateFlow<String?>(null)
    private val tabState = MutableStateFlow(TripTab.TIMELINE)

    val tab: StateFlow<TripTab> = tabState.asStateFlow()

    /** Drives the "now" line and the relative times; one tick a minute is plenty. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(60_000)
        }
    }

    /** Filter, clock, ideas and reminder settings, folded into one flow. */
    private data class Ambient(
        val filter: String?,
        val now: Long,
        val ideas: List<Idea>,
        val reminders: ReminderPreferences,
    )

    private val ambient = combine(
        travelerFilter,
        clock,
        ideas.observe(tripId),
        reminders.observe(),
    ) { filter, now, ideaList, reminderPreferences ->
        Ambient(filter, now, ideaList, reminderPreferences)
    }

    val state: StateFlow<TripUiState> = combine(
        trips.observeDossier(tripId),
        alerts.observe(),
        ambient,
    ) { dossier, raised, current ->
        val instant = Instant.ofEpochMilli(current.now)
        TripUiState(
            dossier = dossier,
            timeline = dossier?.let {
                TimelineBuilder.build(it, travelerFilter = current.filter, now = instant)
            }.orEmpty(),
            splits = dossier?.let(PartySplitAnalyzer::splitWindows).orEmpty(),
            partyNotes = dossier?.let(PartySplitAnalyzer::warnings).orEmpty(),
            alerts = raised.filter { alert ->
                dossier?.segments?.any { it.id == alert.segmentId } == true
            },
            ideas = current.ideas,
            ideaSections = IdeaBoard.sections(
                ideas = current.ideas,
                travelerFilter = current.filter,
            ),
            ideaTally = IdeaBoard.tally(current.ideas),
            ideasByCity = IdeaBoard.byCity(
                current.ideas,
                cityOrder = dossier?.segments
                    ?.sortedBy { it.startEpochMillis }
                    ?.flatMap { listOf(it.destination.city, it.origin.city) }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty(),
            ),
            dayPlan = IdeaBoard.byDay(current.ideas),
            analytics = dossier?.let { TripAnalytics.report(it, current.ideas) },
            reminders = current.reminders,
            travelerFilter = current.filter,
            nowMillis = current.now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    fun selectTab(next: TripTab) {
        tabState.value = next
    }

    fun filterBy(travelerId: String?) {
        travelerFilter.update { current -> if (current == travelerId) null else travelerId }
    }

    /**
     * Set how long before a category of booking to be reminded.
     *
     * Written straight through rather than held in the view model: this is a
     * preference about the person, not about the trip on screen, and the
     * background worker reads the same store.
     */
    fun setReminderLead(category: ReminderCategory, lead: Lead) {
        reminders.set(category, lead)
    }

    fun setTravelers(segmentId: String, travelerIds: Set<String>) {
        viewModelScope.launch { trips.setTravelers(segmentId, travelerIds) }
    }

    fun deleteSegment(segmentId: String) {
        viewModelScope.launch { trips.deleteSegment(segmentId) }
    }

    /**
     * Where the trip is on a given day, for resolving a place typed into a plan
     * or an idea on that day: the position to rank same-named cities against,
     * and the clock to fall back on when the text matches nothing.
     */
    fun planContext(date: LocalDate): PlanContext {
        val dossier = state.value.dossier
        val home = dossier?.trip?.homeZoneId ?: ZoneId.systemDefault().id
        val noon = date.atTime(12, 0).atZone(Segment.zoneOrUtc(home)).toInstant().toEpochMilli()
        val segments = dossier?.segments.orEmpty()
        return PlanContext(
            near = Whereabouts.positionAt(segments, noon),
            zoneId = Whereabouts.zoneIdAt(segments, noon, home),
        )
    }

    /**
     * Turn a completed [PlanDraft] into a segment.
     *
     * Each end is the reading the traveler picked, or the best one: a station
     * code, a city, or the text as typed in the zone the trip is in that day.
     * Never the trip's home zone by default — a 15:00 check-in in Paris on a
     * trip created in San Francisco is 15:00 in Paris.
     */
    fun addPlan(draft: PlanDraft): Boolean {
        val segment = segmentOf(draft) ?: return false
        viewModelScope.launch { trips.saveSegment(segment) }
        return true
    }

    private fun segmentOf(draft: PlanDraft): Segment? {
        if (!draft.named) return null
        val context = planContext(draft.startDate)
        val origin = draft.wherePlace
            ?: Places.resolve(draft.where.ifBlank { draft.title }, context.near, context.zoneId).place
        val destination = if (draft.isGround) {
            draft.destinationPlace ?: Places.resolve(
                draft.destination,
                near = origin.takeIf { it.hasCoordinates }?.let { LatLon(it.latitude, it.longitude) }
                    ?: context.near,
                fallbackZoneId = context.zoneId,
            ).place
        } else {
            origin
        }

        val startZone = Segment.zoneOrUtc(origin.timeZoneId)
        val endZone = Segment.zoneOrUtc(destination.timeZoneId)
        val start = ZonedDateTime.of(draft.startDate, draft.startTime, startZone)
        val end = LegTimes.endAfter(
            start = start,
            end = java.time.LocalDateTime.of(
                if (draft.isStay) draft.endDate else draft.startDate,
                draft.endTime,
            ),
            endZone = endZone,
        )
        if (!LegTimes.isOrdered(start, end)) return null

        val id = TripRepository.newId("seg")
        return when (draft.kind) {
            com.waymark.domain.model.SegmentKind.LODGING -> Segment.Lodging(
                id = id,
                tripId = tripId,
                propertyName = draft.title.trim(),
                origin = origin,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = startZone.id,
                endZoneId = startZone.id,
                travelerIds = draft.travelerIds,
                confirmationCode = draft.confirmationCode.trim().ifBlank { null },
                bookedWith = draft.vendor.trim().ifBlank { null },
                note = draft.note.ifBlank { null },
            )

            com.waymark.domain.model.SegmentKind.GROUND -> Segment.Ground(
                id = id,
                tripId = tripId,
                mode = draft.mode,
                origin = origin,
                destination = destination,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = startZone.id,
                endZoneId = endZone.id,
                travelerIds = draft.travelerIds,
                confirmationCode = draft.confirmationCode.trim().ifBlank { null },
                bookedWith = draft.vendor.trim().ifBlank { null },
                note = draft.note.ifBlank { null },
                provider = draft.vendor.ifBlank { null },
            )

            else -> Segment.Experience(
                id = id,
                tripId = tripId,
                name = draft.title.trim(),
                category = "Booking",
                origin = origin,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = startZone.id,
                endZoneId = startZone.id,
                travelerIds = draft.travelerIds,
                confirmationCode = draft.confirmationCode.trim().ifBlank { null },
                bookedWith = draft.vendor.trim().ifBlank { null },
                note = draft.note.ifBlank { null },
            )
        }
    }

    /**
     * Rename a trip, restate where it goes, or move its dates.
     *
     * The window is stored as two instants in the trip's own zone: the first
     * day from midnight, the last day to 23:59, so a trip that ends on the 14th
     * still contains a flight at 22:00 on the 14th.
     */
    fun updateTrip(
        name: String,
        destinationSummary: String,
        start: LocalDate,
        end: LocalDate,
    ) {
        val trip = state.value.dossier?.trip ?: return
        val zone = Segment.zoneOrUtc(trip.homeZoneId)
        val first = if (end.isBefore(start)) end else start
        val last = if (end.isBefore(start)) start else end
        viewModelScope.launch {
            trips.saveTrip(
                trip.copy(
                    name = name.trim().ifBlank {
                        destinationSummary.trim().ifBlank { trip.name }
                    },
                    destinationSummary = destinationSummary.trim(),
                    startEpochMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
                    endEpochMillis = last.atTime(java.time.LocalTime.of(23, 59))
                        .atZone(zone).toInstant().toEpochMilli(),
                )
            )
        }
    }

    /** Remove the trip and everything on it. The caller navigates away. */
    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            trips.deleteTrip(tripId)
            onDeleted()
        }
    }

    /**
     * Write an edited booking back. Called once, on Save — never per keystroke
     * and never per tap, so a booking cannot drift while it is being read.
     */
    fun updateSegment(segment: Segment) {
        viewModelScope.launch { trips.saveSegment(segment) }
    }

    fun addTraveler(fullName: String, nickname: String?) {
        if (fullName.isBlank()) return
        viewModelScope.launch {
            trips.addTraveler(
                tripId = tripId,
                traveler = com.waymark.domain.model.Traveler(
                    id = TripRepository.newId("trav"),
                    fullName = fullName.trim(),
                    nickname = nickname?.trim()?.ifBlank { null },
                ),
            )
        }
    }

    fun removeTraveler(travelerId: String) {
        viewModelScope.launch { trips.removeTraveler(tripId, travelerId) }
    }

    // — Ideas ————————————————————————————————————————————————————————————

    fun addIdea(title: String, kind: IdeaKind, city: String, note: String?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            ideas.add(
                tripId = tripId,
                title = title,
                kind = kind,
                city = city.ifBlank { state.value.cities.firstOrNull().orEmpty() },
                note = note,
            )
        }
    }

    fun setIdeaStatus(ideaId: String, status: IdeaStatus) {
        viewModelScope.launch { ideas.setStatus(ideaId, status) }
    }

    fun toggleInterest(ideaId: String, travelerId: String) {
        viewModelScope.launch { ideas.setInterest(ideaId, travelerId) }
    }

    fun deleteIdea(ideaId: String) {
        viewModelScope.launch { ideas.delete(ideaId) }
    }

    /**
     * Put an idea on the timeline, on the clock of the place it happens.
     *
     * In order: the idea's own place; a booking in the same city, whose zone is
     * already known exactly; the city looked up by name; and failing all of
     * those, wherever the trip is on that day.
     */
    fun scheduleIdea(
        idea: Idea,
        date: LocalDate,
        startTime: java.time.LocalTime,
        minutesOverride: Int? = null,
    ) {
        val context = planContext(date)
        val bookedHere = state.value.dossier?.segments.orEmpty()
            .flatMap { listOf(it.origin, it.destination) }
            .firstOrNull { idea.city.isNotBlank() && it.city.equals(idea.city, ignoreCase = true) }
        val zoneId = idea.place?.timeZoneId
            ?: bookedHere?.timeZoneId
            ?: Places.resolve(idea.city, context.near, context.zoneId)
                .takeIf { idea.city.isNotBlank() && it.match != Places.Match.TYPED }
                ?.place?.timeZoneId
            ?: context.zoneId
        viewModelScope.launch {
            ideas.schedule(
                idea = idea,
                date = date,
                startTime = startTime,
                zone = Segment.zoneOrUtc(zoneId),
                travelerIds = idea.interestedTravelerIds,
                minutesOverride = minutesOverride,
            )
        }
    }

    fun pencilIdeaFor(ideaId: String, date: LocalDate?) {
        viewModelScope.launch { ideas.setPlannedDay(ideaId, date) }
    }

    /** The trip's own days, for the day-planner chips. */
    fun tripDays(): List<LocalDate> {
        val trip = state.value.dossier?.trip ?: return emptyList()
        val days = mutableListOf<LocalDate>()
        var day = trip.startDate()
        while (!day.isAfter(trip.endDate()) && days.size < 60) {
            days += day
            day = day.plusDays(1)
        }
        return days
    }
}
