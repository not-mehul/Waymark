package com.waymark.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startEpochMillis")
    suspend fun all(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observe(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun find(tripId: String): TripEntity?

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("UPDATE trips SET archived = :archived WHERE id = :tripId")
    suspend fun setArchived(tripId: String, archived: Boolean)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun delete(tripId: String)
}

@Dao
interface TravelerDao {

    @Query("SELECT * FROM travelers ORDER BY fullName")
    fun observeAll(): Flow<List<TravelerEntity>>

    @Query("SELECT * FROM travelers ORDER BY fullName")
    suspend fun all(): List<TravelerEntity>

    @Query(
        """
        SELECT travelers.* FROM travelers
        INNER JOIN trip_members ON trip_members.travelerId = travelers.id
        WHERE trip_members.tripId = :tripId
        ORDER BY trip_members.position, travelers.fullName
        """
    )
    fun observeForTrip(tripId: String): Flow<List<TravelerEntity>>

    @Query(
        """
        SELECT travelers.* FROM travelers
        INNER JOIN trip_members ON trip_members.travelerId = travelers.id
        WHERE trip_members.tripId = :tripId
        ORDER BY trip_members.position, travelers.fullName
        """
    )
    suspend fun forTrip(tripId: String): List<TravelerEntity>

    @Query("SELECT * FROM travelers WHERE id = :travelerId")
    suspend fun find(travelerId: String): TravelerEntity?

    @Upsert
    suspend fun upsert(traveler: TravelerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: TripMemberEntity)

    @Query("SELECT * FROM trip_members")
    suspend fun allMembers(): List<TripMemberEntity>

    @Query("DELETE FROM trip_members WHERE tripId = :tripId AND travelerId = :travelerId")
    suspend fun removeMember(tripId: String, travelerId: String)

    /** Empty a trip's party, ahead of writing an imported one in its place. */
    @Query("DELETE FROM trip_members WHERE tripId = :tripId")
    suspend fun removeMembers(tripId: String)

    @Query("SELECT COUNT(*) FROM trip_members WHERE tripId = :tripId")
    suspend fun memberCount(tripId: String): Int

    /** How many trips a traveler is on — zero means the profile is an orphan. */
    @Query("SELECT COUNT(*) FROM trip_members WHERE travelerId = :travelerId")
    suspend fun tripCountFor(travelerId: String): Int

    /** Per traveler, the number of trips they are on, for the people list. */
    @Query("SELECT travelerId, COUNT(*) AS trips FROM trip_members GROUP BY travelerId")
    fun observeTripCounts(): Flow<List<TravelerTripCount>>

    @Query("DELETE FROM travelers WHERE id = :travelerId")
    suspend fun delete(travelerId: String)

    /** Profiles on no trip at all — left behind by an older build's removal. */
    @Query("DELETE FROM travelers WHERE id NOT IN (SELECT travelerId FROM trip_members)")
    suspend fun pruneOrphans()
}

data class TravelerTripCount(val travelerId: String, val trips: Int)

@Dao
interface SegmentDao {

    @Query("SELECT * FROM segments WHERE tripId = :tripId ORDER BY startEpochMillis")
    fun observeForTrip(tripId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE tripId = :tripId ORDER BY startEpochMillis")
    suspend fun forTrip(tripId: String): List<SegmentEntity>

    @Query("SELECT * FROM segments ORDER BY startEpochMillis")
    suspend fun all(): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE id = :segmentId")
    suspend fun find(segmentId: String): SegmentEntity?

    /** Everything that starts inside a window — the reminder check's read. */
    @Query(
        """
        SELECT * FROM segments
        WHERE startEpochMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY startEpochMillis
        """
    )
    suspend fun startingInWindow(fromMillis: Long, toMillis: Long): List<SegmentEntity>

    /** Everything still to start, for working out when the next reminder falls due. */
    @Query("SELECT * FROM segments WHERE startEpochMillis > :fromMillis ORDER BY startEpochMillis")
    suspend fun startingAfter(fromMillis: Long): List<SegmentEntity>

    /** The next booking across every trip, for the home-screen widget. */
    @Query(
        """
        SELECT segments.* FROM segments
        INNER JOIN trips ON trips.id = segments.tripId
        WHERE segments.endEpochMillis >= :nowMillis AND trips.archived = 0
        ORDER BY segments.startEpochMillis
        LIMIT 1
        """
    )
    suspend fun nextAcrossTrips(nowMillis: Long): SegmentEntity?

    /**
     * Ticks whenever a booking or a trip changes — what tells the reminder
     * alarm to look again. The value itself is not used.
     */
    @Query("SELECT COUNT(*) FROM segments INNER JOIN trips ON trips.id = segments.tripId")
    fun observeChanges(): Flow<Int>

    @Upsert
    suspend fun upsert(segment: SegmentEntity)

    @Upsert
    suspend fun upsertAll(segments: List<SegmentEntity>)

    @Query("DELETE FROM segments WHERE id = :segmentId")
    suspend fun delete(segmentId: String)

    @Query("DELETE FROM segments WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: String)
}

@Dao
interface IdeaDao {

    @Query("SELECT * FROM ideas WHERE tripId = :tripId ORDER BY addedAtMillis")
    fun observeForTrip(tripId: String): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE tripId = :tripId ORDER BY addedAtMillis")
    suspend fun forTrip(tripId: String): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE id = :ideaId")
    suspend fun find(ideaId: String): IdeaEntity?

    @Upsert
    suspend fun upsert(idea: IdeaEntity)

    @Upsert
    suspend fun upsertAll(ideas: List<IdeaEntity>)

    @Query("DELETE FROM ideas WHERE id = :ideaId")
    suspend fun delete(ideaId: String)

    @Query("DELETE FROM ideas WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: String)

    @Query("UPDATE ideas SET plannedDateEpochDay = :epochDay WHERE id = :ideaId")
    suspend fun setPlannedDay(ideaId: String, epochDay: Long?)

    /** When a scheduled segment is removed the idea returns to the list. */
    @Query(
        """
        UPDATE ideas SET status = 'SAVED', scheduledSegmentId = NULL
        WHERE scheduledSegmentId = :segmentId
        """
    )
    suspend fun releaseSegment(segmentId: String)
}

@Dao
interface AlertDao {

    @Query("SELECT * FROM raised_alerts ORDER BY raisedAtMillis DESC LIMIT 50")
    fun observeRecent(): Flow<List<RaisedAlertEntity>>

    @Query("SELECT * FROM raised_alerts WHERE signature = :signature")
    suspend fun find(signature: String): RaisedAlertEntity?

    @Upsert
    suspend fun upsert(alert: RaisedAlertEntity)

    @Query("UPDATE raised_alerts SET posted = 1 WHERE signature = :signature")
    suspend fun markPosted(signature: String)

    @Query("UPDATE raised_alerts SET acknowledged = 1 WHERE segmentId = :segmentId")
    suspend fun acknowledgeFor(segmentId: String)

    @Query("DELETE FROM raised_alerts WHERE raisedAtMillis < :beforeMillis")
    suspend fun prune(beforeMillis: Long)

    /** Alerts about segments that no longer exist; this table has no foreign key. */
    @Query("DELETE FROM raised_alerts WHERE segmentId NOT IN (SELECT id FROM segments)")
    suspend fun pruneOrphans()
}
