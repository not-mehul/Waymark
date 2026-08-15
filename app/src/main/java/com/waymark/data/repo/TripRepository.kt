package com.waymark.data.repo

import androidx.room.withTransaction
import com.waymark.data.local.Mappers
import com.waymark.data.local.SecretCipher
import com.waymark.data.local.TripMemberEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The one place that assembles a trip out of its four tables. Screens read a
 * [TripDossier] and never see a row.
 */
class TripRepository(
    private val database: WaymarkDatabase,
    private val cipher: SecretCipher,
) {

    private val trips = database.tripDao()
    private val travelers = database.travelerDao()
    private val segments = database.segmentDao()
    private val statuses = database.flightStatusDao()
    private val ideas = database.ideaDao()

    fun observeTrips(): Flow<List<Trip>> =
        trips.observeAll().map { rows -> rows.map(Mappers::toTrip) }

    fun observeParty(tripId: String): Flow<TripParty> =
        travelers.observeForTrip(tripId).map { rows ->
            TripParty(tripId, rows.map(Mappers::toTraveler))
        }

    fun observeSegments(tripId: String): Flow<List<Segment>> =
        segments.observeForTrip(tripId).map { rows -> rows.map(Mappers::toSegment) }

    /** Everything one trip screen needs, recomposed whenever any part changes. */
    fun observeDossier(tripId: String): Flow<TripDossier?> = combine(
        trips.observe(tripId),
        travelers.observeForTrip(tripId),
        segments.observeForTrip(tripId),
        statuses.observeAll(),
    ) { trip, party, rows, statusRows ->
        trip ?: return@combine null
        val mappedSegments = rows.map(Mappers::toSegment)
        val segmentIds = mappedSegments.map { it.id }.toSet()
        TripDossier(
            trip = Mappers.toTrip(trip),
            party = TripParty(tripId, party.map(Mappers::toTraveler)),
            segments = mappedSegments,
            statuses = statusRows
                .filter { it.segmentId in segmentIds }
                .associate { it.segmentId to Mappers.toStatus(it) },
        )
    }

    suspend fun findTrip(tripId: String): Trip? = trips.find(tripId)?.let(Mappers::toTrip)

    suspend fun saveTrip(trip: Trip) = trips.upsert(Mappers.toEntity(trip))

    suspend fun deleteTrip(tripId: String) = trips.delete(tripId)

    suspend fun createTrip(
        name: String,
        destinationSummary: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        homeZoneId: String,
    ): String {
        val trip = Trip(
            id = newId("trip"),
            name = name,
            destinationSummary = destinationSummary,
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            homeZoneId = homeZoneId,
        )
        trips.upsert(Mappers.toEntity(trip))
        return trip.id
    }

    suspend fun saveSegment(segment: Segment) {
        segments.upsert(Mappers.toEntity(segment))
        widenTripToFit(segment)
    }

    suspend fun saveSegments(items: List<Segment>) {
        segments.upsertAll(items.map(Mappers::toEntity))
        items.forEach { widenTripToFit(it) }
    }

    suspend fun deleteSegment(segmentId: String) {
        database.withTransaction {
            statuses.delete(segmentId)
            // An idea promoted onto the timeline goes back to the list rather
            // than vanishing with the segment.
            ideas.releaseSegment(segmentId)
            segments.delete(segmentId)
        }
    }

    suspend fun findSegment(segmentId: String): Segment? =
        segments.find(segmentId)?.let(Mappers::toSegment)

    /** Assign or unassign travelers on one segment — the split-itinerary edit. */
    suspend fun setTravelers(segmentId: String, travelerIds: Set<String>) {
        val existing = segments.find(segmentId) ?: return
        val updated = Mappers.toSegment(existing).let { segment ->
            when (segment) {
                is Segment.Flight -> segment.copy(travelerIds = travelerIds)
                is Segment.Lodging -> segment.copy(travelerIds = travelerIds)
                is Segment.Ground -> segment.copy(travelerIds = travelerIds)
                is Segment.Experience -> segment.copy(travelerIds = travelerIds)
            }
        }
        segments.upsert(Mappers.toEntity(updated))
    }

    suspend fun addTraveler(tripId: String, traveler: Traveler) {
        database.withTransaction {
            travelers.upsert(Mappers.toEntity(traveler))
            travelers.addMember(
                TripMemberEntity(
                    tripId = tripId,
                    travelerId = traveler.id,
                    position = travelers.memberCount(tripId),
                )
            )
        }
    }

    suspend fun saveTraveler(traveler: Traveler) = travelers.upsert(Mappers.toEntity(traveler))

    suspend fun removeTraveler(tripId: String, travelerId: String) =
        travelers.removeMember(tripId, travelerId)

    fun observeAllTravelers(): Flow<List<Traveler>> =
        travelers.observeAll().map { rows -> rows.map(Mappers::toTraveler) }

    suspend fun tripCount(): Int = trips.count()

    /** A trip's dates should never exclude something booked inside it. */
    private suspend fun widenTripToFit(segment: Segment) {
        val trip = trips.find(segment.tripId) ?: return
        val start = minOf(trip.startEpochMillis, segment.startEpochMillis)
        val end = maxOf(trip.endEpochMillis, segment.endEpochMillis)
        if (start != trip.startEpochMillis || end != trip.endEpochMillis) {
            trips.upsert(trip.copy(startEpochMillis = start, endEpochMillis = end))
        }
    }

    companion object {
        fun newId(prefix: String): String =
            "$prefix-${UUID.randomUUID().toString().take(8)}"
    }
}
