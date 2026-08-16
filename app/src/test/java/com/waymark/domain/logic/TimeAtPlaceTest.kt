package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeAtPlaceTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(
        trip = bundle.trip,
        party = TripParty(bundle.trip.id, bundle.travelers),
        segments = bundle.segments,
    )
    private val hour = 60 * 60 * 1000L

    @Test
    fun `the worked example is a trip to London and Paris, in that order`() {
        val destinations = TimeAtPlace.destinations(dossier).map { it.place.city }
        assertEquals(listOf("London", "Paris"), destinations)
    }

    @Test
    fun `stays are ranked by time and each is at least a day`() {
        val stays = TimeAtPlace.stays(dossier)
        assertTrue(stays.isNotEmpty())
        assertEquals(stays.sortedByDescending { it.minutes }.map { it.minutes }, stays.map { it.minutes })
        TimeAtPlace.destinations(dossier).forEach {
            assertTrue("${it.place.city} is under a day", it.minutes >= TimeAtPlace.DESTINATION_MINUTES)
        }
    }

    /**
     * The reason this exists. Picking the last arrival made a trip to Japan
     * through Doha a trip to Qatar; picking every arrival gave a two-hour
     * connection the same billing as the week after it.
     */
    @Test
    fun `a connection is not a destination`() {
        val flight = bundle.segments.filterIsInstance<Segment.Flight>().first()
        val start = flight.startEpochMillis

        val out = flight.copy(
            id = "leg-1",
            startEpochMillis = start,
            endEpochMillis = start + 6 * hour,
        )
        val onward = flight.copy(
            id = "leg-2",
            origin = flight.destination,
            destination = flight.origin,
            // Two hours on the ground at the first arrival, then a week away.
            startEpochMillis = start + 8 * hour,
            endEpochMillis = start + 14 * hour,
        )
        val home = flight.copy(
            id = "leg-3",
            origin = flight.origin,
            destination = flight.destination,
            startEpochMillis = start + 14 * hour + 7 * 24 * hour,
            endEpochMillis = start + 20 * hour + 7 * 24 * hour,
        )

        val trip = dossier.copy(segments = listOf(out, onward, home))
        val stays = TimeAtPlace.stays(trip)

        // Both places are visited; only the week counts as a destination.
        assertEquals(2, stays.size)
        val destinations = TimeAtPlace.destinations(trip)
        assertEquals(1, destinations.size)
        assertEquals(flight.origin.city, destinations.single().place.city)
    }

    @Test
    fun `a trip with one movement has nowhere to have stayed`() {
        val single = dossier.copy(segments = bundle.segments.take(1))
        assertTrue(TimeAtPlace.stays(single).isEmpty())
        assertNull(TimeAtPlace.primary(single))
        assertTrue(TimeAtPlace.destinations(single).isEmpty())
    }

    @Test
    fun `a trip shorter than a day still has a destination`() {
        val flight = bundle.segments.filterIsInstance<Segment.Flight>().first()
        val start = flight.startEpochMillis
        val out = flight.copy(id = "a", startEpochMillis = start, endEpochMillis = start + hour)
        val back = flight.copy(
            id = "b",
            origin = flight.destination,
            destination = flight.origin,
            startEpochMillis = start + 6 * hour,
            endEpochMillis = start + 7 * hour,
        )
        val dayTrip = dossier.copy(segments = listOf(out, back))
        assertEquals(1, TimeAtPlace.destinations(dayTrip).size)
    }

    @Test
    fun `filtering to one traveler measures that traveler's own days`() {
        val julian = bundle.travelers.first { it.fullName.startsWith("Julian") }
        val his = TimeAtPlace.stays(dossier, travelerFilter = julian.id)
        assertTrue(his.isNotEmpty())
        assertTrue(his.all { it.minutes > 0 })
    }
}
