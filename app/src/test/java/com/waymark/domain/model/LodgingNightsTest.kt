package com.waymark.domain.model

import com.waymark.data.catalog.SampleItinerary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Nights are calendar nights in the hotel's own zone. A 15:00 check-in and an
 * 11:00 check-out four days later is four nights, though it is only 3 days and
 * 20 hours by the clock — which is what the count used to report.
 */
class LodgingNightsTest {

    private val london = ZoneId.of("Europe/London")

    private fun stay(inDate: LocalDate, inTime: String, outDate: LocalDate, outTime: String) =
        Segment.Lodging(
            id = "stay",
            tripId = "trip",
            propertyName = "Hotel",
            origin = Place(name = "Hotel", timeZoneId = london.id),
            startEpochMillis = ZonedDateTime.of(inDate, LocalTime.parse(inTime), london)
                .toInstant().toEpochMilli(),
            endEpochMillis = ZonedDateTime.of(outDate, LocalTime.parse(outTime), london)
                .toInstant().toEpochMilli(),
            startZoneId = london.id,
            endZoneId = london.id,
        )

    @Test
    fun `the worked example has four nights in London and three in Paris`() {
        val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
        val stays = bundle.segments.filterIsInstance<Segment.Lodging>().associateBy { it.id }
        assertEquals(4L, stays.getValue("seg-hotel-london").nights)
        assertEquals(3L, stays.getValue("seg-hotel-paris").nights)
    }

    @Test
    fun `a late check-in and an early check-out is still one night`() {
        val day = LocalDate.of(2026, 3, 2)
        assertEquals(1L, stay(day, "20:00", day.plusDays(1), "10:00").nights)
    }

    @Test
    fun `a day room counts as one rather than zero`() {
        val day = LocalDate.of(2026, 3, 2)
        assertEquals(1L, stay(day, "09:00", day, "17:00").nights)
    }

    @Test
    fun `a stay across the clocks going forward is not a night short`() {
        // The UK moves to summer time on 29 March 2026.
        val day = LocalDate.of(2026, 3, 28)
        assertEquals(2L, stay(day, "15:00", day.plusDays(2), "11:00").nights)
    }
}
