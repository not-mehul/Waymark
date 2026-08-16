package com.waymark.domain.logic

import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.chronological

/**
 * How long before something starts the traveler wants to hear about it.
 *
 * Stored as minutes rather than an enum so the set of offered choices can change
 * without a migration, and `0` means off — which is a real answer, not an
 * absence. Somebody who wants to be told about flights and nothing else should
 * be able to say exactly that.
 */
@JvmInline
value class Lead(val minutes: Int) {
    val isOn: Boolean get() = minutes > 0

    companion object {
        val OFF = Lead(0)

        /**
         * The choices offered per category. Fifteen minutes is the floor
         * because the check that raises these runs on a fifteen-minute
         * schedule; anything finer would be a promise the platform will not
         * keep.
         */
        val CHOICES: List<Lead> = listOf(
            OFF,
            Lead(15),
            Lead(30),
            Lead(60),
            Lead(2 * 60),
            Lead(3 * 60),
            Lead(6 * 60),
            Lead(12 * 60),
            Lead(24 * 60),
        )
    }
}

/**
 * A category of thing that can be reminded about, and what it is called on the
 * settings screen.
 *
 * These are the four segment kinds, named for what a traveler would call them
 * rather than for the class. The defaults are the ones that survive contact
 * with a real trip: three hours before a flight is roughly when leaving for the
 * airport starts to matter; a booked table needs an hour; a hotel check-in is
 * not something anyone needs waking for, so it starts off.
 */
enum class ReminderCategory(
    val kind: SegmentKind,
    val label: String,
    val detail: String,
    val default: Lead,
) {
    FLIGHTS(SegmentKind.FLIGHT, "Flights", "Before a departure", Lead(3 * 60)),
    GROUND(SegmentKind.GROUND, "Trains and transfers", "Before a ground leg", Lead(60)),
    BOOKINGS(SegmentKind.EXPERIENCE, "Bookings", "Before a table, a tour, a ticket", Lead(60)),
    STAYS(SegmentKind.LODGING, "Stays", "Before a check-in", Lead.OFF);

    companion object {
        fun of(kind: SegmentKind): ReminderCategory = entries.first { it.kind == kind }
    }
}

/** Every category's lead time. Absent means the category's own default. */
data class ReminderPreferences(
    private val leads: Map<ReminderCategory, Lead> = emptyMap(),
) {
    operator fun get(category: ReminderCategory): Lead = leads[category] ?: category.default

    fun with(category: ReminderCategory, lead: Lead): ReminderPreferences =
        copy(leads = leads + (category to lead))

    val anyOn: Boolean get() = ReminderCategory.entries.any { this[it].isOn }

    /** For the menu row: "Flights 3h · Bookings 1h", or what is off. */
    fun summary(): String {
        val on = ReminderCategory.entries.filter { this[it].isOn }
        if (on.isEmpty()) return "Off for everything"
        return on.joinToString(" · ") { "${it.label} ${TimeText.duration(this[it].minutes)}" }
    }

    companion object {
        val DEFAULT = ReminderPreferences()
    }
}

/** One reminder that is ready to be posted. */
data class DueReminder(
    val segmentId: String,
    /** What the booking is called, for the notification's own record. */
    val label: String,
    /** Stable across runs, so a thing is announced once rather than every check. */
    val signature: String,
    val headline: String,
    val detail: String,
    val startEpochMillis: Long,
)

/**
 * Which of a trip's bookings are close enough to say something about.
 *
 * This used to be four lines inside a WorkManager worker, and it only knew
 * about flights: a hotel check-in, a Eurostar and a booked table all passed
 * unremarked, which made "reminders" a promise the app kept for a quarter of
 * what it stored. It is a plain function over segments and a clock now, so the
 * decision of what is due — the part that is easy to get subtly wrong — is
 * tested on the JVM rather than inferred from a phone that did or did not buzz.
 *
 * The rule is the same for every category: a booking is due when its start is
 * within the category's lead time and has not already gone. The signature
 * carries the segment and its start time and deliberately **not** the
 * countdown, so a run at two hours out does not re-announce what a run at three
 * hours out already said — but moving a booking does raise a fresh reminder,
 * which is correct: the time changed.
 */
object ReminderPlanner {

    fun due(
        segments: List<Segment>,
        preferences: ReminderPreferences,
        nowMillis: Long,
    ): List<DueReminder> = segments
        .chronological()
        .mapNotNull { segment ->
            val lead = preferences[ReminderCategory.of(segment.kind)]
            if (!lead.isOn) return@mapNotNull null

            val untilStart = segment.startEpochMillis - nowMillis
            if (untilStart < 0L) return@mapNotNull null
            if (untilStart > lead.minutes * 60_000L) return@mapNotNull null

            val minutesOut = (untilStart / 60_000L).toInt()
            DueReminder(
                segmentId = segment.id,
                label = labelOf(segment),
                signature = "reminder|${segment.id}|${segment.startEpochMillis}",
                headline = headline(segment, minutesOut),
                detail = detail(segment),
                startEpochMillis = segment.startEpochMillis,
            )
        }

    /** The longest lead in use, so the caller knows how far ahead to read. */
    fun lookAheadMillis(preferences: ReminderPreferences): Long =
        ReminderCategory.entries
            .maxOf { preferences[it].minutes }
            .toLong() * 60_000L

    private fun labelOf(segment: Segment): String = when (segment) {
        is Segment.Flight -> segment.designator
        is Segment.Lodging -> segment.propertyName
        is Segment.Ground -> segment.mode.label
        is Segment.Experience -> segment.name
    }

    private fun headline(segment: Segment, minutesOut: Int): String {
        val whenText = if (minutesOut < 1) "now" else "in ${TimeText.duration(minutesOut)}"
        return when (segment) {
            is Segment.Flight -> "${segment.designator} departs $whenText"
            is Segment.Lodging -> "Check in at ${segment.propertyName} $whenText"
            is Segment.Ground -> "${segment.mode.label} to ${segment.destination.shortLabel} $whenText"
            is Segment.Experience -> "${segment.name} $whenText"
        }
    }

    private fun detail(segment: Segment): String = when (segment) {
        is Segment.Flight -> buildString {
            append(segment.origin.shortLabel)
            segment.departureTerminal?.let { append(", terminal $it") }
            segment.departureGate?.let { append(", gate $it") }
            append(" → ${segment.destination.shortLabel}")
        }

        is Segment.Lodging -> listOfNotNull(
            segment.origin.city.ifBlank { null } ?: segment.origin.name,
            segment.roomDescription,
        ).joinToString(" · ")

        is Segment.Ground -> buildString {
            append("${segment.origin.shortLabel} → ${segment.destination.shortLabel}")
            segment.provider?.let { append(" · $it") }
        }

        is Segment.Experience -> listOfNotNull(
            segment.origin.city.ifBlank { null } ?: segment.origin.name,
            segment.category.ifBlank { null },
        ).joinToString(" · ")
    }
}
