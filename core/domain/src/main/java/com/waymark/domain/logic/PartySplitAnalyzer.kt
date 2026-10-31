package com.waymark.domain.logic

import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological

/**
 * A stretch of the trip during which the party is not together, and who is
 * where. Groups are keyed by the segment that holds them; travelers with
 * nothing booked in the window land in [unaccounted].
 */
data class SplitWindow(
    val startMillis: Long,
    val endMillis: Long,
    val groups: List<Group>,
    val unaccounted: List<String>,
) {
    data class Group(val segmentId: String, val label: String, val travelerIds: List<String>)

    val durationMinutes: Int get() = ((endMillis - startMillis) / 60_000L).toInt()
}

/** Per-traveler coverage of a trip: what they are on, and where the holes are. */
data class TravelerCoverage(
    val traveler: Traveler,
    val segmentCount: Int,
    val flightCount: Int,
    val hasLodging: Boolean,
    val firstMovementMillis: Long?,
    val lastMovementMillis: Long?,
    val missingFromSegments: List<Segment>,
)

/**
 * Group travel is where itineraries quietly disagree: one traveler on the
 * early flight, two on the late one, someone with no hotel. This finds those
 * disagreements before the airport does.
 */
object PartySplitAnalyzer {

    /** Windows where at least two travelers are doing different things. */
    fun splitWindows(dossier: TripDossier): List<SplitWindow> {
        val travelers = dossier.party.travelers.map { it.id }
        if (travelers.size < 2) return emptyList()

        val segments = dossier.segments
            .filter { it !is Segment.Lodging }
            .chronological()
        if (segments.isEmpty()) return emptyList()

        val boundaries = sortedSetOf<Long>()
        segments.forEach {
            boundaries += it.startEpochMillis
            boundaries += it.endEpochMillis
        }
        val marks = boundaries.toList()

        val windows = mutableListOf<SplitWindow>()
        for (index in 0 until marks.size - 1) {
            val from = marks[index]
            val to = marks[index + 1]
            if (to <= from) continue
            val midpoint = from + (to - from) / 2

            val active = segments.filter { midpoint in it.startEpochMillis..it.endEpochMillis }
            if (active.isEmpty()) continue

            val assignment = travelers.associateWith { travelerId ->
                active.firstOrNull { segment ->
                    segment.travelerIds.isEmpty() || travelerId in segment.travelerIds
                }
            }
            val distinct = assignment.values.map { it?.id }.distinct()
            if (distinct.size < 2) continue

            val groups = assignment.entries
                .filter { it.value != null }
                .groupBy { it.value!! }
                .map { (segment, entries) ->
                    SplitWindow.Group(
                        segmentId = segment.id,
                        label = segment.title,
                        travelerIds = entries.map { it.key },
                    )
                }
                .sortedBy { it.label }
            val unaccounted = assignment.filterValues { it == null }.keys.toList()

            windows += SplitWindow(from, to, groups, unaccounted)
        }
        return merge(windows)
    }

    /** Adjacent windows with identical grouping read as one split, not five. */
    private fun merge(windows: List<SplitWindow>): List<SplitWindow> {
        if (windows.isEmpty()) return emptyList()
        val merged = mutableListOf(windows.first())
        windows.drop(1).forEach { window ->
            val last = merged.last()
            val sameShape = last.groups.map { it.segmentId to it.travelerIds.sorted() } ==
                window.groups.map { it.segmentId to it.travelerIds.sorted() } &&
                last.unaccounted.sorted() == window.unaccounted.sorted()
            if (sameShape && window.startMillis <= last.endMillis) {
                merged[merged.size - 1] = last.copy(endMillis = window.endMillis)
            } else {
                merged += window
            }
        }
        return merged
    }

    fun coverage(dossier: TripDossier): List<TravelerCoverage> {
        val segments = dossier.segments.chronological()
        return dossier.party.travelers.map { traveler ->
            val mine = segments.filter {
                it.travelerIds.isEmpty() || traveler.id in it.travelerIds
            }
            val movements = mine.filter { it is Segment.Flight || it is Segment.Ground }
            TravelerCoverage(
                traveler = traveler,
                segmentCount = mine.size,
                flightCount = mine.count { it is Segment.Flight },
                hasLodging = mine.any { it is Segment.Lodging },
                firstMovementMillis = movements.minOfOrNull { it.startEpochMillis },
                lastMovementMillis = movements.maxOfOrNull { it.endEpochMillis },
                missingFromSegments = segments.filter {
                    it.travelerIds.isNotEmpty() && traveler.id !in it.travelerIds
                },
            )
        }
    }

    /**
     * The one thing worth saying out loud about a party.
     *
     * There used to be three. Two of them were not problems: "not on the first
     * flight out" and "not on any lodging booking" are the ordinary shape of
     * group travel — people arrive on different days and someone stays with
     * family — and printing them as notices made a correctly entered trip look
     * broken. A split itinerary is a fact about the trip, and the split panel
     * below states it as one.
     *
     * What is left is the case that is genuinely a hole rather than a plan: a
     * traveler on the trip with nothing booked at all. Somebody has forgotten
     * to enter something, or forgotten to tick a name.
     */
    fun warnings(dossier: TripDossier): List<String> {
        if (dossier.party.travelers.isEmpty()) return emptyList()
        if (dossier.segments.isEmpty()) return emptyList()
        return coverage(dossier)
            .filter { it.segmentCount == 0 }
            .map { "${it.traveler.displayName} is on the trip with nothing booked." }
    }
}
