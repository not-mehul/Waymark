package com.waymark.domain.logic

import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.chronological
import com.waymark.domain.model.involves
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

enum class EventRole { WHOLE, CHECK_IN, CHECK_OUT }

enum class EventState { PAST, ACTIVE, FUTURE }

enum class LinkNature {
    /** Plane to plane at the same airport. */
    CONNECTION,

    /** Any other move between two places. */
    TRANSFER,

    /** A gap with nothing booked and nowhere to be. */
    FREE_TIME,
}

sealed interface TimelineEntry {
    val sortKey: Long

    data class DayBreak(
        val date: LocalDate,
        val headline: String,
        val zoneLabel: String,
        override val sortKey: Long,
    ) : TimelineEntry

    data class Event(
        val segment: Segment,
        val role: EventRole,
        val status: FlightStatus?,
        val state: EventState,
        val localTime: ZonedDateTime,
        val arrivalDayOffset: String?,
        override val sortKey: Long,
    ) : TimelineEntry {
        val id: String get() = "${segment.id}:${role.name}"
    }

    data class Link(
        val fromSegment: Segment,
        val toSegment: Segment,
        val nature: LinkNature,
        val verdict: ConnectionVerdict?,
        val estimate: TransitEstimate?,
        val gapMinutes: Int,
        override val sortKey: Long,
    ) : TimelineEntry {
        val id: String get() = "link:${fromSegment.id}:${toSegment.id}"
    }

    data class Now(val label: String, override val sortKey: Long) : TimelineEntry
}

/**
 * Turns a pile of reservations into the one thing a traveler actually reads:
 * a single column of days, in order, with the awkward bits between them named.
 */
object TimelineBuilder {

    /** Gaps longer than this are the traveler's own time, not a transfer. */
    private const val FREE_TIME_THRESHOLD_MINUTES = 240

    fun build(
        dossier: TripDossier,
        travelerFilter: String? = null,
        now: Instant = Instant.now(),
        checkedBags: Boolean = true,
    ): List<TimelineEntry> {
        val segments = dossier.segments
            .filter { travelerFilter == null || it.involves(travelerFilter) }
            .chronological()
        if (segments.isEmpty()) return emptyList()

        val nowMillis = now.toEpochMilli()
        val entries = mutableListOf<TimelineEntry>()

        val instances = buildList {
            segments.forEach { segment ->
                add(Triple(segment.startEpochMillis, entryRoleFor(segment), segment))
                if (segment is Segment.Lodging &&
                    segment.start.toLocalDate() != segment.end.toLocalDate()
                ) {
                    add(Triple(segment.endEpochMillis, EventRole.CHECK_OUT, segment))
                }
            }
        }.sortedWith(compareBy({ it.first }, { it.third.id }, { it.second.ordinal }))

        val linksBefore = links(segments, dossier, checkedBags).associateBy { it.toSegment.id }

        var lastDate: LocalDate? = null
        var nowEmitted = false

        instances.forEach { (millis, role, segment) ->
            val localTime = if (role == EventRole.CHECK_OUT) segment.end else segment.start
            val date = localTime.toLocalDate()

            if (!nowEmitted && nowMillis < millis) {
                entries += TimelineEntry.Now(
                    label = TimeText.relative(millis, nowMillis).replaceFirstChar { it.uppercase() },
                    sortKey = nowMillis,
                )
                nowEmitted = true
            }

            if (date != lastDate) {
                entries += TimelineEntry.DayBreak(
                    date = date,
                    headline = TimeText.dayHeadline(date, now.atZone(localTime.zone).toLocalDate()),
                    zoneLabel = TimeText.zoneLabel(localTime),
                    // Ordered ahead of the link that may precede the first
                    // event of the day: heading, then gap, then event.
                    sortKey = millis - 3,
                )
                lastDate = date
            }

            if (role != EventRole.CHECK_OUT) linksBefore[segment.id]?.let { entries += it }

            entries += TimelineEntry.Event(
                segment = segment,
                role = role,
                status = dossier.statusFor(segment),
                state = stateOf(segment, nowMillis),
                localTime = localTime,
                arrivalDayOffset = if (role == EventRole.CHECK_OUT) {
                    null
                } else {
                    TimeText.dayOffsetSuffix(segment.start, segment.end)
                },
                sortKey = millis,
            )
        }

        if (!nowEmitted) {
            entries += TimelineEntry.Now(label = "Trip complete", sortKey = nowMillis)
        }
        return entries
    }

    private fun entryRoleFor(segment: Segment): EventRole =
        if (segment is Segment.Lodging) EventRole.CHECK_IN else EventRole.WHOLE

    private fun stateOf(segment: Segment, nowMillis: Long): EventState = when {
        nowMillis < segment.startEpochMillis -> EventState.FUTURE
        nowMillis > segment.endEpochMillis -> EventState.PAST
        else -> EventState.ACTIVE
    }

    /** The bits between the bookings — the part that goes wrong. */
    fun links(
        segments: List<Segment>,
        dossier: TripDossier,
        checkedBags: Boolean = true,
    ): List<TimelineEntry.Link> {
        val moving = segments.chronological()
        val result = mutableListOf<TimelineEntry.Link>()
        for (index in 1 until moving.size) {
            val previous = moving[index - 1]
            val next = moving[index]
            val gapMinutes = ((next.startEpochMillis - previous.endEpochMillis) / 60_000L).toInt()

            // Stationary bookings (a hotel) overlap everything else by design.
            if (previous is Segment.Lodging || next is Segment.Lodging) continue
            if (samePlace(previous.destination, next.origin) &&
                gapMinutes in 0..FREE_TIME_THRESHOLD_MINUTES &&
                !(previous is Segment.Flight && next is Segment.Flight)
            ) continue

            val link = when {
                previous is Segment.Flight && next is Segment.Flight -> {
                    val verdict = ConnectionRisk.assess(
                        inbound = previous,
                        onward = next,
                        inboundStatus = dossier.statusFor(previous),
                        onwardStatus = dossier.statusFor(next),
                        checkedBags = checkedBags,
                    )
                    TimelineEntry.Link(
                        fromSegment = previous,
                        toSegment = next,
                        nature = if (samePlace(previous.destination, next.origin)) {
                            LinkNature.CONNECTION
                        } else {
                            LinkNature.TRANSFER
                        },
                        verdict = verdict,
                        estimate = if (samePlace(previous.destination, next.origin)) {
                            null
                        } else {
                            TransitEstimator.estimateBest(previous.destination, next.origin)
                        },
                        gapMinutes = gapMinutes,
                        sortKey = next.startEpochMillis - 2,
                    )
                }

                gapMinutes > FREE_TIME_THRESHOLD_MINUTES &&
                    samePlace(previous.destination, next.origin) -> {
                    TimelineEntry.Link(
                        fromSegment = previous,
                        toSegment = next,
                        nature = LinkNature.FREE_TIME,
                        verdict = null,
                        estimate = null,
                        gapMinutes = gapMinutes,
                        sortKey = next.startEpochMillis - 2,
                    )
                }

                else -> {
                    val estimate = TransitEstimator.estimateBest(previous.destination, next.origin)
                    TimelineEntry.Link(
                        fromSegment = previous,
                        toSegment = next,
                        nature = LinkNature.TRANSFER,
                        verdict = ConnectionRisk.assessGap(
                            fromPlace = previous.destination,
                            toPlace = next.origin,
                            availableMinutes = gapMinutes,
                        ),
                        estimate = estimate,
                        gapMinutes = gapMinutes,
                        sortKey = next.startEpochMillis - 2,
                    )
                }
            }
            result += link
        }
        return result
    }

    private fun samePlace(a: Place, b: Place): Boolean = when {
        a.code != null && b.code != null -> a.code == b.code
        a.hasCoordinates && b.hasCoordinates -> Geo.distanceKm(a, b) < 0.4
        else -> a.name.equals(b.name, ignoreCase = true)
    }

    /** The next thing that needs doing, for the trip card and the alert copy. */
    fun nextEvent(dossier: TripDossier, now: Instant = Instant.now()): Segment? =
        dossier.segments.chronological().firstOrNull { it.endEpochMillis >= now.toEpochMilli() }
}
