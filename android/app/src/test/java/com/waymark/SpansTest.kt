package com.waymark

import kotlin.test.Test
import kotlin.test.assertEquals

/** The same cases as duration-text.test.ts, so the shell says what the page says. */
class SpansTest {

    @Test
    fun durationsInTheUnitsAPersonWouldUse() {
        assertEquals("0m", Spans.duration(0))
        assertEquals("40m", Spans.duration(40))
        assertEquals("2h 40m", Spans.duration(160))
        assertEquals("3h", Spans.duration(180))
        assertEquals("1d 2h", Spans.duration(26 * 60))
        assertEquals("1w 2d", Spans.duration(9 * 24 * 60))
        assertEquals("1mo 1w 3d", Spans.duration(38 * 24 * 60))
        assertEquals("−1h 5m", Spans.duration(-65))
    }

    @Test
    fun relativeTimes() {
        val now = 1_000_000_000L
        assertEquals("now", Spans.relative(now + 30_000, now))
        assertEquals("in 3h 10m", Spans.relative(now + 190 * Spans.MINUTE_MILLIS, now))
        assertEquals("12m ago", Spans.relative(now - 12 * Spans.MINUTE_MILLIS, now))
    }

    @Test
    fun minutesUntilRoundsUp() {
        assertEquals(1, Spans.minutesUntil(1, 0))
        assertEquals(40, Spans.minutesUntil(40 * Spans.MINUTE_MILLIS, 0))
    }
}
