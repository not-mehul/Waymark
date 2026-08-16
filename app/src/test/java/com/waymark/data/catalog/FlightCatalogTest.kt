package com.waymark.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FlightCatalogTest {

    private val date = LocalDate.of(2026, 5, 14)

    @Test
    fun `a flight number produces a fully populated plan`() {
        val plan = requireNotNull(FlightCatalog.lookup("BA286", date))
        assertEquals("BA286", plan.designator)
        assertEquals("SFO", plan.origin.code)
        assertEquals("LHR", plan.destination.code)
        assertEquals("British Airways", plan.carrierName)
        assertNotNull(plan.departureTerminal)
        assertTrue(plan.aircraft.isNotBlank())
        assertTrue("block was ${plan.blockMinutes}", plan.blockMinutes in 540..660)
    }

    @Test
    fun `each end keeps its own local clock`() {
        val plan = requireNotNull(FlightCatalog.lookup("BA286", date))
        assertEquals(ZoneId.of("America/Los_Angeles"), plan.departure.zone)
        assertEquals(ZoneId.of("Europe/London"), plan.arrival.zone)
        assertEquals(16, plan.departure.hour)
        assertEquals(10, plan.arrival.hour)
        assertEquals(date.plusDays(1), plan.arrival.toLocalDate())
    }

    @Test
    fun `a westbound crossing of the date line can arrive the previous day`() {
        val plan = requireNotNull(FlightCatalog.lookup("CX880", date))
        assertEquals(date.minusDays(1), plan.arrival.toLocalDate())
        assertTrue("block was ${plan.blockMinutes}", plan.blockMinutes in 600..840)
    }

    @Test
    fun `lookup is forgiving about how the number is typed`() {
        assertNotNull(FlightCatalog.lookup("ba 286", date))
        assertNotNull(FlightCatalog.lookup("BA-286", date))
        assertNull(FlightCatalog.lookup("ZZ9999", date))
        assertNull(FlightCatalog.lookup("nonsense", date))
    }

    @Test
    fun `every scheduled flight resolves to a real station and a positive block`() {
        FlightCatalog.departuresFrom("SFO").forEach { scheduled ->
            val plan = FlightCatalog.resolve(scheduled, date)
            assertNotNull("${scheduled.designator} origin", Airports.find(scheduled.originCode))
            assertNotNull("${scheduled.designator} dest", Airports.find(scheduled.destinationCode))
            assertTrue("${scheduled.designator} block", plan.blockMinutes > 0)
        }
    }

    @Test
    fun `the whole catalog is internally consistent`() {
        Airports.all().forEach { airport ->
            assertTrue(airport.code.length == 3)
            assertTrue(airport.latitude in -90.0..90.0)
            assertTrue(airport.longitude in -180.0..180.0)
            // A time zone the platform does not know would silently move flights.
            ZoneId.of(airport.timeZoneId)
        }
        assertEquals(Airports.all().size, Airports.all().map { it.code }.distinct().size)
    }

    @Test
    fun `station search finds by code, city and name`() {
        assertEquals("LHR", Airports.search("lhr").first().code)
        assertEquals("LHR", Airports.search("heathrow").first().code)
        assertTrue(Airports.search("tokyo").map { it.code }.containsAll(listOf("NRT", "HND")))
        assertTrue(Airports.search("").isEmpty())
    }

    @Test
    fun `country notes resolve from a station's own country field`() {
        val heathrow = requireNotNull(Airports.find("LHR"))
        val country = requireNotNull(Countries.of(heathrow.toPlace()))
        assertEquals("GB", country.code)
        assertEquals("GBP", country.currencyCode)
        assertTrue(country.drivesOnLeft)
        assertNull(Countries.forName("Atlantis"))
    }
}
