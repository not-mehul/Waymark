package com.waymark.data.repo

import androidx.room.withTransaction
import com.waymark.data.local.Mappers
import com.waymark.data.local.TripMemberEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.share.TripArchive
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import com.waymark.domain.model.copyCommon
import com.waymark.domain.model.normaliseParty
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The one place that assembles a trip out of its four tables. Screens read a
 * [TripDossier] and never see a row.
 *
 * Every write that touches more than one row happens in a transaction: a
 * booking saved without the trip widened around it, or a traveler removed from
 * the party but left on their bookings, is a state the traveler would act on.
 */
class TripRepository(private val database: WaymarkDatabase) {

    private val trips = database.tripDao()
    private val travelers = database.travelerDao()
    private val segments = database.segmentDao()
    private val ideas = database.ideaDao()
    private val alerts = database.alertDao()

    fun observeTrips(): Flow<List<Trip>> =
        trips.observeAll().map { rows -> rows.map(Mappers::toTrip) }

    /** Everything one trip screen needs, recomposed whenever any part changes. */
    fun observeDossier(tripId: String): Flow<TripDossier?> = combine(
        trips.observe(tripId),
        travelers.observeForTrip(tripId),
        segments.observeForTrip(tripId),
    ) { trip, party, rows ->
        trip ?: return@combine null
        TripDossier(
            trip = Mappers.toTrip(trip),
            party = TripParty(tripId, party.map(Mappers::toTraveler)),
            segments = rows.mapNotNull(Mappers::toSegment),
        )
    }

    suspend fun saveTrip(trip: Trip) = trips.upsert(Mappers.toEntity(trip))

    /** Off the shelf and back again; nothing on the trip changes. */
    suspend fun setArchived(tripId: String, archived: Boolean) = trips.setArchived(tripId, archived)

    /**
     * Remove a trip and everything hanging off it.
     *
     * Segments and ideas carry a foreign key to the trip with `ON DELETE
     * CASCADE`. Raised reminders do not — that table is deliberately outside
     * the graph — so they are swept explicitly, and so are profiles of people
     * who were on this trip and no other.
     */
    suspend fun deleteTrip(tripId: String) {
        database.withTransaction {
            trips.delete(tripId)
            alerts.pruneOrphans()
            travelers.pruneOrphans()
        }
    }

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

    suspend fun saveSegment(segment: Segment) = saveSegments(listOf(segment))

    /**
     * Write bookings, in the stored form of their party, and widen each trip
     * so its dates never exclude something booked inside it.
     */
    suspend fun saveSegments(items: List<Segment>) {
        if (items.isEmpty()) return
        database.withTransaction {
            val parties = items.map { it.tripId }.distinct().associateWith { tripId ->
                travelers.forTrip(tripId).map { it.id }.toSet()
            }
            val normalised = items.map { segment ->
                segment.copyCommon(
                    travelerIds = normaliseParty(segment.travelerIds, parties.getValue(segment.tripId)),
                )
            }
            segments.upsertAll(normalised.map(Mappers::toEntity))
            normalised.groupBy { it.tripId }.forEach { (tripId, saved) -> widenTripToFit(tripId, saved) }
        }
    }

    /** Remove one booking, and everything that only existed because of it. */
    suspend fun deleteSegment(segmentId: String) {
        database.withTransaction {
            // An idea promoted onto the timeline goes back to the list rather
            // than vanishing with the segment.
            ideas.releaseSegment(segmentId)
            alerts.acknowledgeFor(segmentId)
            segments.delete(segmentId)
        }
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

    /** Someone already travelling with you, brought onto another trip. */
    suspend fun addExistingTraveler(tripId: String, travelerId: String) {
        database.withTransaction {
            if (travelers.find(travelerId) == null) return@withTransaction
            travelers.addMember(
                TripMemberEntity(tripId, travelerId, position = travelers.memberCount(tripId))
            )
        }
    }

    /** A profile edit: preferences, loyalty numbers, a phone number. Every trip sees it. */
    suspend fun saveTraveler(traveler: Traveler) = travelers.upsert(Mappers.toEntity(traveler))

    fun observeAllTravelers(): Flow<List<Traveler>> =
        travelers.observeAll().map { rows -> rows.map(Mappers::toTraveler) }

    /** Traveler id to the number of trips they are on. */
    fun observeTripCounts(): Flow<Map<String, Int>> =
        travelers.observeTripCounts().map { rows -> rows.associate { it.travelerId to it.trips } }

    /**
     * Take someone off a trip, cleanly.
     *
     * Bookings only they were on go with them — there is nobody left to take
     * them. Bookings shared with others lose their name. Ideas forget their
     * interest. And a profile on no other trip is removed, rather than left
     * behind as a row nothing can reach.
     */
    suspend fun removeTraveler(tripId: String, travelerId: String) {
        database.withTransaction {
            val party = travelers.forTrip(tripId).map { it.id }.toSet()
            val remaining = party - travelerId
            segments.forTrip(tripId).mapNotNull(Mappers::toSegment).forEach { segment ->
                val ids = segment.travelerIds
                when {
                    ids.isEmpty() || travelerId !in ids -> Unit
                    ids == setOf(travelerId) -> {
                        ideas.releaseSegment(segment.id)
                        segments.delete(segment.id)
                    }
                    else -> segments.upsert(
                        Mappers.toEntity(
                            segment.copyCommon(travelerIds = normaliseParty(ids - travelerId, remaining))
                        )
                    )
                }
            }
            ideas.forTrip(tripId).map(Mappers::toIdea)
                .filter { travelerId in it.interestedTravelerIds }
                .forEach { idea ->
                    ideas.upsert(
                        Mappers.toEntity(idea.copy(interestedTravelerIds = idea.interestedTravelerIds - travelerId))
                    )
                }
            travelers.removeMember(tripId, travelerId)
            if (travelers.tripCountFor(travelerId) == 0) travelers.delete(travelerId)
            alerts.pruneOrphans()
        }
    }

    /**
     * Pull a trip's dates in to exactly the span of its bookings — offered on
     * the edit sheet, because a trip only ever widens on its own and a booking
     * moved or removed leaves the old edge behind.
     */
    suspend fun fitTripToBookings(tripId: String) {
        database.withTransaction {
            val trip = trips.find(tripId) ?: return@withTransaction
            val rows = segments.forTrip(tripId)
            if (rows.isEmpty()) return@withTransaction
            trips.upsert(
                trip.copy(
                    startEpochMillis = rows.minOf { it.startEpochMillis },
                    endEpochMillis = rows.maxOf { it.endEpochMillis },
                )
            )
        }
    }

    /**
     * Housekeeping at start-up, for databases written by earlier builds:
     * reminders whose booking has gone, profiles on no trip, and booking
     * parties written out in full or naming people no longer on the trip.
     */
    suspend fun repair() {
        database.withTransaction {
            alerts.pruneOrphans()
            travelers.pruneOrphans()
            val members = travelers.allMembers().groupBy({ it.tripId }, { it.travelerId })
            segments.all().forEach { row ->
                val segment = Mappers.toSegment(row) ?: return@forEach
                val party = members[segment.tripId].orEmpty().toSet()
                val ids = segment.travelerIds
                // A booking whose people have all left is left alone: making
                // it everyone's would put it on travelers who were never on it.
                if (ids.isEmpty() || (ids intersect party).isEmpty()) return@forEach
                val stored = normaliseParty(ids, party)
                if (stored != ids) segments.upsert(Mappers.toEntity(segment.copyCommon(travelerIds = stored)))
            }
        }
    }

    /**
     * Every booking of every trip with something still to come — what
     * reminders are worked out from. Whole trips rather than only the upcoming
     * bookings, because the time to leave for an airport depends on where the
     * trip is before the flight. Archived trips are put away and stay quiet.
     */
    suspend fun reminderItinerary(nowMillis: Long): List<Segment> {
        val tripIds = segments.startingAfter(nowMillis - DAY_MILLIS).map { it.tripId }.distinct()
        return tripIds
            .filter { trips.find(it)?.archived == false }
            .flatMap { tripId -> segments.forTrip(tripId).mapNotNull(Mappers::toSegment) }
    }

    /** Emits whenever any booking or trip changes. */
    fun observeItineraryChanges(): Flow<Unit> = segments.observeChanges().map { }

    /** The next thing happening across every trip, for the home-screen widget. */
    suspend fun nextAcrossTrips(nowMillis: Long): Pair<Trip, Segment>? {
        val row = segments.nextAcrossTrips(nowMillis) ?: return null
        val segment = Mappers.toSegment(row) ?: return null
        val trip = trips.find(row.tripId)?.let(Mappers::toTrip) ?: return null
        return trip to segment
    }

    /** Whole trips as the archive format carries them: every trip, or the ones named. */
    suspend fun bundles(tripIds: Collection<String>? = null): List<TripArchive.TripBundle> =
        database.withTransaction {
            trips.all()
                .filter { tripIds == null || it.id in tripIds }
                .map { row ->
                    TripArchive.TripBundle(
                        trip = Mappers.toTrip(row),
                        travelers = travelers.forTrip(row.id).map(Mappers::toTraveler),
                        segments = segments.forTrip(row.id).mapNotNull(Mappers::toSegment),
                        ideas = ideas.forTrip(row.id).map(Mappers::toIdea),
                    )
                }
        }

    /** Which of these trips are already on this phone — an import replaces them. */
    suspend fun existingTripIds(tripIds: Collection<String>): Set<String> =
        tripIds.filter { trips.find(it) != null }.toSet()

    /**
     * Write trips from a backup or a shared trip file, all or nothing.
     *
     * A trip is matched by its id, so opening the same file twice, or a newer
     * copy of a trip a friend shared before, replaces it rather than leaving
     * two. Its party, bookings and ideas are replaced wholesale — a booking the
     * sender removed must not survive on the receiving phone. Travelers are
     * matched by id too, so someone on several shared trips stays one person.
     */
    suspend fun importBundles(bundles: List<TripArchive.TripBundle>) {
        if (bundles.isEmpty()) return
        database.withTransaction {
            bundles.forEach { bundle ->
                val tripId = bundle.trip.id
                trips.upsert(Mappers.toEntity(bundle.trip))
                travelers.removeMembers(tripId)
                bundle.travelers.forEachIndexed { position, traveler ->
                    travelers.upsert(Mappers.toEntity(traveler))
                    travelers.addMember(TripMemberEntity(tripId, traveler.id, position))
                }
                val party = bundle.travelers.map { it.id }.toSet()
                segments.deleteForTrip(tripId)
                segments.upsertAll(
                    bundle.segments.map { segment ->
                        Mappers.toEntity(
                            segment.copyCommon(
                                tripId = tripId,
                                travelerIds = normaliseParty(segment.travelerIds, party),
                            )
                        )
                    }
                )
                ideas.deleteForTrip(tripId)
                ideas.upsertAll(bundle.ideas.map { Mappers.toEntity(it.copy(tripId = tripId)) })
            }
            alerts.pruneOrphans()
            travelers.pruneOrphans()
        }
    }

    /** A trip's dates should never exclude something booked inside it. */
    private suspend fun widenTripToFit(tripId: String, saved: List<Segment>) {
        val trip = trips.find(tripId) ?: return
        val start = minOf(trip.startEpochMillis, saved.minOf { it.startEpochMillis })
        val end = maxOf(trip.endEpochMillis, saved.maxOf { it.endEpochMillis })
        if (start != trip.startEpochMillis || end != trip.endEpochMillis) {
            trips.upsert(trip.copy(startEpochMillis = start, endEpochMillis = end))
        }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /** Full 128-bit ids: rows are upserted, so a collision would overwrite silently. */
        fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
    }
}
