package com.waymark.data.catalog

import com.waymark.data.catalog.DestinationGuide.toIdea
import com.waymark.domain.model.IdeaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DestinationGuideTest {

    @Test
    fun `every guide city has practical notes to sit beside it`() {
        DestinationGuide.cities().forEach { city ->
            assertNotNull("$city has no destination notes", DestinationInsights.forCity(city))
        }
    }

    @Test
    fun `every city offers both something to see and something to eat`() {
        DestinationGuide.cities().forEach { city ->
            val entries = DestinationGuide.forCity(city)
            assertTrue("$city has too few entries", entries.size >= 5)
            assertTrue(
                "$city has nothing to eat",
                entries.any { it.kind == IdeaKind.EAT || it.kind == IdeaKind.EAT },
            )
            assertTrue(
                "$city has nothing to see or do",
                entries.any { it.kind == IdeaKind.SEE || it.kind == IdeaKind.SEE },
            )
        }
    }

    @Test
    fun `entries are well formed`() {
        DestinationGuide.all().forEach { entry ->
            assertTrue("blank title", entry.title.isNotBlank())
            assertTrue("${entry.title} has no note", entry.note.length > 20)
            entry.typicalMinutes?.let {
                assertTrue("${entry.title} has an odd duration", it in 15..300)
            }
            if (entry.latitude != null || entry.longitude != null) {
                assertNotNull("${entry.title} has half a coordinate", entry.latitude)
                assertNotNull("${entry.title} has half a coordinate", entry.longitude)
                assertTrue(entry.latitude!! in -90.0..90.0)
                assertTrue(entry.longitude!! in -180.0..180.0)
            }
        }
    }

    @Test
    fun `titles are unique within a city`() {
        DestinationGuide.cities().forEach { city ->
            val titles = DestinationGuide.forCity(city).map { it.title }
            assertEquals("duplicate entry in $city", titles.size, titles.distinct().size)
        }
    }

    @Test
    fun `a placed entry becomes an idea with usable coordinates and the city clock`() {
        val entry = DestinationGuide.forCity("London").first { it.title == "Borough Market" }
        val idea = with(DestinationGuide) { entry.toIdea("i1", "trip", "London") }

        assertEquals(IdeaKind.EAT, idea.kind)
        assertTrue(idea.hasLocation)
        assertEquals(ZoneId.of("Europe/London"), ZoneId.of(idea.place!!.timeZoneId))
        assertEquals("Bundled guide", idea.source)
    }

    @Test
    fun `a dish becomes a placeless idea rather than a fake location`() {
        val entry = DestinationGuide.forCity("Singapore")
            .first { it.title == "Hainanese chicken rice" }
        val idea = with(DestinationGuide) { entry.toIdea("i2", "trip", "Singapore") }

        assertTrue(idea.isPlaceless)
        assertTrue(!idea.hasLocation)
    }

    @Test
    fun `guide coordinates land in the right part of the world`() {
        // A transposed latitude and longitude is the classic data-entry error;
        // checking each city's entries against its airport catches it.
        var checked = 0
        DestinationGuide.cities().forEach { city ->
            val airport = Airports.all().firstOrNull { it.city == city } ?: return@forEach
            DestinationGuide.forCity(city)
                .filter { it.latitude != null && it.longitude != null }
                .forEach { entry ->
                    val distance = com.waymark.domain.logic.Geo.distanceKm(
                        com.waymark.domain.logic.LatLon(airport.latitude, airport.longitude),
                        com.waymark.domain.logic.LatLon(entry.latitude!!, entry.longitude!!),
                    )
                    assertTrue(
                        "${entry.title} is ${distance.toInt()} km from $city's airport",
                        distance < 120.0,
                    )
                    checked++
                }
        }
        // Guard against the check passing because nothing was compared.
        assertTrue("no guide coordinates were checked", checked > 20)
    }
}
