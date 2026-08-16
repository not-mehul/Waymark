package com.waymark.domain.model

import java.time.LocalDate

/**
 * Something the party wants to do, see or eat, which does not yet have a time
 * on it — and may never need one.
 *
 * This is the half of a trip that reservations cannot hold. A museum you might
 * get to, a dish you were told to try, a walk if the weather turns. Ideas live
 * beside the itinerary and can be promoted onto it with a date, at which point
 * they become an ordinary [Segment.Experience] and behave like every other
 * booking.
 */
data class Idea(
    val id: String,
    val tripId: String,
    val title: String,
    val kind: IdeaKind,
    val city: String,
    val place: Place? = null,
    val note: String? = null,
    val priceBand: PriceBand? = null,
    val typicalMinutes: Int? = null,
    val bestTime: String? = null,
    /**
     * A day without a time. Most planning happens at this resolution — "the
     * market, Tuesday, sometime" — and forcing a clock time onto it produces
     * a schedule nobody keeps.
     */
    val plannedDate: LocalDate? = null,
    val interestedTravelerIds: Set<String> = emptySet(),
    val status: IdeaStatus = IdeaStatus.SAVED,
    val scheduledSegmentId: String? = null,
    val source: String = SOURCE_TRAVELER,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    val hasLocation: Boolean get() = place?.hasCoordinates == true

    /** On the list with a day against it, but no booking behind it yet. */
    val isPencilled: Boolean get() = plannedDate != null && status == IdeaStatus.SAVED

    /**
     * Something to look for rather than somewhere to go — a dish, a pastry, a
     * local beer. It is a property of the idea, not a category of its own: an
     * eating idea with an address is a restaurant, and one without is a thing
     * to order wherever you end up.
     */
    val isPlaceless: Boolean get() = kind == IdeaKind.EAT && place == null

    companion object {
        const val SOURCE_TRAVELER = "Added by you"
        const val SOURCE_GUIDE = "Bundled guide"
    }
}

/**
 * The four things a trip list is ever made of.
 *
 * There were six — See, Eat, Table, Walk, Do, Shop — and nobody could tell
 * "Eat" from "Table" without being told that one meant a dish and the other a
 * restaurant. That distinction is real, but it is not a *category*: it is
 * whether the thing has an address, which the idea already knows from whether
 * it carries a [Place]. So the kinds are now the four questions a traveler
 * actually asks about a city, and the dish-versus-restaurant difference is
 * derived rather than chosen.
 *
 * "Walk" folded into See — a walk is a route between things worth seeing —
 * and "Activity" folded into Do, which is what it always meant.
 */
enum class IdeaKind {
    SEE, EAT, DO, SHOP;

    /** The chip. One word, imperative, the way the decision is phrased out loud. */
    val label: String
        get() = when (this) {
            SEE -> "See"
            EAT -> "Eat"
            DO -> "Do"
            SHOP -> "Shop"
        }

    /** Section heading in the plural, for the board. */
    val heading: String
        get() = when (this) {
            SEE -> "Places to see"
            EAT -> "Food and tables"
            DO -> "Things to do"
            SHOP -> "Shops and markets"
        }

    /** The one-line hint under the chip when the list is being added to. */
    val hint: String
        get() = when (this) {
            SEE -> "Museums, views, landmarks, a walk"
            EAT -> "A restaurant, or a dish to look for"
            DO -> "Tours, classes, swimming, a show"
            SHOP -> "Markets, bookshops, something to bring back"
        }
}

enum class IdeaStatus {
    /** On the list, no date. */
    SAVED,

    /** Promoted onto the timeline; [Idea.scheduledSegmentId] points at it. */
    SCHEDULED,

    /** Seen, eaten, walked. */
    DONE,

    /** Considered and declined — kept so it stops being suggested. */
    DISMISSED;

    val label: String
        get() = when (this) {
            SAVED -> "Saved"
            SCHEDULED -> "On the timeline"
            DONE -> "Done"
            DISMISSED -> "Not this trip"
        }
}

enum class PriceBand {
    FREE, LOW, MEDIUM, HIGH;

    /** Deliberately not currency: a band survives a change of country. */
    val label: String
        get() = when (this) {
            FREE -> "Free"
            LOW -> "Cheap"
            MEDIUM -> "Mid"
            HIGH -> "Splurge"
        }
}
