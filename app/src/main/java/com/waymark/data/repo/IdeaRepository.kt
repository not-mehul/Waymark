package com.waymark.data.repo

import androidx.room.withTransaction
import com.waymark.data.catalog.DestinationGuide
import com.waymark.data.catalog.DestinationGuide.toIdea
import com.waymark.data.local.Mappers
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.IdeaBoard
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

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

    suspend fun delete(ideaId: String) = ideas.delete(ideaId)

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
    ): Idea {
        val idea = Idea(
            id = newId(),
            tripId = tripId,
            title = title.trim(),
            kind = kind,
            city = city.trim(),
            place = place,
            note = note?.trim()?.ifBlank { null },
            typicalMinutes = typicalMinutes,
            interestedTravelerIds = interestedTravelerIds,
        )
        save(idea)
        return idea
    }

    /** Take a bundled suggestion onto the trip's own list. */
    suspend fun adopt(suggestion: Idea): Idea {
        val saved = suggestion.copy(id = newId(), addedAtMillis = System.currentTimeMillis())
        save(saved)
        return saved
    }

    suspend fun setStatus(ideaId: String, status: IdeaStatus) {
        val existing = find(ideaId) ?: return
        save(
            existing.copy(
                status = status,
                // Un-scheduling releases the link; the segment is dealt with
                // separately by whoever removed it.
                scheduledSegmentId = if (status == IdeaStatus.SCHEDULED) {
                    existing.scheduledSegmentId
                } else {
                    null
                },
            )
        )
    }

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
            segments.upsert(Mappers.toEntity(segment))
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

    /**
     * Bundled suggestions for the cities this trip actually visits, minus
     * anything already on the list. Ids here are provisional; [adopt] mints a
     * real one when the traveler takes it.
     */
    fun suggestionsFor(tripId: String, cities: Collection<String>, existing: List<Idea>): List<Idea> {
        val offered = cities
            .distinct()
            .flatMap { city ->
                DestinationGuide.forCity(city).map { entry ->
                    entry.toIdea(
                        id = "suggestion-${city.lowercase()}-${entry.title.hashCode()}",
                        tripId = tripId,
                        city = city,
                    )
                }
            }
        return IdeaBoard.unseenSuggestions(offered, existing)
    }

    private fun newId(): String = "idea-${UUID.randomUUID().toString().take(8)}"
}
