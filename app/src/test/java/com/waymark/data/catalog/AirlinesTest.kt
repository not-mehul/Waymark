package com.waymark.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirlinesTest {

    @Test
    fun `designators resolve to the names travelers know`() {
        assertEquals("British Airways", Airlines.name("BA"))
        assertEquals("easyJet", Airlines.name("u2"))
        assertEquals("IndiGo", Airlines.name(" 6E "))
        assertEquals("LATAM Airlines", Airlines.name("LA"))
    }

    @Test
    fun `an unknown designator prints as itself`() {
        assertEquals("ZZ", Airlines.name("zz"))
        assertFalse(Airlines.knows("ZZ"))
    }

    @Test
    fun `the table is broad and every key is a well-formed designator`() {
        assertTrue(Airlines.size > 200)
        listOf("AA", "AF", "CX", "EK", "LH", "NH", "QF", "SQ", "TK", "UA", "WN").forEach {
            assertTrue(it, Airlines.knows(it))
        }
    }
}
