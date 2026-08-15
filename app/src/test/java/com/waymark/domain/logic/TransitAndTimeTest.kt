package com.waymark.domain.logic

import com.waymark.data.catalog.Airports
import com.waymark.domain.model.GroundMode
import com.waymark.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TransitAndTimeTest {

    private val heathrow = Airports.place("LHR")
    private val bloomsbury = Place(
        name = "Bloomsbury", city = "London", country = "United Kingdom",
        latitude = 51.5205, longitude = -0.1265, timeZoneId = "Europe/London",
    )

    @Test
    fun `a Heathrow transfer lands in the right hour`() {
        val estimate = TransitEstimator.estimate(heathrow, bloomsbury, GroundMode.TRANSIT)
        assertTrue(
            "estimate was ${estimate.totalMinutes} minutes",
            estimate.totalMinutes in 40..100,
        )
        assertTrue(estimate.overheadMinutes > 0)
    }

    @Test
    fun `a walk across the street is minutes, not an hour`() {
        val nearby = bloomsbury.copy(name = "Museum", latitude = 51.5194, longitude = -0.1270)
        val estimate = TransitEstimator.estimate(bloomsbury, nearby, GroundMode.WALK)
        assertTrue("estimate was ${estimate.totalMinutes}", estimate.totalMinutes <= 6)
    }

    @Test
    fun `mode suggestion scales with distance`() {
        val nextDoor = bloomsbury.copy(latitude = 51.5210, longitude = -0.1268)
        assertEquals(GroundMode.WALK, TransitEstimator.suggestMode(bloomsbury, nextDoor))
        assertEquals(GroundMode.TRANSIT, TransitEstimator.suggestMode(heathrow, bloomsbury))
    }

    @Test
    fun `options are ordered by time and never empty`() {
        val options = TransitEstimator.options(heathrow, bloomsbury)
        assertTrue(options.isNotEmpty())
        assertEquals(options.sortedBy { it.totalMinutes }, options)
    }

    @Test
    fun `confidence drops when coordinates are missing`() {
        val unknown = Place(name = "Somewhere")
        val estimate = TransitEstimator.estimate(bloomsbury, unknown, GroundMode.TAXI)
        assertEquals(TransitEstimate.Confidence.LOW, estimate.confidence)
    }

    @Test
    fun `durations read the way a traveler says them`() {
        assertEquals("45m", TimeText.duration(45))
        assertEquals("2h", TimeText.duration(120))
        assertEquals("2h 40m", TimeText.duration(160))
        assertEquals("−15m", TimeText.duration(-15))
    }

    @Test
    fun `relative time is honest about direction`() {
        val now = 1_800_000_000_000L
        assertEquals("now", TimeText.relative(now, now))
        assertEquals("in 1h", TimeText.relative(now + 3_600_000, now))
        assertEquals("20m ago", TimeText.relative(now - 1_200_000, now))
    }

    @Test
    fun `an overnight arrival is marked with a day offset`() {
        val london = ZoneId.of("Europe/London")
        val sanFrancisco = ZoneId.of("America/Los_Angeles")
        val departure = ZonedDateTime.of(LocalDate.of(2026, 5, 14), java.time.LocalTime.of(16, 20), sanFrancisco)
        val arrival = ZonedDateTime.of(LocalDate.of(2026, 5, 15), java.time.LocalTime.of(10, 55), london)
        assertEquals("+1", TimeText.dayOffsetSuffix(departure, arrival))
        assertEquals(null, TimeText.dayOffsetSuffix(departure, departure))
    }

    @Test
    fun `zone shift states what the body is being asked to absorb`() {
        val shift = TimeText.zoneShift(
            ZoneId.of("America/Los_Angeles"),
            ZoneId.of("Europe/London"),
            ZonedDateTime.of(LocalDate.of(2026, 5, 14), java.time.LocalTime.NOON, ZoneId.of("UTC"))
                .toInstant().toEpochMilli(),
        )
        assertEquals("+8h", shift)
        assertEquals(null, TimeText.zoneShift(ZoneId.of("UTC"), ZoneId.of("UTC"), 0))
    }

    @Test
    fun `date ranges collapse within a month`() {
        val range = TimeText.dateRange(LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 20))
        assertTrue(range, range.contains("14") && range.contains("20") && range.contains("May"))
    }
}
