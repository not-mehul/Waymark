package com.waymark.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The country table is bundled reference data, so what can be tested is its
 * shape rather than its truth: every row well formed, no duplicates, and — the
 * one that matters for the app working at all — every country a bundled station
 * can be in has a row.
 */
class CountriesTest {

    private val all = Countries.all()

    @Test
    fun `every row is well formed`() {
        assertTrue(all.size > 200)
        all.forEach { country ->
            assertEquals("${country.code} is not an alpha-2 code", 2, country.code.length)
            assertTrue(country.code, country.code.all { it in 'A'..'Z' })
            assertTrue(country.name, country.name.isNotBlank())
            assertEquals("${country.code} currency", 3, country.currencyCode.length)
            assertTrue(country.currencyName.isNotBlank())
            assertTrue("${country.code} has no plug type", country.plugTypes.isNotEmpty())
            assertTrue(
                "${country.code} plug letters",
                country.plugTypes.all { it.length == 1 && it[0] in 'A'..'O' },
            )
            assertTrue("${country.code} volts", country.volts in 100..250)
            assertTrue("${country.code} hertz", country.hertz == 50 || country.hertz == 60)
            assertTrue("${country.code} emergency", country.emergencyNumber.isNotBlank())
            assertTrue(country.emergencyNumber.all { it.isDigit() })
        }
    }

    @Test
    fun `codes and names are unique`() {
        assertEquals(all.size, all.map { it.code }.distinct().size)
        assertEquals(all.size, all.map { it.name }.distinct().size)
    }

    /**
     * The whole point of replacing nine hand-written cities. If a station can
     * be entered, its country has notes.
     */
    @Test
    fun `every country in the bundled directory is covered`() {
        val uncovered = AirportDirectory.parse(DirectoryFixture.lines().asSequence())
            .map { it.country }
            .distinct()
            .filter { Countries.forName(it) == null && Countries.forCode(it) == null }
            // The directory has one strip in Antarctica, which has no currency,
            // no emergency number and nobody to call one.
            .filterNot { it.equals("AQ", ignoreCase = true) }

        assertTrue("no notes for $uncovered", uncovered.isEmpty())
    }

    @Test
    fun `lookup works by code and by name, and says no to nonsense`() {
        val japan = requireNotNull(Countries.forCode("JP"))
        assertEquals("Japan", japan.name)
        assertEquals("JPY", japan.currencyCode)
        assertTrue(japan.drivesOnLeft)
        assertEquals(japan, Countries.forName("Japan"))

        // The core station list writes names, the directory writes codes, and
        // neither is consistent about case or the definite article.
        assertNotNull(Countries.forName("united kingdom"))
        assertNotNull(Countries.forName("The United Kingdom"))
        assertNotNull(Countries.forName("Türkiye"))
        assertNotNull(Countries.forName("Turkiye"))

        assertNull(Countries.forCode("ZZ"))
        assertNull(Countries.forName("Atlantis"))
        assertNull(Countries.forName(""))
    }

    @Test
    fun `the rendered strings read as sentences rather than fields`() {
        val uk = requireNotNull(Countries.forCode("GB"))
        assertEquals("Pound sterling (GBP)", uk.currency)
        assertEquals("Type G · 230V / 50Hz", uk.power)
        assertEquals("Drives on the left", uk.driving)

        val us = requireNotNull(Countries.forCode("US"))
        assertEquals("Type A / B · 120V / 60Hz", us.power)
        assertEquals("Drives on the right", us.driving)
    }
}

/**
 * The asset is not on the JVM test classpath, so it is read from the source
 * tree — from either working directory Gradle might use.
 */
private object DirectoryFixture {
    fun lines(): List<String> =
        java.io.File("src/main/assets/${AirportDirectory.ASSET_NAME}")
            .takeIf { it.exists() }
            ?.readLines()
            ?: java.io.File("app/src/main/assets/${AirportDirectory.ASSET_NAME}").readLines()
}
