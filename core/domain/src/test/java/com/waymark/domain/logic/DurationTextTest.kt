package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a span reads, at every scale a trip actually produces — from a tight
 * connection to a season away.
 */
class DurationTextTest {

    private val hour = 60
    private val day = 24 * hour
    private val week = 7 * day

    @Test
    fun `under a day stays in hours and minutes`() {
        assertEquals("45m", TimeText.duration(45))
        assertEquals("1h", TimeText.duration(60))
        assertEquals("2h 40m", TimeText.duration(160))
        assertEquals("23h 59m", TimeText.duration(day - 1))
    }

    @Test
    fun `a day or more drops minutes and reads in days`() {
        assertEquals("1d", TimeText.duration(day))
        assertEquals("1d 6h", TimeText.duration(day + 6 * hour))
        // The case that prompted this: a 30-hour layover is not "30h".
        assertEquals("1d 6h", TimeText.duration(30 * hour))
        assertEquals("6d 23h", TimeText.duration(week - hour))
    }

    @Test
    fun `a week or more reads in weeks and days`() {
        assertEquals("1w", TimeText.duration(week))
        assertEquals("1w 3d", TimeText.duration(week + 3 * day))
        assertEquals("2w", TimeText.duration(2 * week))
        // Hours are gone at this scale, not rounded into a day.
        assertEquals("1w 3d", TimeText.duration(week + 3 * day + 20 * hour))
    }

    @Test
    fun `four weeks or more reads in months, weeks and days`() {
        assertEquals("1mo", TimeText.duration(4 * week))
        assertEquals("1mo 1w", TimeText.duration(5 * week))
        assertEquals("1mo 1w 3d", TimeText.duration(5 * week + 3 * day))
        assertEquals("2mo", TimeText.duration(8 * week))
        assertEquals("3mo 2w 1d", TimeText.duration(12 * week + 2 * week + day))
    }

    @Test
    fun `zero terms are dropped rather than printed`() {
        assertEquals("2h", TimeText.duration(2 * hour))
        assertEquals("3d", TimeText.duration(3 * day))
        assertEquals("2w", TimeText.duration(2 * week))
        assertTrue(TimeText.duration(4 * week).none { it == '0' })
    }

    @Test
    fun `a negative span keeps its sign at every scale`() {
        assertEquals("−20m", TimeText.duration(-20))
        assertEquals("−1d 6h", TimeText.duration(-30 * hour))
        assertEquals("−1w 3d", TimeText.duration(-(week + 3 * day)))
    }

    @Test
    fun `nothing at all still prints something`() {
        assertEquals("0m", TimeText.duration(0))
    }

    /**
     * The invariant behind the thresholds: as a span grows, the unit it leads
     * with never gets finer.
     *
     * Deliberately checked on the *leading* term. The trailing one legitimately
     * goes the other way — "1d" (a zero hour term dropped) is followed by
     * "1d 1h" — and an earlier version of this test asserted on the trailing
     * unit and failed on exactly that.
     */
    @Test
    fun `the leading unit only ever coarsens as the span grows`() {
        val rank = mapOf("m" to 0, "h" to 1, "d" to 2, "w" to 3, "mo" to 4)
        var previous = 0
        (1..(20 * week) step 37).forEach { minutes ->
            val leading = TimeText.duration(minutes)
                .substringBefore(" ")
                .dropWhile { it.isDigit() }
            val current = rank.getValue(leading)
            assertTrue(
                "at $minutes minutes the unit got finer: ${TimeText.duration(minutes)}",
                current >= previous,
            )
            previous = current
        }
    }

    /** And every span is at most three terms, so a line never wraps on it. */
    @Test
    fun `no span is written with more than three terms`() {
        (1..(60 * week) step 53).forEach { minutes ->
            val terms = TimeText.duration(minutes).split(" ")
            assertTrue("${TimeText.duration(minutes)} has ${terms.size} terms", terms.size <= 3)
        }
    }

    @Test
    fun `durationBetween agrees with duration`() {
        val start = 1_700_000_000_000L
        assertEquals(
            TimeText.duration(3 * day + 4 * hour),
            TimeText.durationBetween(start, start + (3L * day + 4 * hour) * 60_000L),
        )
    }
}
