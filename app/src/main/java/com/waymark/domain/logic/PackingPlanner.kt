package com.waymark.domain.logic

import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.PackingItem
import com.waymark.domain.model.PackingProgress
import kotlin.math.ceil
import kotlin.math.min

/** Everything the planner needs to know about a trip, and nothing else. */
data class PackingContext(
    val tripId: String,
    val nights: Int,
    val crossesBorder: Boolean,
    val hasFlights: Boolean,
    val longHaul: Boolean,
    val climate: Climate,
    val rainLikely: Boolean,
    val homePlugTypes: Set<String>,
    val destinationPlugTypes: Set<String>,
    val swimming: Boolean = false,
    val walking: Boolean = false,
    val formalDinner: Boolean = false,
) {
    enum class Climate { COLD, MILD, WARM, TROPICAL }

    /** Past a week, most travelers do laundry rather than pack a second week. */
    val laundryLikely: Boolean get() = nights > 7
}

/**
 * A first draft of a packing list, derived from the itinerary rather than from
 * a generic template: counts scale with the number of nights, an adaptor is
 * suggested only when the destination's sockets differ from home, and a
 * swimsuit appears only when something on the trip involves water.
 *
 * It is a draft in the literal sense — every line can be deleted, and nothing
 * is added twice.
 */
object PackingPlanner {

    fun suggest(context: PackingContext, travelerId: String?): List<PackingItem> {
        val items = mutableListOf<Draft>()

        // Documents
        if (context.crossesBorder) {
            items += Draft("Passport", PackingCategory.DOCUMENTS, essential = true)
        }
        if (context.hasFlights) {
            items += Draft(
                "Boarding passes saved offline",
                PackingCategory.DOCUMENTS,
                essential = true,
                note = "Waymark keeps them; check they are here before you leave for the airport.",
            )
        }
        if (context.crossesBorder) {
            items += Draft("Travel insurance details", PackingCategory.DOCUMENTS)
            items += Draft("A card that works abroad", PackingCategory.DOCUMENTS, essential = true)
        }

        // Clothing, scaled to the stay
        val laundry = context.laundryLikely
        val days = context.nights.coerceAtLeast(1)
        items += Draft(
            "Underwear",
            PackingCategory.CLOTHING,
            quantity = if (laundry) min(days + 2, 9) else days + 2,
        )
        items += Draft(
            "Socks",
            PackingCategory.CLOTHING,
            quantity = if (laundry) min(days + 1, 8) else days + 1,
        )
        items += Draft(
            "Tops",
            PackingCategory.CLOTHING,
            // Capped at a week's worth: the count must plateau, never fall.
            // A fortnight suggesting fewer tops than a week reads as a bug
            // whatever the laundry reasoning behind it.
            quantity = if (laundry) min(days, 7) else days,
        )
        items += Draft(
            "Trousers or skirts",
            PackingCategory.CLOTHING,
            quantity = ceil(days / 3.0).toInt().coerceIn(1, 4),
        )

        when (context.climate) {
            PackingContext.Climate.COLD -> {
                items += Draft("Warm layer", PackingCategory.CLOTHING, quantity = 2, essential = true)
                items += Draft("Hat and gloves", PackingCategory.CLOTHING)
                items += Draft("Windproof coat", PackingCategory.CLOTHING, essential = true)
            }

            PackingContext.Climate.MILD -> {
                items += Draft("A layer for the evening", PackingCategory.CLOTHING)
            }

            PackingContext.Climate.WARM -> {
                items += Draft("Sun hat", PackingCategory.CLOTHING)
                items += Draft("Sun cream", PackingCategory.HEALTH, essential = true)
            }

            PackingContext.Climate.TROPICAL -> {
                items += Draft("Light long sleeves", PackingCategory.CLOTHING, note = "Sun and air conditioning both.")
                items += Draft("Sun cream", PackingCategory.HEALTH, essential = true)
                items += Draft("Insect repellent", PackingCategory.HEALTH)
            }
        }

        if (context.rainLikely) {
            items += Draft("Rain shell or small umbrella", PackingCategory.CLOTHING)
        }
        if (context.formalDinner) {
            items += Draft("One outfit that behaves in a restaurant", PackingCategory.CLOTHING)
        }
        if (context.swimming) {
            items += Draft("Swimsuit", PackingCategory.GEAR)
            items += Draft("Quick-dry towel", PackingCategory.GEAR)
        }
        if (context.walking) {
            items += Draft("Shoes you can walk all day in", PackingCategory.CLOTHING, essential = true)
            items += Draft("Blister plasters", PackingCategory.HEALTH)
        }

        // Electronics — an adaptor only where the sockets actually differ.
        val foreignSockets = context.destinationPlugTypes - context.homePlugTypes
        if (foreignSockets.isNotEmpty()) {
            items += Draft(
                "Travel adaptor (${foreignSockets.sorted().joinToString(", ")})",
                PackingCategory.ELECTRONICS,
                essential = true,
                note = "Home sockets are ${context.homePlugTypes.sorted().joinToString(", ")}.",
            )
        }
        items += Draft("Phone charger", PackingCategory.ELECTRONICS, essential = true)
        if (context.longHaul) {
            items += Draft("Power bank", PackingCategory.ELECTRONICS, note = "In the cabin, not the hold.")
            items += Draft("Earplugs or headphones", PackingCategory.GEAR)
        }

        // Toiletries and health
        items += Draft("Toothbrush and paste", PackingCategory.TOILETRIES, essential = true)
        items += Draft("Any prescription medicine", PackingCategory.HEALTH, essential = true)
        if (context.hasFlights) {
            items += Draft(
                "Liquids under 100 ml in one bag",
                PackingCategory.TOILETRIES,
                note = "Security will find the shampoo you forgot about.",
            )
        }

        return items.map { it.toItem(context.tripId, travelerId) }
    }

    /** Suggestions the traveler does not already have, matched on title. */
    fun newSuggestions(
        suggestions: List<PackingItem>,
        existing: List<PackingItem>,
    ): List<PackingItem> {
        val held = existing.map { it.title.trim().lowercase() }.toSet()
        return suggestions.filterNot { it.title.trim().lowercase() in held }
    }

    fun progress(
        items: List<PackingItem>,
        travelerId: String?,
        name: String,
    ): PackingProgress {
        val mine = items.filter { it.travelerId == travelerId }
        return PackingProgress(
            travelerId = travelerId,
            name = name,
            total = mine.size,
            packed = mine.count { it.packed },
            essentialOutstanding = mine.count { it.essential && !it.packed },
        )
    }

    private data class Draft(
        val title: String,
        val category: PackingCategory,
        val quantity: Int = 1,
        val essential: Boolean = false,
        val note: String? = null,
    ) {
        fun toItem(tripId: String, travelerId: String?): PackingItem = PackingItem(
            id = "suggested-${travelerId.orEmpty()}-${title.hashCode()}",
            tripId = tripId,
            travelerId = travelerId,
            title = title,
            category = category,
            quantity = quantity,
            essential = essential,
            note = note,
            source = PackingItem.SOURCE_SUGGESTED,
        )
    }
}
