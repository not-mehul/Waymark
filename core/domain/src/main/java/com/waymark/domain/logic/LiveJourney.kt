package com.waymark.domain.logic

import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind

/**
 * A flight or a ground leg as it happens: what the ongoing notification — a
 * Live Update, on Android 16 and later — shows from a little before departure
 * until arrival.
 *
 * Shown only for the kinds a traveler has departure reminders on for, so
 * turning "Flights" off in the reminder settings quiets this too.
 */
data class LiveJourney(
    val segment: Segment,
    val phase: Phase,
    /** How far along, out of [PROGRESS_MAX]; zero until departure. */
    val progress: Int,
    /** The few characters the status bar has room for: "40m", "6h 5m". */
    val chip: String,
    /** "Departs in 40m", "Lands in 6h 5m". */
    val headline: String,
    /** The moment the chip is counting down to. */
    val targetMillis: Long,
) {
    enum class Phase { DEPARTING, UNDER_WAY }

    companion object {
        const val PROGRESS_MAX = 1_000

        /** How often the notification is brought up to date while it is showing. */
        const val TICK_MILLIS = 15 * 60_000L

        private const val MINUTE = 60_000L

        /** How long before departure the journey starts showing. */
        private fun leadMillis(kind: SegmentKind): Long = when (kind) {
            SegmentKind.FLIGHT -> 3 * 60 * MINUTE
            else -> 30 * MINUTE
        }

        private fun shown(segment: Segment, preferences: ReminderPreferences): Boolean =
            (segment.kind == SegmentKind.FLIGHT || segment.kind == SegmentKind.GROUND) &&
                segment.endEpochMillis > segment.startEpochMillis &&
                ReminderCategory.entries.any {
                    it.kind == segment.kind && it.moment == ReminderMoment.START && preferences[it].isOn
                }

        private fun window(segment: Segment): LongRange =
            (segment.startEpochMillis - leadMillis(segment.kind)) until segment.endEpochMillis

        /** The journey to show now, if any: the earliest-starting one in its window. */
        fun of(itinerary: List<Segment>, preferences: ReminderPreferences, nowMillis: Long): LiveJourney? {
            val segment = itinerary
                .filter { shown(it, preferences) && nowMillis in window(it) }
                .minByOrNull { it.startEpochMillis }
                ?: return null
            val departed = nowMillis >= segment.startEpochMillis
            val target = if (departed) segment.endEpochMillis else segment.startEpochMillis
            val left = TimeText.duration(minutesUntil(target, nowMillis))
            return LiveJourney(
                segment = segment,
                phase = if (departed) Phase.UNDER_WAY else Phase.DEPARTING,
                progress = if (departed) {
                    ((nowMillis - segment.startEpochMillis) * PROGRESS_MAX / segment.duration.toMillis()).toInt()
                } else {
                    0
                },
                chip = left,
                headline = when {
                    !departed -> "Departs in $left"
                    segment.kind == SegmentKind.FLIGHT -> "Lands in $left"
                    else -> "Arrives in $left"
                },
                targetMillis = target,
            )
        }

        /**
         * When the notification next needs redrawing: the next tick while one
         * is showing, its departure or arrival if sooner, or the moment the
         * next journey's window opens.
         */
        fun nextChangeAt(itinerary: List<Segment>, preferences: ReminderPreferences, nowMillis: Long): Long? {
            val candidates = itinerary.filter { shown(it, preferences) }
            val live = of(candidates, preferences, nowMillis)
            val opening = candidates.map { window(it).first }.filter { it > nowMillis }.minOrNull()
            val turning = live?.let {
                minOf(nowMillis + TICK_MILLIS, it.targetMillis.coerceAtLeast(nowMillis + MINUTE))
            }
            return listOfNotNull(opening, turning).minOrNull()
        }

        /** Whole minutes until [targetMillis], rounded up so "1m" means not yet. */
        private fun minutesUntil(targetMillis: Long, nowMillis: Long): Int =
            ((targetMillis - nowMillis + MINUTE - 1) / MINUTE).toInt()
    }
}
