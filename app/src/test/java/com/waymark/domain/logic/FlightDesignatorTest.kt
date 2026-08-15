package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightDesignatorTest {

    @Test
    fun `parses the way airlines print it`() {
        assertEquals("BA286", FlightDesignator.parse("BA286")?.normalised)
        assertEquals("BA286", FlightDesignator.parse("ba 286")?.normalised)
        assertEquals("BA286", FlightDesignator.parse(" BA-286 ")?.normalised)
        assertEquals("BA286", FlightDesignator.parse("BA0286")?.normalised)
    }

    @Test
    fun `handles carrier codes with digits and three-letter ICAO codes`() {
        assertEquals("3U8888", FlightDesignator.parse("3U8888")?.normalised)
        assertEquals("U21234", FlightDesignator.parse("U2 1234")?.normalised)
        assertEquals("BAW286", FlightDesignator.parse("BAW286")?.normalised)
    }

    @Test
    fun `keeps an operational suffix`() {
        val parsed = FlightDesignator.parse("LH1234A")
        assertEquals("LH", parsed?.carrier)
        assertEquals(1234, parsed?.number)
        assertEquals("A", parsed?.suffix)
    }

    @Test
    fun `pads the printed form the way a boarding pass does`() {
        assertEquals("BA 286", FlightDesignator.parse("BA286")?.printed)
        assertEquals("AA 001", FlightDesignator.parse("AA1")?.printed)
    }

    @Test
    fun `rejects what cannot be a flight number`() {
        assertNull(FlightDesignator.parse(""))
        assertNull(FlightDesignator.parse("BA"))
        assertNull(FlightDesignator.parse("286"))
        assertNull(FlightDesignator.parse("BA12345"))
        assertNull(FlightDesignator.parse("BA0"))
        assertNull(FlightDesignator.parse("HEATHROW"))
    }

    @Test
    fun `completeness gate matches parsing`() {
        assertTrue(FlightDesignator.looksComplete("BA286"))
        assertTrue(!FlightDesignator.looksComplete("BA"))
    }
}
