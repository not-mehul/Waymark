package com.waymark.domain.model

/**
 * One line on a packing list. Items belong to a traveler, or to the party when
 * [travelerId] is null — nobody needs two travel adaptors and a shared list is
 * how that gets agreed rather than discovered at the gate.
 */
data class PackingItem(
    val id: String,
    val tripId: String,
    val travelerId: String?,
    val title: String,
    val category: PackingCategory,
    val quantity: Int = 1,
    val packed: Boolean = false,
    val essential: Boolean = false,
    val note: String? = null,
    val source: String = SOURCE_TRAVELER,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    val isShared: Boolean get() = travelerId == null

    companion object {
        const val SOURCE_TRAVELER = "Added by you"
        const val SOURCE_SUGGESTED = "Suggested"
    }
}

enum class PackingCategory {
    DOCUMENTS, CLOTHING, TOILETRIES, ELECTRONICS, HEALTH, GEAR, OTHER;

    val label: String
        get() = when (this) {
            DOCUMENTS -> "Documents"
            CLOTHING -> "Clothing"
            TOILETRIES -> "Toiletries"
            ELECTRONICS -> "Electronics"
            HEALTH -> "Health"
            GEAR -> "Gear"
            OTHER -> "Other"
        }
}

/** Per-traveler progress, for the meters on the packing screen. */
data class PackingProgress(
    val travelerId: String?,
    val name: String,
    val total: Int,
    val packed: Int,
    val essentialOutstanding: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else packed.toFloat() / total
    val complete: Boolean get() = total > 0 && packed == total
}
