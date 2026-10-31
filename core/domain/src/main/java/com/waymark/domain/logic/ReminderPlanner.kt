package com.waymark.domain.logic

import com.waymark.domain.model.Segment
import com.waymark.domain.model.SegmentKind
import com.waymark.domain.model.chronological

/**
 * How long before something the traveler wants to hear about it.
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

        /** The choices offered for a booking's start. */
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

/** What a reminder is about: the booking starting, check-in opening, or time to go. */
enum class ReminderMoment(val signaturePrefix: String) {
    START("reminder"),
    CHECK_IN("checkin"),
    LEAVE("leave"),
}

/**
 * A kind of reminder, and what it is called on the settings sheet.
 *
 * Four are about a booking starting, named for what a traveler would call the
 * kind of booking. Two are about flights in particular: online check-in, which
 * opens about a day ahead and is the thing most often remembered too late, and
 * the moment to leave for the airport — worked out from where the trip is
 * before the flight and how long the journey to the airport takes.
 */
enum class ReminderCategory(
    val kind: SegmentKind,
    val moment: ReminderMoment,
    val label: String,
    val detail: String,
    val default: Lead,
    val choices: List<Lead> = Lead.CHOICES,
) {
    FLIGHTS(SegmentKind.FLIGHT, ReminderMoment.START, "Flights", "Before a departure", Lead(3 * 60)),
    LEAVE(
        SegmentKind.FLIGHT, ReminderMoment.LEAVE, "Leave for the airport",
        "Warning before it is time to go", Lead(30),
        listOf(Lead.OFF, Lead(15), Lead(30), Lead(60)),
    ),
    CHECK_IN(
        SegmentKind.FLIGHT, ReminderMoment.CHECK_IN, "Online check-in",
        "Before departure, when check-in usually opens", Lead(24 * 60),
        listOf(Lead.OFF, Lead(24 * 60), Lead(36 * 60), Lead(48 * 60)),
    ),
    GROUND(SegmentKind.GROUND, ReminderMoment.START, "Trains and transfers", "Before a ground leg", Lead(60)),
    BOOKINGS(SegmentKind.EXPERIENCE, ReminderMoment.START, "Bookings", "Before a table, a tour, a ticket", Lead(60)),
    STAYS(SegmentKind.LODGING, ReminderMoment.START, "Stays", "Before a check-in", Lead.OFF);

    companion object {
        fun forSegment(kind: SegmentKind): List<ReminderCategory> = entries.filter { it.kind == kind }
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
        val on = ReminderCategory.entries.filter { this[it].isOn && it.moment == ReminderMoment.START }
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
    val tripId: String,
    val moment: ReminderMoment,
    /** What the booking is called, for the notification's own record. */
    val label: String,
    /** Stable across runs, so a thing is announced once rather than every check. */
    val signature: String,
    val headline: String,
    val detail: String,
    val startEpochMillis: Long,
)

/**
 * Which of a trip's bookings are close enough to say something about, and when
 * the next one will be.
 *
 * A plain function over segments and a clock, so the decision of what is due —
 * the part that is easy to get subtly wrong — is tested on the JVM rather than
 * inferred from a phone that did or did not buzz.
 *
 * Every reminder has a moment it fires and a moment after which it is no use:
 * a departure reminder after the departure, a check-in reminder once check-in
 * has closed, a "time to leave" half an hour after the time to leave. The
 * signature carries the booking and its start and deliberately **not** the
 * countdown, so a check at two hours out does not re-announce what one at
 * three hours out already said — but moving a booking raises a fresh one.
 */
object ReminderPlanner {

    /** Online check-in closes about an hour before departure. */
    private const val CHECK_IN_CLOSES_MINUTES = 60

    /** "Time to leave" stays useful a little after the time has come. */
    private const val LEAVE_GRACE_MINUTES = 30

    /** How early to be at the airport. */
    private const val AIRPORT_DOMESTIC_MINUTES = 120
    private const val AIRPORT_INTERNATIONAL_MINUTES = 180

    /** Farther than this from the airport is not a journey "to the airport". */
    private const val MAX_AIRPORT_JOURNEY_KM = 150.0

    private data class Plan(
        val segment: Segment,
        val category: ReminderCategory,
        val fireAtMillis: Long,
        val usefulUntilMillis: Long,
    )

    /**
     * Everything due at [nowMillis]. [itinerary] is every booking of the trips
     * concerned — the time to leave for an airport depends on where the trip
     * is before the flight, not only on the flight.
     */
    fun due(
        itinerary: List<Segment>,
        preferences: ReminderPreferences,
        nowMillis: Long,
    ): List<DueReminder> = plans(itinerary, preferences)
        .filter { nowMillis >= it.fireAtMillis && nowMillis < it.usefulUntilMillis }
        .map { plan ->
            DueReminder(
                segmentId = plan.segment.id,
                tripId = plan.segment.tripId,
                moment = plan.category.moment,
                label = labelOf(plan.segment),
                signature = signature(plan.category.moment, plan.segment),
                headline = headline(plan.segment, plan.category.moment, nowMillis, itinerary),
                detail = detail(plan.segment, plan.category.moment, itinerary),
                startEpochMillis = plan.segment.startEpochMillis,
            )
        }

    /** When the next reminder falls due after [nowMillis], or null if none ever will. */
    fun nextDueAt(
        itinerary: List<Segment>,
        preferences: ReminderPreferences,
        nowMillis: Long,
    ): Long? = plans(itinerary, preferences)
        .map { it.fireAtMillis }
        .filter { it > nowMillis }
        .minOrNull()

    /** A reminder's text as of now — what the trip screen's banner says, live. */
    fun headline(
        segment: Segment,
        moment: ReminderMoment,
        nowMillis: Long,
        itinerary: List<Segment> = listOf(segment),
    ): String = when (moment) {
        ReminderMoment.START -> startHeadline(segment, minutesUntil(segment.startEpochMillis, nowMillis))

        ReminderMoment.CHECK_IN -> "Check in online for ${labelOf(segment)}"

        ReminderMoment.LEAVE -> {
            val leaveBy = leaveBy(segment, itinerary)
            val place = segment.origin.shortLabel
            if (leaveBy == null || nowMillis >= leaveBy) {
                "Time to leave for $place"
            } else {
                "Leave for $place in ${TimeText.duration(minutesUntil(leaveBy, nowMillis))}"
            }
        }
    }

    /** Which moment a stored reminder was about, from its signature. */
    fun momentOf(signature: String): ReminderMoment =
        ReminderMoment.entries.firstOrNull { signature.startsWith("${it.signaturePrefix}|") }
            ?: ReminderMoment.START

    fun signature(moment: ReminderMoment, segment: Segment): String =
        "${moment.signaturePrefix}|${segment.id}|${segment.startEpochMillis}"

    private fun plans(itinerary: List<Segment>, preferences: ReminderPreferences): List<Plan> =
        itinerary.chronological().flatMap { segment ->
            ReminderCategory.forSegment(segment.kind).mapNotNull { category ->
                val lead = preferences[category]
                if (!lead.isOn) return@mapNotNull null
                val leadMillis = lead.minutes * 60_000L
                when (category.moment) {
                    ReminderMoment.START -> Plan(
                        segment, category,
                        fireAtMillis = segment.startEpochMillis - leadMillis,
                        usefulUntilMillis = segment.startEpochMillis + 1,
                    )

                    ReminderMoment.CHECK_IN -> Plan(
                        segment, category,
                        fireAtMillis = segment.startEpochMillis - leadMillis,
                        usefulUntilMillis = segment.startEpochMillis - CHECK_IN_CLOSES_MINUTES * 60_000L,
                    )

                    ReminderMoment.LEAVE -> leaveBy(segment, itinerary)?.let { leaveBy ->
                        Plan(
                            segment, category,
                            fireAtMillis = leaveBy - leadMillis,
                            usefulUntilMillis = minOf(
                                leaveBy + LEAVE_GRACE_MINUTES * 60_000L,
                                segment.startEpochMillis,
                            ),
                        )
                    }
                }
            }
        }

    /**
     * When to set off for a flight's airport: the journey there from wherever
     * the trip is before it, plus time at the airport. Null when there is no
     * journey to make — a connection, or a place the app cannot locate.
     */
    fun leaveBy(flight: Segment, itinerary: List<Segment>): Long? {
        if (flight !is Segment.Flight) return null
        val from = departurePoint(flight, itinerary) ?: return null
        if (!from.hasCoordinates || !flight.origin.hasCoordinates) return null
        if (from.code != null && from.code == flight.origin.code) return null
        // Next door is already there; a city away is a gap in the itinerary,
        // not a journey to the airport anyone could be reminded about.
        val km = Geo.distanceKm(from, flight.origin)
        if (km < 0.5 || km > MAX_AIRPORT_JOURNEY_KM) return null
        val journey = TransitEstimator.estimateBest(from, flight.origin).totalMinutes
        return flight.startEpochMillis - (airportMinutes(flight) + journey) * 60_000L
    }

    private fun airportMinutes(flight: Segment.Flight): Int {
        val from = flight.origin.country
        val to = flight.destination.country
        val international = from.isNotBlank() && to.isNotBlank() && from != to
        return if (international) AIRPORT_INTERNATIONAL_MINUTES else AIRPORT_DOMESTIC_MINUTES
    }

    private fun minutesUntil(targetMillis: Long, nowMillis: Long): Int =
        ((targetMillis - nowMillis).coerceAtLeast(0L) / 60_000L).toInt()

    private fun labelOf(segment: Segment): String = when (segment) {
        is Segment.Flight -> segment.designator
        is Segment.Lodging -> segment.propertyName
        is Segment.Ground -> segment.mode.label
        is Segment.Experience -> segment.name
    }

    private fun startHeadline(segment: Segment, minutesOut: Int): String {
        val whenText = if (minutesOut < 1) "now" else "in ${TimeText.duration(minutesOut)}"
        return when (segment) {
            is Segment.Flight -> "${segment.designator} departs $whenText"
            is Segment.Lodging -> "Check in at ${segment.propertyName} $whenText"
            is Segment.Ground -> "${segment.mode.label} to ${segment.destination.shortLabel} $whenText"
            is Segment.Experience -> "${segment.name} $whenText"
        }
    }

    private fun detail(segment: Segment, moment: ReminderMoment, itinerary: List<Segment>): String =
        when (moment) {
            ReminderMoment.START -> startDetail(segment)

            ReminderMoment.CHECK_IN ->
                "Departs ${TimeText.dayCompact(segment.start.toLocalDate())} at " +
                    "${TimeText.clock(segment.start)} from ${segment.origin.shortLabel}"

            ReminderMoment.LEAVE -> leaveDetail(segment, itinerary)
        }

    private fun leaveDetail(flight: Segment, itinerary: List<Segment>): String {
        val from = departurePoint(flight, itinerary)
        val airport = (flight as? Segment.Flight)?.let(::airportMinutes) ?: AIRPORT_DOMESTIC_MINUTES
        return buildString {
            if (from != null) {
                val journey = TransitEstimator.estimateBest(from, flight.origin)
                append("${journey.summary} from ${from.name}, ")
            }
            append("to be there ${TimeText.duration(airport)} before ${labelOf(flight)}")
        }
    }

    /** Where the traveler sets off from for a flight: wherever the trip was just before it. */
    private fun departurePoint(flight: Segment, itinerary: List<Segment>) =
        Whereabouts.before(itinerary.filter { it.id != flight.id }, flight.startEpochMillis - 1)

    private fun startDetail(segment: Segment): String = when (segment) {
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
