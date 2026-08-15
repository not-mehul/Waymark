package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BcbpTest {

    private val date = LocalDate.of(2026, 5, 14)

    @Test
    fun `the mandatory section is exactly sixty characters`() {
        val payload = Bcbp.build(
            passengerName = "Mara Ellison",
            recordLocator = "K7QH2P",
            origin = "SFO",
            destination = "LHR",
            carrier = "BA",
            flightNumber = "286",
            date = date,
            seat = "21A",
            sequence = "042",
        )
        assertEquals(60, payload.length)
        assertTrue(payload.startsWith("M1"))
    }

    @Test
    fun `builds and parses back the same facts`() {
        val payload = Bcbp.build(
            passengerName = "Mara Ellison",
            recordLocator = "K7QH2P",
            origin = "SFO",
            destination = "LHR",
            carrier = "BA",
            flightNumber = "286",
            date = date,
            seat = "21A",
            sequence = "042",
        )
        val parsed = requireNotNull(Bcbp.parse(payload))

        assertEquals("ELLISON/MARA", parsed.passengerName)
        assertEquals("K7QH2P", parsed.recordLocator)
        assertEquals("SFO", parsed.origin)
        assertEquals("LHR", parsed.destination)
        assertEquals("BA", parsed.carrier)
        assertEquals("286", parsed.flightNumber)
        assertEquals(date.dayOfYear, parsed.julianDate)
        assertEquals("21A", parsed.seat)
        assertEquals("42", parsed.sequence)
        assertEquals(date, parsed.flightDate(2026))
    }

    @Test
    fun `a name already in surname-slash form is kept`() {
        val payload = Bcbp.build(
            passengerName = "BECK/JULIAN MR",
            recordLocator = "XQ4M2T",
            origin = "LHR",
            destination = "CDG",
            carrier = "AF",
            flightNumber = "1681",
            date = date,
        )
        assertEquals("BECK/JULIAN MR", requireNotNull(Bcbp.parse(payload)).passengerName)
    }

    @Test
    fun `conditional items ride along as an opaque tail`() {
        val payload = Bcbp.build(
            passengerName = "Mara Ellison",
            recordLocator = "K7QH2P",
            origin = "SFO",
            destination = "LHR",
            carrier = "BA",
            flightNumber = "286",
            date = date,
        ) + ">5180  W6A0028BBA 0000"
        val parsed = requireNotNull(Bcbp.parse(payload))
        assertTrue(parsed.tail.startsWith(">"))
        assertEquals("SFO", parsed.origin)
    }

    @Test
    fun `rejects anything that is not a pass`() {
        assertNull(Bcbp.parse(""))
        assertNull(Bcbp.parse("not a boarding pass"))
        assertNull(Bcbp.parse("X1" + " ".repeat(58)))
    }

    @Test
    fun `boarding opens forty minutes before departure`() {
        val departure = 1_800_000_000_000L
        assertEquals(departure - 40 * 60_000L, Bcbp.defaultBoardingTime(departure))
    }
}
