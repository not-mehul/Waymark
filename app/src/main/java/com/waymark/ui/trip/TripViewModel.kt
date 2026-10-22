package com.waymark.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waymark.data.catalog.Places
import com.waymark.data.local.ReminderStore
import com.waymark.data.repo.AlertRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.TripRepository
import com.waymark.domain.logic.CityGroup
import com.waymark.domain.logic.DayPlan
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.logic.IdeaSection
import com.waymark.domain.logic.IdeaTally
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.Lead
import com.waymark.domain.logic.LegTimes
import com.waymark.domain.logic.LinkNature
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.ReminderCategory
import com.waymark.domain.logic.ReminderPlanner
import com.waymark.domain.logic.ReminderPreferences
import com.waymark.domain.logic.SplitWindow
import com.waymark.domain.logic.TimelineBuilder
import com.waymark.domain.logic.TimelineEntry
import com.waymark.domain.logic.TripAnalytics
import com.waymark.domain.logic.TripAnalyticsReport
import com.waymark.domain.logic.Whereabouts
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.PriceBand
import com.waymark.domain.model.RaisedReminder
import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.TripDossier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Three tabs.
 *
 * The map and the numbers went to the menu — things you open to look at, not
 * views you move between while planning.
 */
enum class TripTab(val label: String) {
    TIMELINE("Timeline"),
    IDEAS("Ideas"),
    PARTY("Party"),
}

/** Where the trip is on a day: what typed places are resolved against. */
data class PlanContext(val near: LatLon?, val zoneId: String)

/** The reminder banner at the top of the trip, counting down rather than frozen. */
data class ReminderBanner(val segmentId: String, val headline: String, val detail: String)

data class TripUiState(
    val dossier: TripDossier? = null,
    val timeline: List<TimelineEntry> = emptyList(),
    /** Saved ideas that fit each stretch of free time, keyed by the link's key. */
    val freeTimeIdeas: Map<String, List<Idea>> = emptyMap(),
    val splits: List<SplitWindow> = emptyList(),
    val partyNotes: List<String> = emptyList(),
    val banner: ReminderBanner? = null,
    val ideas: List<Idea> = emptyList(),
    val ideaSections: List<IdeaSection> = emptyList(),
    val ideaTally: IdeaTally = IdeaTally(0, 0, 0, 0),
    val ideasByCity: List<CityGroup> = emptyList(),
    val dayPlan: DayPlan = DayPlan(emptyList(), emptyList()),
    val showDismissed: Boolean = false,
    val analytics: TripAnalyticsReport? = null,
    val reminders: ReminderPreferences = ReminderPreferences.DEFAULT,
    val travelerFilter: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    /** Every city the trip touches, in visiting order — the ideas board's key. */
    val cities: List<String> = emptyList(),
    /** The trip's own days, for the day-planner chips. */
    val tripDays: List<LocalDate> = emptyList(),
    /** People from other trips who could be brought onto this one. */
    val otherTravelers: List<Traveler> = emptyList(),
    /** The idea just removed, offered back for a few seconds. */
    val undoIdea: Idea? = null,
)

/**
 * One view model behind every screen of a trip: they are views of the same
 * dossier, and splitting them would mean several subscriptions to the same
 * tables and the same timeline computed more than once.
 *
 * Nothing heavy runs on the main thread. What depends only on the trip — the
 * numbers, the splits, the ideas board — is worked out on a background
 * dispatcher when the trip changes. What depends on the clock — the "now"
 * line and the reminder banner — is all the once-a-minute tick rebuilds.
 */
class TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val alerts: AlertRepository,
    private val ideas: IdeaRepository,
    private val reminders: ReminderStore,
) : ViewModel() {

    private val travelerFilter = MutableStateFlow<String?>(null)
    private val showDismissed = MutableStateFlow(false)
    private val undoIdea = MutableStateFlow<Idea?>(null)
    private val tabState = MutableStateFlow(TripTab.TIMELINE)
    private var undoExpiry: Job? = null

    val tab: StateFlow<TripTab> = tabState.asStateFlow()

    private val dossier = trips.observeDossier(tripId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val ideaList = ideas.observe(tripId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Drives the "now" line and the relative times; one tick a minute is plenty. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    /** Everything that depends on the trip but not on the time. */
    private data class Derived(
        val splits: List<SplitWindow>,
        val partyNotes: List<String>,
        val ideaSections: List<IdeaSection>,
        val ideaTally: IdeaTally,
        val ideasByCity: List<CityGroup>,
        val dayPlan: DayPlan,
        val analytics: TripAnalyticsReport?,
        val cities: List<String>,
        val tripDays: List<LocalDate>,
    )

    private val derived = combine(dossier, ideaList, travelerFilter, showDismissed) { trip, all, filter, dismissed ->
        val cities = trip?.segments
            ?.sortedBy { it.startEpochMillis }
            ?.flatMap { listOf(it.destination.city, it.origin.city) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        Derived(
            splits = trip?.let(PartySplitAnalyzer::splitWindows).orEmpty(),
            partyNotes = trip?.let(PartySplitAnalyzer::warnings).orEmpty(),
            ideaSections = IdeaBoard.sections(all, travelerFilter = filter, includeDismissed = dismissed),
            ideaTally = IdeaBoard.tally(all),
            ideasByCity = IdeaBoard.byCity(all, cities, travelerFilter = filter, includeDismissed = dismissed),
            dayPlan = IdeaBoard.byDay(all, travelerFilter = filter, includeDismissed = dismissed),
            analytics = trip?.let { TripAnalytics.report(it, all) },
            cities = cities,
            tripDays = trip?.let { daysOf(it) }.orEmpty(),
        )
    }.flowOn(Dispatchers.Default)

    /** The timeline, and the ideas that fit its free stretches: rebuilt each minute. */
    private data class Timed(
        val timeline: List<TimelineEntry>,
        val freeTimeIdeas: Map<String, List<Idea>>,
        val now: Long,
    )

    private val timed = combine(dossier, ideaList, travelerFilter, clock) { trip, all, filter, now ->
        val timeline = trip?.let {
            TimelineBuilder.build(it, travelerFilter = filter, now = Instant.ofEpochMilli(now))
        }.orEmpty()
        Timed(timeline, freeTimeIdeas(timeline, all, now), now)
    }.flowOn(Dispatchers.Default)

    private data class Surroundings(
        val raised: List<RaisedReminder>,
        val preferences: ReminderPreferences,
        val undo: Idea?,
        val showDismissed: Boolean,
        val everyone: List<Traveler>,
    )

    private val surroundings = combine(
        alerts.observe(),
        reminders.observe(),
        undoIdea,
        showDismissed,
        trips.observeAllTravelers(),
    ) { raised, preferences, undo, dismissed, everyone ->
        Surroundings(raised, preferences, undo, dismissed, everyone)
    }

    val state: StateFlow<TripUiState> = combine(
        dossier,
        ideaList,
        derived,
        timed,
        combine(surroundings, travelerFilter) { around, filter -> around to filter },
    ) { trip, all, d, t, (around, filter) ->
        val party = trip?.party?.travelers.orEmpty().map { it.id }.toSet()
        TripUiState(
            dossier = trip,
            timeline = t.timeline,
            freeTimeIdeas = t.freeTimeIdeas,
            splits = d.splits,
            partyNotes = d.partyNotes,
            banner = trip?.let { banner(it, around.raised, t.now) },
            ideas = all,
            ideaSections = d.ideaSections,
            ideaTally = d.ideaTally,
            ideasByCity = d.ideasByCity,
            dayPlan = d.dayPlan,
            showDismissed = around.showDismissed,
            analytics = d.analytics,
            reminders = around.preferences,
            travelerFilter = filter,
            nowMillis = t.now,
            cities = d.cities,
            tripDays = d.tripDays,
            otherTravelers = around.everyone.filter { it.id !in party },
            undoIdea = around.undo,
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    fun selectTab(next: TripTab) {
        tabState.value = next
    }

    fun filterBy(travelerId: String?) {
        travelerFilter.update { current -> if (current == travelerId) null else travelerId }
    }

    /**
     * Set how long before a category of booking to be reminded. Written
     * straight through: this is a preference about the person, not the trip,
     * and the reminder check reads the same store.
     */
    fun setReminderLead(category: ReminderCategory, lead: Lead) {
        reminders.set(category, lead)
    }

    /** Dismiss the reminder banner for a booking. */
    fun dismissBanner(segmentId: String) {
        viewModelScope.launch { alerts.acknowledge(segmentId) }
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
        val trip = state.value.dossier
        val home = trip?.trip?.homeZoneId ?: ZoneId.systemDefault().id
        val noon = date.atTime(12, 0).atZone(Segment.zoneOrUtc(home)).toInstant().toEpochMilli()
        val segments = trip?.segments.orEmpty()
        return PlanContext(
            near = Whereabouts.positionAt(segments, noon),
            zoneId = Whereabouts.zoneIdAt(segments, noon, home),
        )
    }

    /**
     * Turn a completed [PlanDraft] into a segment. False when the times do not
     * make one — the flow stays open rather than saving nonsense.
     *
     * Each end is the reading the traveler picked, or the best one: a station
     * code, a city, or the text as typed in the zone the trip is in that day.
     * Never the trip's home zone by default — a 15:00 check-in in Paris on a
     * trip created in San Francisco is 15:00 in Paris.
     */
    fun addPlan(draft: PlanDraft): Boolean {
        val segment = planSegment(draft) ?: return false
        viewModelScope.launch { trips.saveSegment(segment) }
        return true
    }

    /** Whether a draft's times describe a booking, for the flow's last step. */
    fun planIsValid(draft: PlanDraft): Boolean = planSegment(draft) != null

    private fun planSegment(draft: PlanDraft): Segment? {
        if (!draft.named) return null
        val context = planContext(draft.startDate)
        val origin = draft.wherePlace
            ?: Places.resolve(draft.where.ifBlank { draft.title }, context.near, context.zoneId).place
        val destination = if (draft.isGround) {
            draft.destinationPlace ?: Places.resolve(
                draft.destination,
                near = origin.position ?: context.near,
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
            end = LocalDateTime.of(if (draft.isStay) draft.endDate else draft.startDate, draft.endTime),
            endZone = endZone,
        )
        if (!LegTimes.isOrdered(start, end)) return null

        val id = TripRepository.newId("seg")
        val reference = draft.confirmationCode.trim().ifBlank { null }
        val vendor = draft.vendor.trim().ifBlank { null }
        val note = draft.note.trim().ifBlank { null }
        return when (draft.kind) {
            SegmentKind.LODGING -> Segment.Lodging(
                id = id,
                tripId = tripId,
                propertyName = draft.title.trim(),
                origin = origin,
                startEpochMillis = start.toInstant().toEpochMilli(),
                endEpochMillis = end.toInstant().toEpochMilli(),
                startZoneId = startZone.id,
                endZoneId = startZone.id,
                travelerIds = draft.travelerIds,
                confirmationCode = reference,
                bookedWith = vendor,
                note = note,
            )

            SegmentKind.GROUND -> Segment.Ground(
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
                confirmationCode = reference,
                bookedWith = vendor,
                note = note,
                provider = draft.title.trim().ifBlank { vendor },
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
                confirmationCode = reference,
                bookedWith = vendor,
                note = note,
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
                    endEpochMillis = last.atTime(LocalTime.of(23, 59))
                        .atZone(zone).toInstant().toEpochMilli(),
                )
            )
        }
    }

    /** Pull the trip's dates in to the span of its bookings. */
    fun fitDatesToBookings() {
        viewModelScope.launch { trips.fitTripToBookings(tripId) }
    }

    /** Put the trip away, or bring it back. The caller navigates away when archiving. */
    fun setArchived(archived: Boolean) {
        viewModelScope.launch { trips.setArchived(tripId, archived) }
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
                traveler = Traveler(
                    id = TripRepository.newId("trav"),
                    fullName = fullName.trim(),
                    nickname = nickname?.trim()?.ifBlank { null },
                ),
            )
        }
    }

    /** Someone from another trip, brought onto this one with their profile. */
    fun addExistingTraveler(travelerId: String) {
        viewModelScope.launch { trips.addExistingTraveler(tripId, travelerId) }
    }

    fun updateTraveler(traveler: Traveler) {
        viewModelScope.launch { trips.saveTraveler(traveler) }
    }

    fun removeTraveler(travelerId: String) {
        viewModelScope.launch { trips.removeTraveler(tripId, travelerId) }
    }

    // — Ideas ————————————————————————————————————————————————————————————

    fun addIdea(draft: IdeaDraft) {
        if (draft.title.isBlank()) return
        val city = draft.city.ifBlank { state.value.cities.firstOrNull().orEmpty() }
        val context = planContext(state.value.dossier?.trip?.startDate() ?: LocalDate.now())
        viewModelScope.launch {
            ideas.add(
                tripId = tripId,
                title = draft.title,
                kind = draft.kind,
                city = city,
                note = draft.note,
                place = draft.address.trim().ifBlank { null }?.let { address ->
                    // The address is kept as typed, on its city's clock. The
                    // city's centre is not the address, so nothing is pinned.
                    Places.resolve(city, context.near, context.zoneId).place.copy(
                        name = draft.title.trim(),
                        address = address,
                        latitude = null,
                        longitude = null,
                    )
                },
                priceBand = draft.priceBand,
                bestTime = draft.bestTime,
                typicalMinutes = draft.minutes,
            )
        }
    }

    fun setIdeaStatus(ideaId: String, status: IdeaStatus) {
        viewModelScope.launch { ideas.setStatus(ideaId, status) }
    }

    fun toggleInterest(ideaId: String, travelerId: String) {
        viewModelScope.launch { ideas.setInterest(ideaId, travelerId) }
    }

    fun showDismissed(show: Boolean) {
        showDismissed.value = show
    }

    /** Removed at once, and offered back for a few seconds. */
    fun deleteIdea(ideaId: String) {
        viewModelScope.launch {
            val removed = ideas.delete(ideaId) ?: return@launch
            undoIdea.value = removed
            undoExpiry?.cancel()
            undoExpiry = launch {
                delay(UNDO_MILLIS)
                undoIdea.value = null
            }
        }
    }

    fun undoDelete() {
        val idea = undoIdea.value ?: return
        undoIdea.value = null
        undoExpiry?.cancel()
        viewModelScope.launch { ideas.save(idea) }
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
        startTime: LocalTime,
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

    private companion object {
        const val UNDO_MILLIS = 6_000L

        /** The trip's own days; capped at a year so a mistyped date cannot build a decade. */
        fun daysOf(dossier: TripDossier): List<LocalDate> {
            val days = mutableListOf<LocalDate>()
            var day = dossier.trip.startDate()
            while (!day.isAfter(dossier.trip.endDate()) && days.size < TripAnalytics.MAX_DAYS) {
                days += day
                day = day.plusDays(1)
            }
            return days
        }

        /**
         * The newest reminder not dismissed for a booking that has not yet
         * started, its headline worked out again from the clock: "departs in
         * 40m" an hour later, rather than the "in 2h 55m" it was raised with.
         */
        fun banner(dossier: TripDossier, raised: List<RaisedReminder>, now: Long): ReminderBanner? {
            val byId = dossier.segments.associateBy { it.id }
            return raised
                .asSequence()
                .filter { !it.acknowledged }
                .mapNotNull { reminder -> byId[reminder.segmentId]?.let { reminder to it } }
                .filter { (_, segment) -> segment.startEpochMillis > now }
                .maxByOrNull { (reminder, _) -> reminder.raisedAtMillis }
                ?.let { (reminder, segment) ->
                    ReminderBanner(
                        segmentId = segment.id,
                        headline = ReminderPlanner.headline(
                            segment,
                            ReminderPlanner.momentOf(reminder.signature),
                            now,
                            dossier.segments,
                        ),
                        detail = reminder.detail,
                    )
                }
        }

        /**
         * For each stretch of free time still ahead, the saved ideas in that
         * place that fit inside it — the timeline already names the gap; this
         * says what could go in it.
         */
        fun freeTimeIdeas(timeline: List<TimelineEntry>, ideas: List<Idea>, now: Long): Map<String, List<Idea>> =
            timeline.asSequence()
                .filterIsInstance<TimelineEntry.Link>()
                .filter { it.nature == LinkNature.FREE_TIME && it.toSegment.startEpochMillis > now }
                .mapNotNull { link ->
                    val where = link.fromSegment.destination
                    val local = ideas.filter { idea ->
                        idea.city.isBlank() || where.city.isBlank() ||
                            idea.city.equals(where.city, ignoreCase = true)
                    }
                    IdeaBoard.fitsIn(local, from = where, availableMinutes = link.gapMinutes, limit = 3)
                        .takeIf { it.isNotEmpty() }
                        ?.let { link.key to it }
                }
                .toMap()
    }
}

/** An idea being added, before it has an id. */
data class IdeaDraft(
    val title: String,
    val kind: IdeaKind,
    val city: String,
    val note: String? = null,
    val address: String = "",
    val priceBand: PriceBand? = null,
    val bestTime: String? = null,
    val minutes: Int? = null,
)
