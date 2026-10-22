package com.waymark.data.repo

import androidx.room.withTransaction
import com.waymark.data.local.Mappers
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.copyCommon
import com.waymark.domain.model.normaliseParty
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The list of things a trip wants to do, and the bridge from that list onto
 * the timeline.
 */
class IdeaRepository(
    private val database: WaymarkDatabase,
) {

    private val ideas = database.ideaDao()
    private val segments = database.segmentDao()

    fun observe(tripId: String): Flow<List<Idea>> =
        ideas.observeForTrip(tripId).map { rows -> rows.map(Mappers::toIdea) }

    suspend fun find(ideaId: String): Idea? = ideas.find(ideaId)?.let(Mappers::toIdea)

    suspend fun save(idea: Idea) = ideas.upsert(Mappers.toEntity(idea))

    suspend fun saveAll(items: List<Idea>) = ideas.upsertAll(items.map(Mappers::toEntity))

    /** Returns what was removed, so the screen can offer it back. */
    suspend fun delete(ideaId: String): Idea? {
        val existing = find(ideaId) ?: return null
        ideas.delete(ideaId)
        return existing
    }

    /** Add something the traveler typed rather than something the guide offered. */
    suspend fun add(
        tripId: String,
        title: String,
        kind: IdeaKind,
        city: String,
        note: String?,
        place: Place? = null,
        typicalMinutes: Int? = null,
        interestedTravelerIds: Set<String> = emptySet(),
        priceBand: com.waymark.domain.model.PriceBand? = null,
        bestTime: String? = null,
    ): Idea {
        val idea = Idea(
            id = TripRepository.newId("idea"),
            tripId = tripId,
            title = title.trim(),
            kind = kind,
            city = city.trim(),
            place = place,
            note = note?.trim()?.ifBlank { null },
            typicalMinutes = typicalMinutes,
            interestedTravelerIds = interestedTravelerIds,
            priceBand = priceBand,
            bestTime = bestTime?.trim()?.ifBlank { null },
        )
        save(idea)
        return idea
    }

    /**
     * Done, not yet, or set aside. An idea that is on the timeline stays tied
     * to its booking through all of it: marking it done does not orphan the
     * booking, and "not yet" puts it back on the timeline where it still is,
     * rather than on the list as though it had never been scheduled.
     */
    suspend fun setStatus(ideaId: String, status: IdeaStatus) {
        val existing = find(ideaId) ?: return
        val linked = existing.scheduledSegmentId?.takeIf { segments.find(it) != null }
        val next = if (status == IdeaStatus.SAVED && linked != null) IdeaStatus.SCHEDULED else status
        save(existing.copy(status = next, scheduledSegmentId = linked))
    }

    /** Pencil an idea in for a day, or rub the day out. */
    suspend fun setPlannedDay(ideaId: String, date: LocalDate?) =
        ideas.setPlannedDay(ideaId, date?.toEpochDay())

    suspend fun setInterest(ideaId: String, travelerId: String) {
        val existing = find(ideaId) ?: return
        val next = existing.interestedTravelerIds.toMutableSet()
        if (!next.add(travelerId)) next.remove(travelerId)
        save(existing.copy(interestedTravelerIds = next))
    }

    /**
     * Promote an idea onto the timeline. The segment and the idea's new state
     * are written together: an idea marked scheduled with no segment behind it
     * would be a lie the traveler acts on.
     */
    suspend fun schedule(
        idea: Idea,
        date: LocalDate,
        startTime: LocalTime,
        zone: ZoneId,
        travelerIds: Set<String>,
        minutesOverride: Int? = null,
    ): String {
        val segment = IdeaBoard.schedule(
            idea = idea,
            segmentId = TripRepository.newId("seg"),
            date = date,
            startTime = startTime,
            zone = zone,
            travelerIds = travelerIds,
            minutesOverride = minutesOverride,
        )
        database.withTransaction {
            val party = database.travelerDao().forTrip(idea.tripId).map { it.id }.toSet()
            segments.upsert(
                Mappers.toEntity(
                    segment.copyCommon(travelerIds = normaliseParty(segment.travelerIds, party))
                )
            )
            ideas.upsert(
                Mappers.toEntity(
                    idea.copy(
                        status = IdeaStatus.SCHEDULED,
                        scheduledSegmentId = segment.id,
                    )
                )
            )
        }
        return segment.id
    }

    /** A removed segment returns its idea to the list rather than deleting it. */
    suspend fun releaseSegment(segmentId: String) = ideas.releaseSegment(segmentId)
}
