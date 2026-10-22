package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereaboutsTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val segments = bundle.segments

    private fun at(date: LocalDate, time: String, zone: String) =
        ZonedDateTime.of(date, LocalTime.parse(time), ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test
    fun `before anything happens the trip is where it starts`() {
        val place = Whereabouts.at(segments, at(LocalDate.of(2026, 5, 1), "09:00", "UTC"))
        assertEquals("SFO", place?.code)
    }

    @Test
    fun `during the London stay the clock is London's`() {
        val day = LocalDate.of(2026, 5, 16)
        assertEquals(
            "Europe/London",
            Whereabouts.zoneIdAt(segments, at(day, "20:00", "Europe/London"), "America/Los_Angeles"),
        )
    }

    @Test
    fun `after the train to Paris the clock is Paris's`() {
        val split = LocalDate.of(2026, 5, 19)
        assertEquals(
            "Europe/Paris",
            Whereabouts.zoneIdAt(segments, at(split.plusDays(1), "18:00", "Europe/Paris"), "UTC"),
        )
    }

    @Test
    fun `an empty trip is nowhere`() {
        assertNull(Whereabouts.at(emptyList(), 0L))
        assertEquals("UTC", Whereabouts.zoneIdAt(emptyList(), 0L, "UTC"))
    }
}
