package com.waymark.domain.logic

import com.waymark.data.catalog.DestinationGuide
import com.waymark.data.catalog.DestinationGuide.toIdea
import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IdeaBoardTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val ideas = bundle.ideas
    private val london = ZoneId.of("Europe/London")

    @Test
    fun `sections group by kind and keep a stable order`() {
        val sections = IdeaBoard.sections(ideas)
        assertTrue(sections.isNotEmpty())
        assertEquals(sections.map { it.kind }.distinct(), sections.map { it.kind })
        sections.forEach { section ->
            assertTrue(section.ideas.all { it.kind == section.kind })
        }
        // Sights come before food, food before walks: the board does not reshuffle.
        val order = sections.map { it.kind }
        assertTrue(order.indexOf(IdeaKind.SIGHT) < order.indexOf(IdeaKind.DISH))
    }

    @Test
    fun `open items sort above finished ones`() {
        val mixed = listOf(
            idea("z-done", IdeaKind.SIGHT, IdeaStatus.DONE),
            idea("a-saved", IdeaKind.SIGHT, IdeaStatus.SAVED),
            idea("m-scheduled", IdeaKind.SIGHT, IdeaStatus.SCHEDULED),
        )
        val titles = IdeaBoard.sections(mixed).single().ideas.map { it.title }
        assertEquals(listOf("a-saved", "m-scheduled", "z-done"), titles)
    }

    @Test
    fun `dismissed ideas stay out of the way unless asked for`() {
        val withDismissed = ideas + idea("Not this trip", IdeaKind.SIGHT, IdeaStatus.DISMISSED)
        val visible = IdeaBoard.sections(withDismissed).flatMap { it.ideas }
        assertTrue(visible.none { it.status == IdeaStatus.DISMISSED })
        val all = IdeaBoard.sections(withDismissed, includeDismissed = true).flatMap { it.ideas }
        assertTrue(all.any { it.status == IdeaStatus.DISMISSED })
    }

    @Test
    fun `filtering by traveler keeps ideas nobody has claimed`() {
        val julian = bundle.travelers.first { it.fullName.startsWith("Julian") }
        val mara = bundle.travelers.first { it.fullName.startsWith("Mara") }
        val forJulian = IdeaBoard.sections(ideas, travelerFilter = julian.id).flatMap { it.ideas }

        // Claimed by Julian: kept. Claimed by Mara alone: dropped. Unclaimed: kept.
        assertTrue(forJulian.any { it.id == "idea-canal" })
        assertTrue(forJulian.none { it.id == "idea-rodin" })
        assertTrue(forJulian.any { it.interestedTravelerIds.isEmpty() })
        assertTrue(IdeaBoard.sections(ideas, travelerFilter = mara.id).flatMap { it.ideas }
            .any { it.id == "idea-rodin" })
    }

    @Test
    fun `the tally counts each status once`() {
        val tally = IdeaBoard.tally(ideas)
        assertEquals(ideas.count { it.status == IdeaStatus.SAVED }, tally.saved)
        assertEquals(ideas.count { it.status == IdeaStatus.DONE }, tally.done)
        assertEquals(ideas.size, tally.total + tally.dismissed)
    }

    @Test
    fun `suggestions exclude anything already on the list, in any state`() {
        val offered = DestinationGuide.forCity("London").map { entry ->
            with(DestinationGuide) { entry.toIdea("s-${entry.title}", "trip-sample", "London") }
        }
        val unseen = IdeaBoard.unseenSuggestions(offered, ideas)

        // Saved, and done, are both already taken up.
        assertTrue(unseen.none { it.title == "The Wallace Collection" })
        assertTrue(unseen.none { it.title == "A proper Sunday roast" })
        // Something not on the trip's list is still offered.
        assertTrue(unseen.any { it.title == "Sky Garden" })
    }

    @Test
    fun `scheduling produces a segment that carries the idea forward`() {
        val idea = ideas.first { it.id == "idea-wallace" }
        val segment = IdeaBoard.schedule(
            idea = idea,
            segmentId = "seg-new",
            date = LocalDate.of(2026, 5, 17),
            startTime = LocalTime.of(10, 30),
            zone = london,
            travelerIds = setOf("trav-mara"),
        )

        assertEquals(idea.title, segment.name)
        assertEquals(idea.note, segment.note)
        assertEquals("Europe/London", segment.startZoneId)
        assertEquals(10, segment.start.hour)
        // The guide says ninety minutes for this one.
        assertEquals(90, ((segment.endEpochMillis - segment.startEpochMillis) / 60_000L).toInt())
        assertEquals(setOf("trav-mara"), segment.travelerIds)
    }

    @Test
    fun `an idea with no estimate gets an hour, and an override wins`() {
        val vague = idea("Something", IdeaKind.ACTIVITY, IdeaStatus.SAVED)
        val default = IdeaBoard.schedule(
            vague, "s1", LocalDate.of(2026, 5, 17), LocalTime.NOON, london, emptySet(),
        )
        assertEquals(60, ((default.endEpochMillis - default.startEpochMillis) / 60_000L).toInt())

        val overridden = IdeaBoard.schedule(
            vague, "s2", LocalDate.of(2026, 5, 17), LocalTime.NOON, london, emptySet(),
            minutesOverride = 25,
        )
        assertEquals(25, ((overridden.endEpochMillis - overridden.startEpochMillis) / 60_000L).toInt())
    }

    @Test
    fun `scheduling falls back to the idea's own interest list for travelers`() {
        val claimed = idea("Walk", IdeaKind.WALK, IdeaStatus.SAVED)
            .copy(interestedTravelerIds = setOf("trav-julian"))
        val segment = IdeaBoard.schedule(
            claimed, "s3", LocalDate.of(2026, 5, 17), LocalTime.NOON, london, emptySet(),
        )
        assertEquals(setOf("trav-julian"), segment.travelerIds)
    }

    @Test
    fun `nearby finds saved ideas within reach and orders them by distance`() {
        val bloomsbury = Place(
            name = "Hotel", city = "London",
            latitude = 51.5205, longitude = -0.1265, timeZoneId = "Europe/London",
        )
        val near = IdeaBoard.nearby(ideas, bloomsbury, withinKm = 5.0)
        assertTrue(near.isNotEmpty())
        assertEquals(near.sortedBy { it.second }, near)
        assertTrue(near.all { it.second <= 5.0 })
        assertTrue(near.all { it.first.status == IdeaStatus.SAVED })
        // A dish has no coordinates and cannot be "near" anything.
        assertTrue(near.none { it.first.kind == IdeaKind.DISH && it.first.place == null })
    }

    @Test
    fun `fitsIn respects the time available including getting there`() {
        val bloomsbury = Place(
            name = "Hotel", city = "London",
            latitude = 51.5205, longitude = -0.1265, timeZoneId = "Europe/London",
        )
        val roomy = IdeaBoard.fitsIn(ideas, bloomsbury, availableMinutes = 240)
        val cramped = IdeaBoard.fitsIn(ideas, bloomsbury, availableMinutes = 20)
        assertTrue(roomy.size >= cramped.size)
        assertTrue(cramped.all { (it.typicalMinutes ?: 60) <= 20 })
    }

    private fun idea(title: String, kind: IdeaKind, status: IdeaStatus) = Idea(
        id = title,
        tripId = "trip-sample",
        title = title,
        kind = kind,
        city = "London",
        status = status,
    )
}
