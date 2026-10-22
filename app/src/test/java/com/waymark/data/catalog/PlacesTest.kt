package com.waymark.data.catalog

import com.waymark.domain.logic.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class PlacesTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadGazetteer() {
            if (!Cities.loaded) {
                Cities.install(CityDirectory.parse(assetLines(CityDirectory.ASSET_NAME).asSequence()))
            }
        }
    }

    private val newYork = LatLon(40.71, -74.0)
    private val wichita = LatLon(37.69, -97.34)

    @Test
    fun `the gazetteer is the whole world, with a zone on every city`() {
        val parsed = CityDirectory.parse(assetLines(CityDirectory.ASSET_NAME).asSequence())
        assertTrue(parsed.size > 30_000)
        assertTrue(parsed.all { it.timeZoneId.contains('/') || it.timeZoneId == "UTC" })
        // Largest first, so an ambiguous name means the city most people mean.
        assertTrue(parsed.first().population > parsed.last().population)
    }

    @Test
    fun `a city resolves to its own clock, not the trip's`() {
        val paris = Places.resolve("Paris", near = null, fallbackZoneId = "America/Los_Angeles")
        assertEquals(Places.Match.CITY, paris.match)
        assertEquals("Europe/Paris", paris.place.timeZoneId)
        assertEquals("France", paris.place.country)

        val kyoto = Places.resolve("Kyoto", near = null, fallbackZoneId = "UTC")
        assertEquals("Asia/Tokyo", kyoto.place.timeZoneId)
    }

    @Test
    fun `a city named in an address is found, and the address is kept as the name`() {
        val stay = Places.resolve("Bloomsbury, London", near = null, fallbackZoneId = "UTC")
        assertEquals("London", stay.place.city)
        assertEquals("Bloomsbury, London", stay.place.name)
        assertEquals("Europe/London", stay.place.timeZoneId)
    }

    @Test
    fun `a shared name goes to the city nearest the trip, or the largest without one`() {
        val unplaced = Places.resolve("Manhattan", near = null, fallbackZoneId = "UTC")
        assertEquals("America/New_York", unplaced.place.timeZoneId)

        val inNewYork = Places.resolve("Manhattan", near = newYork, fallbackZoneId = "UTC")
        assertEquals("America/New_York", inNewYork.place.timeZoneId)

        val inKansas = Places.resolve("Manhattan", near = wichita, fallbackZoneId = "UTC")
        assertEquals("America/Chicago", inKansas.place.timeZoneId)
    }

    @Test
    fun `english names and spelling variants find the city`() {
        assertEquals(
            "BE",
            Places.resolve("Bruges", null, "UTC").place.country.let { name ->
                Countries.forName(name)?.code
            }
        )
        assertEquals("Europe/Moscow", Places.resolve("st. petersburg", null, "UTC").place.timeZoneId)
        assertEquals("Europe/Zurich", Places.resolve("ZURICH", null, "UTC").place.timeZoneId)
    }

    @Test
    fun `a station code is a station`() {
        val heathrow = Places.resolve("lhr", near = null, fallbackZoneId = "UTC")
        assertEquals(Places.Match.STATION, heathrow.match)
        assertEquals("LHR", heathrow.place.code)
    }

    @Test
    fun `nothing is matched by a fragment, and unmatched text keeps the caller's zone`() {
        val fragment = Places.resolve("Manhatt", near = null, fallbackZoneId = "Europe/Rome")
        assertEquals(Places.Match.TYPED, fragment.match)
        assertEquals("Europe/Rome", fragment.place.timeZoneId)
        assertFalse(fragment.place.hasCoordinates)

        val bakery = Places.resolve("the bakery on the corner", null, "Asia/Tokyo")
        assertEquals("the bakery on the corner", bakery.place.name)
        assertEquals("Asia/Tokyo", bakery.place.timeZoneId)
    }

    @Test
    fun `the typed reading is always offered last`() {
        val options = Places.candidates("Paris", near = null, fallbackZoneId = "UTC")
        assertTrue(options.size >= 3)
        assertEquals(Places.Match.TYPED, options.last().match)
        assertEquals("FR", Countries.forName(options.first().place.country)?.code)
    }
}

/** Bundled assets are not on the JVM classpath; read them from the source tree. */
internal fun assetLines(name: String): List<String> =
    java.io.File("src/main/assets/$name").takeIf { it.exists() }?.readLines()
        ?: java.io.File("app/src/main/assets/$name").readLines()
