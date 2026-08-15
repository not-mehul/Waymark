package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.data.catalog.TripFacts
import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class PackingPlannerTest {

    private fun context(
        nights: Int = 7,
        climate: PackingContext.Climate = PackingContext.Climate.MILD,
        home: Set<String> = setOf("Type A", "Type B"),
        destination: Set<String> = setOf("Type G"),
        longHaul: Boolean = true,
        swimming: Boolean = false,
        walking: Boolean = false,
    ) = PackingContext(
        tripId = "trip",
        nights = nights,
        crossesBorder = true,
        hasFlights = true,
        longHaul = longHaul,
        climate = climate,
        rainLikely = false,
        homePlugTypes = home,
        destinationPlugTypes = destination,
        swimming = swimming,
        walking = walking,
    )

    private fun titles(context: PackingContext) =
        PackingPlanner.suggest(context, "trav-mara").map { it.title }

    @Test
    fun `counts scale with the number of nights`() {
        val short = PackingPlanner.suggest(context(nights = 3), null)
            .first { it.title == "Underwear" }
        val long = PackingPlanner.suggest(context(nights = 6), null)
            .first { it.title == "Underwear" }
        assertTrue(long.quantity > short.quantity)
    }

    @Test
    fun `past a week the list assumes laundry rather than doubling`() {
        val fortnight = PackingPlanner.suggest(context(nights = 14), null)
            .first { it.title == "Tops" }.quantity
        assertTrue("a fortnight should not need 14 tops", fortnight < 10)
    }

    /**
     * Counts may plateau once laundry is assumed, but they must never fall:
     * a longer trip suggesting fewer socks than a shorter one reads as a bug
     * however sound the reasoning behind it.
     */
    @Test
    fun `counts never decrease as the trip lengthens`() {
        val tracked = listOf("Underwear", "Socks", "Tops", "Trousers or skirts")
        tracked.forEach { title ->
            val counts = (1..21).map { nights ->
                PackingPlanner.suggest(context(nights = nights), null)
                    .first { it.title == title }.quantity
            }
            counts.zipWithNext().forEachIndexed { index, (shorter, longer) ->
                assertTrue(
                    "$title fell from $shorter to $longer between ${index + 1} and ${index + 2} nights",
                    longer >= shorter,
                )
            }
        }
    }

    @Test
    fun `an adaptor appears only where the sockets differ`() {
        val abroad = titles(context(home = setOf("Type A"), destination = setOf("Type G")))
        assertTrue(abroad.any { it.startsWith("Travel adaptor") && it.contains("Type G") })

        val sameSockets = titles(context(home = setOf("Type G"), destination = setOf("Type G")))
        assertTrue(sameSockets.none { it.startsWith("Travel adaptor") })
    }

    @Test
    fun `climate decides the outer layers`() {
        val cold = titles(context(climate = PackingContext.Climate.COLD))
        assertTrue(cold.any { it.contains("Windproof") })
        assertTrue(cold.none { it == "Sun cream" })

        val tropical = titles(context(climate = PackingContext.Climate.TROPICAL))
        assertTrue(tropical.contains("Sun cream"))
        assertTrue(tropical.contains("Insect repellent"))
        assertTrue(tropical.none { it.contains("Windproof") })
    }

    @Test
    fun `a swimsuit appears only when something involves water`() {
        assertTrue(titles(context(swimming = false)).none { it == "Swimsuit" })
        assertTrue(titles(context(swimming = true)).contains("Swimsuit"))
    }

    @Test
    fun `a short domestic hop skips the passport and the power bank`() {
        val domestic = PackingContext(
            tripId = "trip",
            nights = 2,
            crossesBorder = false,
            hasFlights = true,
            longHaul = false,
            climate = PackingContext.Climate.MILD,
            rainLikely = false,
            homePlugTypes = setOf("Type A"),
            destinationPlugTypes = setOf("Type A"),
        )
        val list = titles(domestic)
        assertTrue(list.none { it == "Passport" })
        assertTrue(list.none { it == "Power bank" })
        assertTrue(list.contains("Phone charger"))
    }

    @Test
    fun `essentials are marked and land in sensible categories`() {
        val items = PackingPlanner.suggest(context(), "trav-mara")
        assertTrue(items.any { it.essential })
        assertEquals(
            PackingCategory.DOCUMENTS,
            items.first { it.title == "Passport" }.category,
        )
        assertEquals(
            PackingCategory.ELECTRONICS,
            items.first { it.title == "Phone charger" }.category,
        )
    }

    @Test
    fun `applying the draft twice adds nothing the second time`() {
        val first = PackingPlanner.suggest(context(), "trav-mara")
        val second = PackingPlanner.newSuggestions(
            suggestions = PackingPlanner.suggest(context(), "trav-mara"),
            existing = first,
        )
        assertTrue(second.isEmpty())
    }

    @Test
    fun `progress counts only the list asked for`() {
        val mine = PackingPlanner.suggest(context(), "trav-mara")
        val shared = PackingPlanner.suggest(context(), null)
        val progress = PackingPlanner.progress(mine + shared, "trav-mara", "Mara")

        assertEquals(mine.size, progress.total)
        assertEquals(0, progress.packed)
        assertTrue(progress.essentialOutstanding > 0)
        assertTrue(!progress.complete)
    }

    @Test
    fun `the sample trip yields a context that reflects the itinerary`() {
        val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
        val dossier = TripDossier(
            trip = bundle.trip,
            party = TripParty(bundle.trip.id, bundle.travelers),
            segments = bundle.segments,
        )
        val context = TripFacts.packingContext(dossier, bundle.ideas)

        assertTrue(context.crossesBorder)
        assertTrue(context.hasFlights)
        assertTrue("SFO–LHR is long haul", context.longHaul)
        assertTrue(context.nights > 0)
        // Home is the United States, the destinations are not.
        assertTrue(context.destinationPlugTypes.isNotEmpty())
        assertTrue(context.destinationPlugTypes != context.homePlugTypes)
    }

    @Test
    fun `climate follows latitude and season, and flips below the equator`() {
        // Reykjavík in January, London in July, Singapore whenever.
        assertEquals(
            PackingContext.Climate.COLD,
            TripFacts.climateFor(64.14, Month.JANUARY),
        )
        assertEquals(
            PackingContext.Climate.WARM,
            TripFacts.climateFor(51.5, Month.JULY),
        )
        assertEquals(
            PackingContext.Climate.TROPICAL,
            TripFacts.climateFor(1.35, Month.JANUARY),
        )
        // Sydney in July is winter, but a southern winter at 34° is mild —
        // eight to seventeen degrees, not a coat-and-gloves climate.
        assertEquals(
            PackingContext.Climate.MILD,
            TripFacts.climateFor(-33.87, Month.JULY),
        )
        assertEquals(
            PackingContext.Climate.WARM,
            TripFacts.climateFor(-33.87, Month.JANUARY),
        )
    }
}
