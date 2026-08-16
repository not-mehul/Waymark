package com.waymark.domain.model

/**
 * A passenger profile. Profiles are global — the same person can be carried
 * across trips — and are attached to a trip through [TripParty].
 */
data class Traveler(
    val id: String,
    val fullName: String,
    val nickname: String? = null,
    val seatPreference: SeatPreference = SeatPreference.NONE,
    val mealPreference: String? = null,
    val loyaltyRefs: List<String> = emptyList(),
    val documentRef: String? = null,
    val contactPhone: String? = null,
) {
    val displayName: String get() = nickname ?: fullName

    /** Up to two letters, used for the party marks throughout the app. */
    val initials: String
        get() = fullName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .let { parts ->
                when {
                    parts.isEmpty() -> "?"
                    parts.size == 1 -> parts[0].take(2).uppercase()
                    else -> "${parts.first().first()}${parts.last().first()}".uppercase()
                }
            }
}

enum class SeatPreference { NONE, WINDOW, AISLE, EXIT_ROW, BULKHEAD }

/** The set of travelers on a trip, in display order. */
data class TripParty(val tripId: String, val travelers: List<Traveler>) {
    fun byId(id: String): Traveler? = travelers.firstOrNull { it.id == id }
    fun resolve(ids: Collection<String>): List<Traveler> =
        travelers.filter { it.id in ids }
}
