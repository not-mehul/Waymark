package com.waymark.domain.model

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
    val interestedTravelerIds: Set<String> = emptySet(),
    val status: IdeaStatus = IdeaStatus.SAVED,
    val scheduledSegmentId: String? = null,
    val source: String = SOURCE_TRAVELER,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    val hasLocation: Boolean get() = place?.hasCoordinates == true

    /** A dish has no address; everything else can be put on the map. */
    val isPlaceless: Boolean get() = kind == IdeaKind.DISH && place == null

    companion object {
        const val SOURCE_TRAVELER = "Added by you"
        const val SOURCE_GUIDE = "Bundled guide"
    }
}

enum class IdeaKind {
    SIGHT, DISH, EATERY, WALK, ACTIVITY, SHOP;

    val label: String
        get() = when (this) {
            SIGHT -> "See"
            DISH -> "Eat"
            EATERY -> "Table"
            WALK -> "Walk"
            ACTIVITY -> "Do"
            SHOP -> "Shop"
        }

    /** Section heading in the plural, for the board. */
    val heading: String
        get() = when (this) {
            SIGHT -> "Places to see"
            DISH -> "Food to try"
            EATERY -> "Places to eat"
            WALK -> "Walks"
            ACTIVITY -> "Things to do"
            SHOP -> "Shops and markets"
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
