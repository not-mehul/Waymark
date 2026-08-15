package com.waymark.data.repo

import com.waymark.data.catalog.SampleItinerary
import java.time.LocalDate

/**
 * Writes the worked example on first run only. It is anchored to the current
 * date so the sample trip is always about to happen — a timeline of last
 * year's flights would demonstrate nothing.
 */
class SampleSeeder(
    private val trips: TripRepository,
    private val vault: VaultRepository,
    private val ideas: IdeaRepository,
) {

    suspend fun seedIfEmpty(today: LocalDate = LocalDate.now()) {
        if (trips.tripCount() > 0) return
        seed(today.plusDays(DAYS_UNTIL_DEPARTURE))
    }

    suspend fun seed(departure: LocalDate) {
        val bundle = SampleItinerary.build(departure)
        trips.saveTrip(bundle.trip)
        bundle.travelers.forEach { trips.addTraveler(bundle.trip.id, it) }
        trips.saveSegments(bundle.segments)
        bundle.reservations.forEach { vault.save(it) }
        bundle.passes.forEach { vault.savePass(it) }
        ideas.saveAll(bundle.ideas)
    }

    private companion object {
        const val DAYS_UNTIL_DEPARTURE = 6L
    }
}
