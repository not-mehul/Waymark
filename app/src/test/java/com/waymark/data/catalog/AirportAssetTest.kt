package com.waymark.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

/**
 * The generated asset itself, read straight off disk.
 *
 * A directory this size is only useful if it is *right*, and the failure modes
 * are quiet ones: a transposed coordinate puts an airport in the sea, a bad
 * time-zone name moves a flight to the wrong day and only shows up in the
 * timeline weeks later. These are the checks that catch a bad regeneration
 * before it ships.
 */
class AirportAssetTest {

    private val directory: List<Airports.Airport> by lazy {
        val asset = File("src/main/assets/${AirportDirectory.ASSET_NAME}")
            .takeIf { it.exists() }
            ?: File("app/src/main/assets/${AirportDirectory.ASSET_NAME}")
        AirportDirectory.parse(asset.readLines().asSequence())
    }

    @Test
    fun `the whole world is present`() {
        assertTrue("only ${directory.size} stations", directory.size > 8_000)
        assertEquals(directory.size, directory.map { it.code }.distinct().size)
    }

    @Test
    fun `every time zone is one the platform actually knows`() {
        val zones = directory.map { it.timeZoneId }.distinct()
        assertTrue("only ${zones.size} zones", zones.size > 300)
        zones.forEach { zone ->
            // Throws for an unknown region, which is exactly the failure we want.
            ZoneId.of(zone)
        }
    }

    @Test
    fun `every station has a code, a name, something to call it, and a position`() {
        directory.forEach { airport ->
            assertEquals("bad code ${airport.code}", 3, airport.code.length)
            assertTrue(airport.code.all { it in 'A'..'Z' })
            assertTrue("${airport.code} has no name", airport.name.isNotBlank())
            assertTrue("${airport.code} has no city", airport.city.isNotBlank())
            assertTrue("${airport.code} lat", airport.latitude in -90.0..90.0)
            assertTrue("${airport.code} lon", airport.longitude in -180.0..180.0)
        }
    }

    /**
     * The check that catches a transposed latitude and longitude: a station's
     * zone should agree with its longitude to within a few hours. A row with
     * its coordinates swapped lands on the wrong side of the world and the
     * offset disagrees by half a day.
     */
    @Test
    fun `each station's zone agrees with its longitude`() {
        val instant = java.time.Instant.parse("2026-01-15T12:00:00Z")
        val wrong = directory.filter { airport ->
            val offsetHours = ZoneId.of(airport.timeZoneId).rules
                .getOffset(instant).totalSeconds / 3600.0
            val expected = airport.longitude / 15.0
            var delta = offsetHours - expected
            while (delta > 12) delta -= 24
            while (delta < -12) delta += 24
            kotlin.math.abs(delta) > 4.0
        }
        // A handful of genuine outliers exist — western China on Beijing time,
        // Spain on central European time — so this is a ceiling, not zero.
        assertTrue(
            "${wrong.size} stations disagree with their longitude: " +
                wrong.take(5).joinToString { "${it.code} ${it.timeZoneId}" },
            wrong.size < directory.size / 50,
        )
    }

    @Test
    fun `the stations a traveler is most likely to type are correct`() {
        val expected = mapOf(
            "LHR" to Triple("Europe/London", 51.47, -0.46),
            "JFK" to Triple("America/New_York", 40.64, -73.78),
            "NRT" to Triple("Asia/Tokyo", 35.76, 140.39),
            "SYD" to Triple("Australia/Sydney", -33.95, 151.18),
            "GRU" to Triple("America/Sao_Paulo", -23.44, -46.47),
            "DXB" to Triple("Asia/Dubai", 25.25, 55.36),
            "JNB" to Triple("Africa/Johannesburg", -26.14, 28.25),
            "KTM" to Triple("Asia/Kathmandu", 27.70, 85.36),
        )
        val byCode = directory.associateBy { it.code }
        expected.forEach { (code, want) ->
            val airport = requireNotNull(byCode[code]) { "$code missing" }
            val (zone, lat, lon) = want
            assertEquals(code, zone, airport.timeZoneId)
            assertEquals("$code latitude", lat, airport.latitude, 0.1)
            assertEquals("$code longitude", lon, airport.longitude, 0.1)
        }
    }

    /** Half-hour and three-quarter-hour zones are the ones a lazy table gets wrong. */
    @Test
    fun `stations in offset zones keep their odd offsets`() {
        val byCode = directory.associateBy { it.code }
        assertEquals("Asia/Kolkata", byCode.getValue("BOM").timeZoneId)
        assertEquals("Asia/Kathmandu", byCode.getValue("KTM").timeZoneId)
        assertEquals("Australia/Adelaide", byCode.getValue("ADL").timeZoneId)
        assertEquals("Asia/Tehran", byCode.getValue("IKA").timeZoneId)
    }

    @Test
    fun `the core list and the directory agree on where the core stations are`() {
        val byCode = directory.associateBy { it.code }
        Airports.all().forEach { curated ->
            val row = byCode[curated.code] ?: return@forEach
            assertEquals("${curated.code} zone", curated.timeZoneId, row.timeZoneId)
            assertEquals("${curated.code} latitude", curated.latitude, row.latitude, 0.5)
            assertEquals("${curated.code} longitude", curated.longitude, row.longitude, 0.5)
        }
    }
}
