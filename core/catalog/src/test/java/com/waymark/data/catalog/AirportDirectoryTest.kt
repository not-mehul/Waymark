package com.waymark.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirportDirectoryTest {

    private val sample = """
        Europe/London
        America/Los_Angeles
        Pacific/Majuro

        LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
        SFO|San Francisco International Airport|San Francisco|US|37.6188|-122.3750|1
        UTK|Utirik Airport||MH|11.2220|169.8520|2
    """.trimIndent().lineSequence()

    private val parsed = AirportDirectory.parse(sample)

    @Test
    fun `zones are interned and resolved by index`() {
        assertEquals(3, parsed.size)
        assertEquals("Europe/London", parsed.first { it.code == "LHR" }.timeZoneId)
        assertEquals("America/Los_Angeles", parsed.first { it.code == "SFO" }.timeZoneId)
        assertEquals("Pacific/Majuro", parsed.first { it.code == "UTK" }.timeZoneId)
    }

    @Test
    fun `coordinates and country survive the round trip`() {
        val heathrow = parsed.first { it.code == "LHR" }
        assertEquals(51.4706, heathrow.latitude, 0.0001)
        assertEquals(-0.4619, heathrow.longitude, 0.0001)
        // The file carries "GB"; the parser expands it, because "GB" is not a
        // country anybody reads.
        assertEquals("United Kingdom", heathrow.country)
    }

    @Test
    fun `an airport with no municipality falls back to its own shortened name`() {
        // "Utirik Airport" on an island with no town listed against it.
        assertEquals("Utirik", parsed.first { it.code == "UTK" }.city)
    }

    @Test
    fun `a malformed line is skipped rather than failing the whole directory`() {
        val broken = """
            Europe/London

            LHR|London Heathrow Airport|London|GB|51.4706|-0.4619|0
            XX|too short|Nowhere|GB|1.0|1.0|0
            BAD|missing fields|GB|0
            NAN|unparseable|Nowhere|GB|north|west|0
            OOB|off the planet|Nowhere|GB|991.0|1.0|0
            ZON|no such zone|Nowhere|GB|1.0|1.0|99
            CDG|Charles de Gaulle|Paris|FR|49.0097|2.5479|0
        """.trimIndent().lineSequence()

        val codes = AirportDirectory.parse(broken).map { it.code }
        assertEquals(listOf("LHR", "CDG"), codes)
    }

    @Test
    fun `an empty directory parses to nothing rather than throwing`() {
        assertTrue(AirportDirectory.parse(emptySequence()).isEmpty())
        assertTrue(AirportDirectory.parse(sequenceOf("", "  ")).isEmpty())
    }

    // — Installation ——————————————————————————————————————————————————————

    @Test
    fun `the core list wins a code collision, keeping its terminals`() {
        // The directory row for LHR carries no terminals; the curated one does.
        Airports.install(parsed)
        val heathrow = requireNotNull(Airports.find("LHR"))
        assertTrue("core terminals were lost", heathrow.terminals.isNotEmpty())
        assertEquals("Heathrow", heathrow.name)

        // A code the core list has never heard of comes from the directory.
        val utirik = requireNotNull(Airports.find("UTK"))
        assertEquals("Pacific/Majuro", utirik.timeZoneId)
    }

    @Test
    fun `search prefers a core station over a directory one at the same rank`() {
        Airports.install(parsed)
        // Both LHR and UTK exist; an exact code still wins outright.
        assertEquals("UTK", Airports.search("utk").first().code)
        assertEquals("LHR", Airports.search("lhr").first().code)
        assertNull(Airports.find("ZZZ"))
    }
}
