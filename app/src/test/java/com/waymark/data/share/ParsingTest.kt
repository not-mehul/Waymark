package com.waymark.data.share

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    private val today = LocalDate.of(2026, 10, 22)

    @Test
    fun `a typical airline confirmation is read into complete flights`() {
        val email = """
            Your booking is confirmed. Booking reference: K7QH2P

            Outbound — Thursday 14 May 2026
            BA 286  San Francisco (SFO) to London Heathrow (LHR)
            Departs 16:20   Arrives 10:55 (+1)
            Terminal 5, seat 21A

            Return — 21 May 2026
            AF0084  Paris Charles de Gaulle (CDG) → San Francisco (SFO)
            Depart 13:25, arrive 15:40
        """.trimIndent()

        val reading = BookingText.read(email, today)
        assertEquals("K7QH2P", reading.reference)
        assertEquals(2, reading.flights.size)

        val out = reading.flights[0]
        assertEquals("BA286", out.designator)
        assertEquals(LocalDate.of(2026, 5, 14), out.date)
        assertEquals("SFO", out.origin)
        assertEquals("LHR", out.destination)
        assertEquals(LocalTime.of(16, 20), out.departs)
        assertEquals(LocalTime.of(10, 55), out.arrives)
        assertTrue(out.complete)

        val back = reading.flights[1]
        assertEquals("AF84", back.designator)
        assertEquals(LocalDate.of(2026, 5, 21), back.date)
        assertEquals("CDG", back.origin)
        assertEquals(LocalTime.of(15, 40), back.arrives)
    }

    @Test
    fun `a complete flight becomes a booking in the airports' own zones`() {
        val found = BookingText.read("BA 286 SFO to LHR on 14 May 2026, departs 16:20, arrives 10:55", today)
            .flights.single()
        val flight = BookingText.toSegment(found, tripId = "trip-1", id = "seg-1", reference = "K7QH2P")!!
        assertEquals("BA", flight.carrierCode)
        assertEquals("286", flight.flightNumber)
        assertEquals("America/Los_Angeles", flight.startZoneId)
        assertEquals("Europe/London", flight.endZoneId)
        // Overnight: lands the next morning, ten and a half hours later.
        assertEquals(LocalDate.of(2026, 5, 15), flight.end.toLocalDate())
        assertEquals(10 * 60 + 35L, flight.duration.toMinutes())
        assertEquals("K7QH2P", flight.confirmationCode)
    }

    @Test
    fun `an incomplete flight does not become a booking`() {
        val found = BookingText.read("BA 286 SFO to LHR on 14 May 2026", today).flights.single()
        assertNull(BookingText.toSegment(found, "trip-1", "seg-1", null))
    }

    @Test
    fun `twelve-hour times and an American date are understood`() {
        val text = "Flight UA 930 on May 3 from SFO to LHR, departs 3:25 PM and arrives 9:50 AM"
        val flight = BookingText.read(text, today).flights.single()
        assertEquals(LocalDate.of(2027, 5, 3), flight.date)
        assertEquals(LocalTime.of(15, 25), flight.departs)
        assertEquals(LocalTime.of(9, 50), flight.arrives)
    }

    @Test
    fun `things that look like flight numbers but are not are left alone`() {
        val text = "Order ZZ 1234 shipped. Room 215, floor 2. Call 0800 123 456 before 18.05.2026."
        assertTrue(BookingText.read(text, today).flights.isEmpty())
    }

    @Test
    fun `a boarding pass barcode is read leg by leg`() {
        // The worked example from IATA Resolution 792, with one leg.
        val pass = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100"
        val leg = Bcbp.parse(pass, today).single()
        assertEquals("AC834", leg.designator)
        assertEquals("YUL", leg.origin)
        assertEquals("FRA", leg.destination)
        assertEquals("1A", leg.seat)
        assertEquals("Business", leg.cabin)
        assertEquals("ABC123", leg.reference)
        assertEquals("Luc Desmarais", leg.passenger)
        // Day 326 of 2026 is 22 November — a month from today.
        assertEquals(LocalDate.of(2026, 11, 22), leg.date)
    }

    @Test
    fun `two legs on one pass are both read, past the variable field`() {
        val first = "ABC123 SFOLHRBA 0286 134Y021A0012 100"
        val second = "ABC123 LHRCDGBA 0304 135Y003C0040 100"
        val pass = "M2ELLISON/MARA        E$first$second"
        val legs = Bcbp.parse(pass, today)
        assertEquals(listOf("BA286", "BA304"), legs.map { it.designator })
        assertEquals(listOf("LHR", "CDG"), legs.map { it.destination })
        assertEquals("21A", legs[0].seat)
    }

    @Test
    fun `text that is not a boarding pass is not one`() {
        assertTrue(Bcbp.parse("https://example.com", today).isEmpty())
        assertTrue(Bcbp.parse("", today).isEmpty())
        assertNull(Bcbp.parse("M1X", today).firstOrNull())
    }
}
