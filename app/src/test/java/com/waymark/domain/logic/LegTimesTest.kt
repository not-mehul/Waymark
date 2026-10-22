package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LegTimesTest {

    private val date = LocalDate.of(2026, 11, 10)
    private val sfo = ZoneId.of("America/Los_Angeles")
    private val lax = sfo
    private val sin = ZoneId.of("Asia/Singapore")
    private val hkg = ZoneId.of("Asia/Hong_Kong")
    private val lhr = ZoneId.of("Europe/London")
    private val ams = ZoneId.of("Europe/Amsterdam")
    private val syd = ZoneId.of("Australia/Sydney")

    private fun t(text: String) = LocalTime.parse(text)

    @Test
    fun `an arrival two calendar days later is found, not saved negative`() {
        // SQ33: the departure that used to save at minus seven hours.
        val leg = requireNotNull(LegTimes.flight(date, t("21:45"), sfo, t("06:30"), sin))
        assertEquals(2, leg.dayOffset)
        assertEquals(16 * 60 + 45L, leg.minutes)
    }

    @Test
    fun `an arrival the day before departure is found`() {
        // CX880 leaves Hong Kong after midnight and lands in Los Angeles the
        // previous evening.
        val leg = requireNotNull(LegTimes.flight(date, t("00:50"), hkg, t("22:15"), lax))
        assertEquals(-1, leg.dayOffset)
        assertEquals(13 * 60 + 25L, leg.minutes)
    }

    @Test
    fun `overnight and same-day flights keep their ordinary shape`() {
        val transatlantic = requireNotNull(LegTimes.flight(date, t("16:20"), sfo, t("10:55"), lhr))
        assertEquals(1, transatlantic.dayOffset)
        assertEquals(10 * 60 + 35L, transatlantic.minutes)

        val hop = requireNotNull(LegTimes.flight(date, t("07:05"), lhr, t("09:25"), ams))
        assertEquals(0, hop.dayOffset)
        assertEquals(80L, hop.minutes)

        // Westbound across the date line: same calendar date, fifteen hours.
        val pacific = requireNotNull(LegTimes.flight(date, t("10:45"), syd, t("06:35"), lax))
        assertEquals(0, pacific.dayOffset)
        assertEquals(14 * 60 + 50L, pacific.minutes)
    }

    @Test
    fun `a stated day offset is used as stated, and refused when impossible`() {
        val stated = LegTimes.flight(date, t("21:45"), sfo, t("06:30"), sin, dayOffset = 2)
        assertNotNull(stated)
        assertNull(LegTimes.flight(date, t("21:45"), sfo, t("06:30"), sin, dayOffset = 0))
    }

    @Test
    fun `times that cannot be a flight are refused`() {
        assertNull(LegTimes.flight(date, t("09:00"), lhr, t("09:00"), lhr, dayOffset = 0))
        // A day and a half in the air is a time typed against the wrong airport.
        assertNull(LegTimes.flight(date, t("09:00"), lhr, t("21:00"), lhr, dayOffset = 1))
    }

    @Test
    fun `an end before its start runs past midnight once, and no further`() {
        val start = ZonedDateTime.of(date, t("22:40"), lhr)
        val sleeper = LegTimes.endAfter(start, LocalDateTime.of(date, t("07:10")), lhr)
        assertEquals(date.plusDays(1), sleeper.toLocalDate())
        assertTrue(LegTimes.isOrdered(start, sleeper))

        val backwards = LegTimes.endAfter(start, LocalDateTime.of(date.minusDays(3), t("07:10")), lhr)
        assertEquals(date.minusDays(3), backwards.toLocalDate())
        assertFalse(LegTimes.isOrdered(start, backwards))
    }
}
