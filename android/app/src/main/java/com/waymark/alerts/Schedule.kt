package com.waymark.alerts

import com.waymark.Spans
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the page has worked out for the shell to do while the app is closed:
 * every reminder still worth posting, with its words and the moment it falls
 * due, and every journey to draw as an ongoing notification. Sent as JSON by
 * `shellSchedule` (src/state/shell-data.ts) whenever a trip or a reminder
 * setting changes, and kept until the next one replaces it.
 */
data class Schedule(
    val reminders: List<ScheduledReminder>,
    val journeys: List<JourneyWindow>,
) {

    /** What is due at [nowMillis]: fallen due, and still worth posting. */
    fun due(nowMillis: Long): List<ScheduledReminder> =
        reminders.filter { nowMillis >= it.fireAtMillis && nowMillis < it.usefulUntilMillis }

    /** The journey to show now, if any: the earliest-starting one whose window is open. */
    fun journey(nowMillis: Long): LiveJourney? {
        val window = journeys
            .filter { nowMillis >= it.showFromMillis && nowMillis < it.endMillis }
            .minByOrNull { it.startMillis }
            ?: return null
        return LiveJourney.of(window, nowMillis)
    }

    /**
     * When to look again: the next reminder, or the next redraw of the journey
     * (every quarter of an hour while one shows, at its departure and arrival,
     * and when the next window opens). Null when nothing will ever be due.
     */
    fun nextAlarmAt(nowMillis: Long): Long? {
        val nextReminder = reminders.map { it.fireAtMillis }.filter { it > nowMillis }.minOrNull()
        val opening = journeys.map { it.showFromMillis }.filter { it > nowMillis }.minOrNull()
        val turning = journey(nowMillis)?.let {
            minOf(nowMillis + LiveJourney.TICK_MILLIS, maxOf(it.targetMillis, nowMillis + Spans.MINUTE_MILLIS))
        }
        return listOfNotNull(nextReminder, opening, turning).minOrNull()
    }

    val signatures: Set<String> get() = reminders.mapTo(HashSet()) { it.signature }

    fun toJson(): JSONObject = JSONObject()
        .put("reminders", JSONArray(reminders.map { it.toJson() }))
        .put("journeys", JSONArray(journeys.map { it.toJson() }))

    companion object {
        val EMPTY = Schedule(emptyList(), emptyList())

        /** Reads what the page sent; anything malformed is left out rather than failing the rest. */
        fun fromJson(json: JSONObject): Schedule = Schedule(
            reminders = json.optJSONArray("reminders").objects().mapNotNull(ScheduledReminder::fromJson),
            journeys = json.optJSONArray("journeys").objects().mapNotNull(JourneyWindow::fromJson),
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

data class ScheduledReminder(
    /** Stable across pushes, so a reminder is posted once however often the page sends it. */
    val signature: String,
    val tripId: String,
    val segmentId: String,
    val headline: String,
    val detail: String,
    val startMillis: Long,
    val fireAtMillis: Long,
    val usefulUntilMillis: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("signature", signature)
        .put("tripId", tripId)
        .put("segmentId", segmentId)
        .put("headline", headline)
        .put("detail", detail)
        .put("startEpochMillis", startMillis)
        .put("fireAtMillis", fireAtMillis)
        .put("usefulUntilMillis", usefulUntilMillis)

    companion object {
        fun fromJson(json: JSONObject): ScheduledReminder? = runCatching {
            ScheduledReminder(
                signature = json.getString("signature"),
                tripId = json.getString("tripId"),
                segmentId = json.getString("segmentId"),
                headline = json.getString("headline"),
                detail = json.optString("detail"),
                startMillis = json.getLong("startEpochMillis"),
                fireAtMillis = json.getLong("fireAtMillis"),
                usefulUntilMillis = json.getLong("usefulUntilMillis"),
            )
        }.getOrNull()
    }
}

data class JourneyWindow(
    val tripId: String,
    val segmentId: String,
    val title: String,
    val flight: Boolean,
    val showFromMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tripId", tripId)
        .put("segmentId", segmentId)
        .put("title", title)
        .put("kind", if (flight) "FLIGHT" else "GROUND")
        .put("showFromMillis", showFromMillis)
        .put("startMillis", startMillis)
        .put("endMillis", endMillis)

    companion object {
        fun fromJson(json: JSONObject): JourneyWindow? = runCatching {
            JourneyWindow(
                tripId = json.getString("tripId"),
                segmentId = json.getString("segmentId"),
                title = json.getString("title"),
                flight = json.optString("kind") == "FLIGHT",
                showFromMillis = json.getLong("showFromMillis"),
                startMillis = json.getLong("startMillis"),
                endMillis = json.getLong("endMillis"),
            )
        }.getOrNull()?.takeIf { it.endMillis > it.startMillis }
    }
}

/**
 * A journey as it happens: a countdown to departure, then a bar that fills
 * until arrival. The same as `liveJourneyOf` in src/domain/live-journey.ts.
 */
data class LiveJourney(
    val window: JourneyWindow,
    val departed: Boolean,
    /** How far along, out of [PROGRESS_MAX]; zero until departure. */
    val progress: Int,
    /** The few characters the status bar has room for: "40m", "6h 5m". */
    val chip: String,
    /** "Departs in 40m", "Lands in 6h 5m". */
    val headline: String,
    /** The moment the chip is counting down to. */
    val targetMillis: Long,
) {
    companion object {
        const val PROGRESS_MAX = 1_000

        /** How often the notification is brought up to date while it is showing. */
        const val TICK_MILLIS = 15 * Spans.MINUTE_MILLIS

        fun of(window: JourneyWindow, nowMillis: Long): LiveJourney {
            val departed = nowMillis >= window.startMillis
            val target = if (departed) window.endMillis else window.startMillis
            val left = Spans.duration(Spans.minutesUntil(target, nowMillis))
            return LiveJourney(
                window = window,
                departed = departed,
                progress = if (departed) {
                    ((nowMillis - window.startMillis) * PROGRESS_MAX / (window.endMillis - window.startMillis)).toInt()
                } else {
                    0
                },
                chip = left,
                headline = when {
                    !departed -> "Departs in $left"
                    window.flight -> "Lands in $left"
                    else -> "Arrives in $left"
                },
                targetMillis = target,
            )
        }
    }
}
