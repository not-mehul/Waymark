package com.waymark.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE archived = 0 ORDER BY startEpochMillis")
    fun observeActive(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observe(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun find(tripId: String): TripEntity?

    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun delete(tripId: String)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int
}

@Dao
interface TravelerDao {

    @Query("SELECT * FROM travelers ORDER BY fullName")
    fun observeAll(): Flow<List<TravelerEntity>>

    @Query(
        """
        SELECT travelers.* FROM travelers
        INNER JOIN trip_members ON trip_members.travelerId = travelers.id
        WHERE trip_members.tripId = :tripId
        ORDER BY trip_members.position, travelers.fullName
        """
    )
    fun observeForTrip(tripId: String): Flow<List<TravelerEntity>>

    @Upsert
    suspend fun upsert(traveler: TravelerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: TripMemberEntity)

    @Query("DELETE FROM trip_members WHERE tripId = :tripId AND travelerId = :travelerId")
    suspend fun removeMember(tripId: String, travelerId: String)

    @Query("SELECT COUNT(*) FROM trip_members WHERE tripId = :tripId")
    suspend fun memberCount(tripId: String): Int

    @Query("DELETE FROM travelers WHERE id = :travelerId")
    suspend fun delete(travelerId: String)
}

@Dao
interface SegmentDao {

    @Query("SELECT * FROM segments WHERE tripId = :tripId ORDER BY startEpochMillis")
    fun observeForTrip(tripId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE id = :segmentId")
    fun observe(segmentId: String): Flow<SegmentEntity?>

    @Query("SELECT * FROM segments WHERE id = :segmentId")
    suspend fun find(segmentId: String): SegmentEntity?

    @Query(
        """
        SELECT * FROM segments
        WHERE kind = 'FLIGHT' AND endEpochMillis > :fromMillis AND startEpochMillis < :toMillis
        ORDER BY startEpochMillis
        """
    )
    suspend fun flightsInWindow(fromMillis: Long, toMillis: Long): List<SegmentEntity>

    @Upsert
    suspend fun upsert(segment: SegmentEntity)

    @Upsert
    suspend fun upsertAll(segments: List<SegmentEntity>)

    @Query("DELETE FROM segments WHERE id = :segmentId")
    suspend fun delete(segmentId: String)
}

@Dao
interface ReservationDao {

    @Query("SELECT * FROM reservations WHERE tripId = :tripId ORDER BY updatedAtMillis DESC")
    fun observeForTrip(tripId: String): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE id = :id")
    suspend fun find(id: String): ReservationEntity?

    @Query("SELECT * FROM reservations WHERE segmentId = :segmentId LIMIT 1")
    suspend fun findForSegment(segmentId: String): ReservationEntity?

    @Upsert
    suspend fun upsert(reservation: ReservationEntity)

    @Query("DELETE FROM reservations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BoardingPassDao {

    @Query("SELECT * FROM boarding_passes WHERE tripId = :tripId ORDER BY boardingTimeMillis")
    fun observeForTrip(tripId: String): Flow<List<BoardingPassEntity>>

    @Query("SELECT * FROM boarding_passes WHERE segmentId = :segmentId")
    fun observeForSegment(segmentId: String): Flow<List<BoardingPassEntity>>

    @Query("SELECT * FROM boarding_passes WHERE id = :id")
    suspend fun find(id: String): BoardingPassEntity?

    @Upsert
    suspend fun upsert(pass: BoardingPassEntity)

    @Query("DELETE FROM boarding_passes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface IdeaDao {

    @Query("SELECT * FROM ideas WHERE tripId = :tripId ORDER BY addedAtMillis")
    fun observeForTrip(tripId: String): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :ideaId")
    suspend fun find(ideaId: String): IdeaEntity?

    @Query("SELECT * FROM ideas WHERE tripId = :tripId AND status = :status")
    suspend fun findByStatus(tripId: String, status: String): List<IdeaEntity>

    @Upsert
    suspend fun upsert(idea: IdeaEntity)

    @Upsert
    suspend fun upsertAll(ideas: List<IdeaEntity>)

    @Query("DELETE FROM ideas WHERE id = :ideaId")
    suspend fun delete(ideaId: String)

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
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY expiresOnEpochDay IS NULL, expiresOnEpochDay")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE travelerId IN (:travelerIds)")
    fun observeFor(travelerIds: List<String>): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun find(id: String): DocumentEntity?

    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PackingDao {

    @Query("SELECT * FROM packing_items WHERE tripId = :tripId ORDER BY addedAtMillis")
    fun observeForTrip(tripId: String): Flow<List<PackingItemEntity>>

    @Query("SELECT * FROM packing_items WHERE id = :id")
    suspend fun find(id: String): PackingItemEntity?

    @Upsert
    suspend fun upsert(item: PackingItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<PackingItemEntity>)

    @Query("UPDATE packing_items SET packed = :packed WHERE id = :id")
    suspend fun setPacked(id: String, packed: Boolean)

    @Query("UPDATE packing_items SET packed = 0 WHERE tripId = :tripId")
    suspend fun unpackAll(tripId: String)

    @Query("DELETE FROM packing_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AlertDao {

    @Query("SELECT * FROM raised_alerts ORDER BY raisedAtMillis DESC LIMIT 50")
    fun observeRecent(): Flow<List<RaisedAlertEntity>>

    @Query("SELECT * FROM raised_alerts WHERE signature = :signature")
    suspend fun find(signature: String): RaisedAlertEntity?

    @Upsert
    suspend fun upsert(alert: RaisedAlertEntity)

    @Query("UPDATE raised_alerts SET acknowledged = 1 WHERE segmentId = :segmentId")
    suspend fun acknowledgeFor(segmentId: String)

    @Query("DELETE FROM raised_alerts WHERE raisedAtMillis < :beforeMillis")
    suspend fun prune(beforeMillis: Long)
}
