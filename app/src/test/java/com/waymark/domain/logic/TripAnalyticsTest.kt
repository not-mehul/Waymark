package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripAnalyticsTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(
        trip = bundle.trip,
        party = TripParty(bundle.trip.id, bundle.travelers),
        segments = bundle.segments,
    )
    private val report = TripAnalytics.report(dossier, bundle.ideas)

    @Test
    fun `distance splits into air and ground and adds up`() {
        assertTrue(report.flownKm > 15_000)
        assertTrue(report.groundKm > 0)
        assertEquals(report.totalDistanceKm, report.flownKm + report.groundKm, 0.001)
    }

    @Test
    fun `the mode breakdown covers the ground distance and leads with air`() {
        assertEquals("Air", report.distanceByMode.first().label)
        assertTrue(report.distanceByMode.first().emphasis)

        val groundTotal = report.distanceByMode.drop(1).sumOf { it.value }
        assertEquals(report.groundKm, groundTotal, 0.5)
        // Every bar carries its own written value; none is blank.
        assertTrue(report.distanceByMode.all { it.display.isNotBlank() })
    }

    @Test
    fun `the longest leg is the transatlantic one`() {
        assertTrue(report.longestLegKm > 8_000)
        assertTrue(report.longestLegLabel!!.contains("SFO"))
    }

    @Test
    fun `there is one day load per calendar day of the trip`() {
        val first = bundle.segments.minOf { it.start.toLocalDate() }
        val last = bundle.segments.maxOf { it.end.toLocalDate() }
        assertEquals(
            java.time.temporal.ChronoUnit.DAYS.between(first, last).toInt() + 1,
            report.dayLoads.size,
        )
        assertTrue(report.dayLoads.zipWithNext().all { (a, b) -> a.date < b.date })
    }

    @Test
    fun `a day cannot hold more hours than a day has`() {
        report.dayLoads.forEach { load ->
            assertTrue("${load.date} held ${load.busyHours}h", load.busyHours <= 24.01)
            assertTrue(load.movingHours >= 0 && load.bookedHours >= 0)
        }
    }

    @Test
    fun `the busiest day carries one of the long-haul flights`() {
        val busiest = report.busiestDay!!
        val longHaulDays = bundle.segments
            .filterIsInstance<Segment.Flight>()
            .flatMap { listOf(it.start.toLocalDate(), it.end.toLocalDate()) }
            .toSet()

        assertTrue("busiest was ${busiest.date}", busiest.date in longHaulDays)
        assertEquals(report.dayLoads.maxOf { it.busyHours }, busiest.busyHours, 0.0001)
    }

    @Test
    fun `countries and zones are counted without repeats`() {
        assertTrue(report.countriesVisited.containsAll(listOf("France", "United Kingdom")))
        assertEquals(report.countriesVisited, report.countriesVisited.distinct())
        assertTrue(report.timeZonesCrossed >= 3)
    }

    @Test
    fun `nights are counted per city`() {
        val total = report.nightsByCity.sumOf { it.value }
        val expected = bundle.segments.filterIsInstance<Segment.Lodging>().sumOf { it.nights }
        assertEquals(expected.toDouble(), total, 0.001)
    }

    @Test
    fun `carbon uses the long-haul factor for long flights and is dominated by them`() {
        val flightsOnly = TripAnalytics.carbonKg(
            bundle.segments.filterIsInstance<Segment.Flight>()
        )
        assertTrue(flightsOnly > report.carbonKg * 0.9)

        // A single 10 000 km flight at the published long-haul factor.
        val oneLongFlight = bundle.segments
            .filterIsInstance<Segment.Flight>()
            .maxByOrNull { Geo.distanceKm(it.origin, it.destination) }!!
        val km = Geo.distanceKm(oneLongFlight.origin, oneLongFlight.destination)
        assertEquals(
            km * TripAnalytics.EmissionFactors.LONG_HAUL_FLIGHT,
            TripAnalytics.carbonKg(listOf(oneLongFlight)),
            0.001,
        )
    }

    @Test
    fun `walking costs nothing`() {
        val walk = bundle.segments.filterIsInstance<Segment.Ground>().first().copy(
            mode = com.waymark.domain.model.GroundMode.WALK,
        )
        assertEquals(0.0, TripAnalytics.carbonKg(listOf(walk)), 0.0001)
    }

    @Test
    fun `the booked share stays a fraction`() {
        assertTrue(report.bookedShare in 0.0..1.0)
    }

    @Test
    fun `an empty trip reports zeroes rather than throwing`() {
        val empty = TripAnalytics.report(dossier.copy(segments = emptyList()))
        assertEquals(0.0, empty.totalDistanceKm, 0.0)
        assertTrue(empty.dayLoads.isEmpty())
        assertTrue(empty.busiestDay == null)
        assertTrue(!empty.hasDistance)
    }
}
