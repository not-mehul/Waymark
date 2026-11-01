package com.waymark.widget

import com.waymark.Spans
import org.json.JSONArray
import org.json.JSONObject

/**
 * One booking as the page hands it to the widget (`widgetEntries` in
 * src/state/shell-data.ts): what it is called and when it starts, in the
 * booking's own time zone and the phone's clock style. The widget works out
 * "now", "next" and "in 3h" from these as time passes.
 */
data class WidgetEntry(
    val tripId: String,
    val tripName: String,
    val segmentId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    /** "Thu 14 May". */
    val day: String,
    /** "09:40". */
    val clock: String,
    /** "London Heathrow". */
    val place: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tripId", tripId)
        .put("tripName", tripName)
        .put("segmentId", segmentId)
        .put("title", title)
        .put("startMillis", startMillis)
        .put("endMillis", endMillis)
        .put("day", day)
        .put("clock", clock)
        .put("place", place)

    companion object {
        fun fromJson(json: JSONObject): WidgetEntry? = runCatching {
            WidgetEntry(
                tripId = json.getString("tripId"),
                tripName = json.getString("tripName"),
                segmentId = json.getString("segmentId"),
                title = json.getString("title"),
                startMillis = json.getLong("startMillis"),
                endMillis = json.getLong("endMillis"),
                day = json.optString("day"),
                clock = json.optString("clock"),
                place = json.optString("place"),
            )
        }.getOrNull()

        fun listFromJson(array: JSONArray?): List<WidgetEntry> = if (array == null) {
            emptyList()
        } else {
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::fromJson) }
        }
    }
}

/** One booking as the widget says it. */
data class NextUp(
    val eyebrow: String,
    val title: String,
    val detail: String,
    /** "in 3h", "now", or a day and time when it is further off. */
    val soon: String,
    val tripId: String?,
    val segmentId: String?,
) {
    companion object {
        /** The first booking and the two after it, for the tall widget. */
        const val SHOWN = 3
        private const val DAY_MILLIS = 24 * 60 * Spans.MINUTE_MILLIS

        val NOTHING = NextUp(
            eyebrow = "Waymark",
            title = "Nothing coming up",
            detail = "Add a trip and its next booking shows here.",
            soon = "Nothing coming up",
            tripId = null,
            segmentId = null,
        )

        /** What the widget picker shows: a booking of the kind the widget is for. */
        val PREVIEW = listOf(
            NextUp(
                eyebrow = "Next · Lisbon",
                title = "TP 1351 · LHR → LIS",
                detail = "Thu 14 May, 09:40 · London Heathrow · in 3h",
                soon = "in 3h",
                tripId = null,
                segmentId = null,
            ),
            NextUp("", "Memmo Alfama", "", "Thu 15:00", null, null),
            NextUp("", "Tram 28 · Graça → Estrela", "", "Fri 10:30", null, null),
        )

        /** What is under way or next at [nowMillis], soonest first; ended bookings drop off. */
        fun of(entries: List<WidgetEntry>, nowMillis: Long): List<NextUp> = entries
            .filter { it.endMillis >= nowMillis }
            .sortedBy { it.startMillis }
            .take(SHOWN)
            .map { entry ->
                val under = nowMillis >= entry.startMillis
                NextUp(
                    eyebrow = if (under) "Now · ${entry.tripName}" else "Next · ${entry.tripName}",
                    title = entry.title,
                    detail = buildString {
                        append(entry.day).append(", ").append(entry.clock)
                        if (entry.place.isNotEmpty()) append(" · ").append(entry.place)
                        if (!under) append(" · ").append(Spans.relative(entry.startMillis, nowMillis))
                    },
                    // Within a day, how long; further off, the day and the time.
                    soon = if (entry.startMillis - nowMillis < DAY_MILLIS) {
                        Spans.relative(entry.startMillis, nowMillis)
                    } else {
                        "${entry.day} ${entry.clock}"
                    },
                    tripId = entry.tripId,
                    segmentId = entry.segmentId,
                )
            }
            .ifEmpty { listOf(NOTHING) }
    }
}
