package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import com.waymark.domain.model.WeatherBasis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherPlanTest {

    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(sample.trip, TripParty(sample.trip.id, sample.travelers), sample.segments)

    @Test
    fun `the worked example leaves San Francisco for London, then Paris, day by day`() {
        val stops = WeatherPlan.stops(dossier, today = LocalDate.of(2026, 5, 1))
        assertEquals(listOf("San Francisco", "London", "Paris"), stops.map { it.label }.distinct())
        val days = stops.flatMap { it.days }
        // Every day once, in order, with no gaps.
        assertEquals(days.sorted(), days)
        assertEquals(days.distinct(), days)
        assertEquals(sample.trip.endDate(), days.last())
    }

    @Test
    fun `days already gone are not asked about`() {
        val today = sample.trip.startDate().plusDays(2)
        val days = WeatherPlan.stops(dossier, today).flatMap { it.days }
        assertTrue(days.all { !it.isBefore(today) })
        assertEquals(today, days.first())
    }

    @Test
    fun `a trip that is over needs no weather`() {
        assertTrue(WeatherPlan.stops(dossier, sample.trip.endDate().plusDays(1)).isEmpty())
    }

    @Test
    fun `the next sixteen days are forecast, the rest are last year`() {
        val today = LocalDate.of(2026, 5, 1)
        assertEquals(WeatherBasis.FORECAST, WeatherPlan.basisFor(today, today))
        assertEquals(WeatherBasis.FORECAST, WeatherPlan.basisFor(today.plusDays(15), today))
        assertEquals(WeatherBasis.LAST_YEAR, WeatherPlan.basisFor(today.plusDays(16), today))
    }

    @Test
    fun `places are cached to about a kilometre`() {
        assertEquals("51.51,-0.13", WeatherPlan.keyOf(51.5074, -0.1278))
    }

    @Test
    fun `weather is said in words and in the reader's degrees`() {
        assertEquals("Partly cloudy", WeatherText.describe(2))
        assertEquals("Thunderstorms", WeatherText.describe(95))
        assertEquals("21°", WeatherText.temperature(21.4, fahrenheit = false))
        assertEquals("71°", WeatherText.temperature(21.4, fahrenheit = true))
        assertTrue(WeatherText.isWet(61))
        assertTrue(!WeatherText.isWet(3))
    }
}
