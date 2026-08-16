package com.waymark.domain.logic

import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The board grouped by place. */
data class CityGroup(val city: String, val ideas: List<Idea>) {
    val outstanding: Int get() = ideas.count { it.status == IdeaStatus.SAVED }
}

/** The board grouped by the day it is pencilled in for. */
data class DayGroup(val date: LocalDate, val ideas: List<Idea>) {
    val estimatedMinutes: Int get() = ideas.sumOf { it.typicalMinutes ?: 60 }
}

data class DayPlan(val days: List<DayGroup>, val undated: List<Idea>) {
    val isEmpty: Boolean get() = days.isEmpty() && undated.isEmpty()
}

/** One section of the board: a kind of idea, and the ideas in it. */
data class IdeaSection(
    val kind: IdeaKind,
    val ideas: List<Idea>,
) {
    val outstanding: Int get() = ideas.count { it.status == IdeaStatus.SAVED }
}

data class IdeaTally(
    val saved: Int,
    val scheduled: Int,
    val done: Int,
    val dismissed: Int,
) {
    val total: Int get() = saved + scheduled + done
    val eaten: Int get() = done
}

/**
 * The list of things a trip might do, and the one operation that matters:
 * promoting an idea onto the timeline, where it stops being an idea.
 */
object IdeaBoard {

    /** Sections in a fixed order, so the board does not reshuffle as it fills. */
    private val ORDER = listOf(IdeaKind.SEE, IdeaKind.EAT, IdeaKind.DO, IdeaKind.SHOP)

    fun sections(
        ideas: List<Idea>,
        city: String? = null,
        travelerFilter: String? = null,
        includeDismissed: Boolean = false,
    ): List<IdeaSection> {
        val visible = ideas
            .filter { includeDismissed || it.status != IdeaStatus.DISMISSED }
            .filter { city == null || it.city.equals(city, ignoreCase = true) }
            .filter { idea ->
                travelerFilter == null ||
                    idea.interestedTravelerIds.isEmpty() ||
                    travelerFilter in idea.interestedTravelerIds
            }
        return ORDER.mapNotNull { kind ->
            val group = visible.filter { it.kind == kind }.sortedWith(displayOrder)
            if (group.isEmpty()) null else IdeaSection(kind, group)
        }
    }

    /** Open items first, then scheduled, then done; alphabetical within each. */
    private val displayOrder = compareBy<Idea>(
        {
            when (it.status) {
                IdeaStatus.SAVED -> 0
                IdeaStatus.SCHEDULED -> 1
                IdeaStatus.DONE -> 2
                IdeaStatus.DISMISSED -> 3
            }
        },
        { it.title.lowercase() },
    )

    fun tally(ideas: List<Idea>): IdeaTally = IdeaTally(
        saved = ideas.count { it.status == IdeaStatus.SAVED },
        scheduled = ideas.count { it.status == IdeaStatus.SCHEDULED },
        done = ideas.count { it.status == IdeaStatus.DONE },
        dismissed = ideas.count { it.status == IdeaStatus.DISMISSED },
    )

    /**
     * Guide entries the trip has not already taken up. Matching is on title
     * and city, so an idea saved, scheduled, done or explicitly declined is
     * never suggested again.
     */
    fun unseenSuggestions(
        suggestions: List<Idea>,
        existing: List<Idea>,
    ): List<Idea> {
        val taken = existing.map { key(it.title, it.city) }.toSet()
        return suggestions.filterNot { key(it.title, it.city) in taken }
    }

    private fun key(title: String, city: String): String =
        "${title.trim().lowercase()}@${city.trim().lowercase()}"

    /**
     * Promote an idea onto the timeline.
     *
     * Duration comes from the idea's own estimate where it has one, because a
     * museum is not a coffee; the fallback is an hour. The segment carries the
     * idea's note forward so the reason it was saved survives the transition.
     */
    fun schedule(
        idea: Idea,
        segmentId: String,
        date: LocalDate,
        startTime: LocalTime,
        zone: ZoneId,
        travelerIds: Set<String>,
        minutesOverride: Int? = null,
    ): Segment.Experience {
        val minutes = (minutesOverride ?: idea.typicalMinutes ?: DEFAULT_MINUTES).coerceAtLeast(15)
        val start = ZonedDateTime.of(date, startTime, zone)
        val end = start.plusMinutes(minutes.toLong())
        return Segment.Experience(
            id = segmentId,
            tripId = idea.tripId,
            name = idea.title,
            category = idea.kind.label,
            origin = idea.place ?: Place(
                name = idea.title,
                city = idea.city,
                timeZoneId = zone.id,
            ),
            startEpochMillis = start.toInstant().toEpochMilli(),
            endEpochMillis = end.toInstant().toEpochMilli(),
            startZoneId = zone.id,
            endZoneId = zone.id,
            travelerIds = travelerIds.ifEmpty { idea.interestedTravelerIds },
            note = idea.note,
            curatedBy = idea.bestTime ?: idea.source,
        )
    }

    /**
     * The list grouped by place, in visiting order where the trip supplies one.
     * A trip that touches three cities should not present one undifferentiated
     * list; what to eat in Paris is not useful while standing in London.
     */
    fun byCity(
        ideas: List<Idea>,
        cityOrder: List<String> = emptyList(),
        includeDismissed: Boolean = false,
    ): List<CityGroup> {
        val visible = ideas.filter { includeDismissed || it.status != IdeaStatus.DISMISSED }
        val order = cityOrder.map { it.lowercase() }
        return visible
            .groupBy { it.city.ifBlank { "Anywhere" } }
            .map { (city, group) -> CityGroup(city, group.sortedWith(displayOrder)) }
            .sortedWith(
                compareBy(
                    { group ->
                        order.indexOf(group.city.lowercase())
                            .let { if (it < 0) Int.MAX_VALUE else it }
                    },
                    { it.city.lowercase() },
                )
            )
    }

    /**
     * The list grouped by the day it is pencilled in for, with everything that
     * has no day yet gathered at the end. Days the trip does not contain are
     * kept rather than hidden: a date that has drifted out of range is a fact
     * the traveler needs to see.
     */
    fun byDay(
        ideas: List<Idea>,
        includeDismissed: Boolean = false,
    ): DayPlan {
        val visible = ideas.filter { includeDismissed || it.status != IdeaStatus.DISMISSED }
        val dated = visible.filter { it.plannedDate != null }
        val days = dated
            .groupBy { it.plannedDate!! }
            .map { (date, group) -> DayGroup(date, group.sortedWith(displayOrder)) }
            .sortedBy { it.date }
        return DayPlan(days = days, undated = visible.filter { it.plannedDate == null }
            .sortedWith(displayOrder))
    }

    /**
     * How full a pencilled day already is, by the ideas' own estimates. Used to
     * warn before a fourth museum joins a day that already holds three.
     */
    fun plannedMinutes(ideas: List<Idea>, date: LocalDate): Int = ideas
        .filter { it.plannedDate == date && it.status != IdeaStatus.DISMISSED }
        .sumOf { it.typicalMinutes ?: DEFAULT_MINUTES }

    /**
     * Ideas worth doing from where the traveler currently is: everything saved,
     * with a location, sorted by distance. This is the "we have three hours,
     * what is near" question.
     */
    fun nearby(
        ideas: List<Idea>,
        from: Place,
        withinKm: Double = 5.0,
        limit: Int = 6,
    ): List<Pair<Idea, Double>> {
        if (!from.hasCoordinates) return emptyList()
        return ideas
            .asSequence()
            .filter { it.status == IdeaStatus.SAVED && it.hasLocation }
            .map { it to Geo.distanceKm(from, it.place!!) }
            .filter { (_, distance) -> distance <= withinKm }
            .sortedBy { (_, distance) -> distance }
            .take(limit)
            .toList()
    }

    /**
     * Ideas that fit a gap in the day, by their own duration estimate plus the
     * walk there. Used to fill the free-time blocks the timeline already names.
     */
    fun fitsIn(
        ideas: List<Idea>,
        from: Place,
        availableMinutes: Int,
        limit: Int = 4,
    ): List<Idea> = ideas
        .asSequence()
        .filter { it.status == IdeaStatus.SAVED }
        .filter { idea ->
            val visit = idea.typicalMinutes ?: DEFAULT_MINUTES
            val travel = idea.place
                ?.takeIf { from.hasCoordinates && it.hasCoordinates }
                ?.let { TransitEstimator.estimateBest(from, it).totalMinutes * 2 }
                ?: 0
            visit + travel <= availableMinutes
        }
        .sortedByDescending { it.typicalMinutes ?: DEFAULT_MINUTES }
        .take(limit)
        .toList()

    private const val DEFAULT_MINUTES = 60
}
