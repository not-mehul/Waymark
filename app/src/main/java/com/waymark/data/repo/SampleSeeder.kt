package com.waymark.data.repo

import com.waymark.data.catalog.SampleItinerary
import java.time.LocalDate

/**
 * The worked example: a week in London and Paris with flights, a hotel, a
 * Eurostar, ideas and documents already on it.
 *
 * This used to run itself on first launch, which meant every fresh install
 * opened on somebody else's holiday and a real first trip began by deleting a
 * fake one. It is offered instead — a second, quieter button on an empty shelf
 * — so the demonstration is available to anyone who wants to look around the
 * app before trusting it with a real itinerary, and invisible to everyone else.
 *
 * It is anchored to the current date so the sample trip is always about to
 * happen: a timeline of last year's flights would demonstrate nothing.
 */
class SampleSeeder(
    private val trips: TripRepository,
    private val ideas: IdeaRepository,
    private val preparations: PreparationRepository,
) {

    /** Writes the example and returns the id of the trip it created. */
    suspend fun seed(departure: LocalDate = LocalDate.now().plusDays(DAYS_UNTIL_DEPARTURE)): String {
        val bundle = SampleItinerary.build(departure)
        trips.saveTrip(bundle.trip)
        bundle.travelers.forEach { trips.addTraveler(bundle.trip.id, it) }
        trips.saveSegments(bundle.segments)
        ideas.saveAll(bundle.ideas)
        bundle.documents.forEach { preparations.saveDocument(it) }
        return bundle.trip.id
    }

    private companion object {
        const val DAYS_UNTIL_DEPARTURE = 6L
    }
}
