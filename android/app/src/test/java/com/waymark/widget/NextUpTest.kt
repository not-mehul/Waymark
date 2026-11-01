package com.waymark.widget

import com.waymark.Spans.MINUTE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import org.json.JSONArray

class NextUpTest {

    private val hour = 60 * MINUTE_MILLIS
    private val now = 1_800_000_000_000L

    private fun entry(id: String, startsIn: Long, lasts: Long = hour) = WidgetEntry(
        tripId = "trip-1",
        tripName = "Lisbon",
        segmentId = id,
        title = "Booking $id",
        startMillis = now + startsIn,
        endMillis = now + startsIn + lasts,
        day = "Thu 14 May",
        clock = "09:40",
        place = "London Heathrow",
    )

    @Test
    fun theNextBookingWithHowLongUntilIt() {
        val next = NextUp.of(listOf(entry("a", 3 * hour)), now).single()
        assertEquals("Next · Lisbon", next.eyebrow)
        assertEquals("Booking a", next.title)
        assertEquals("Thu 14 May, 09:40 · London Heathrow · in 3h", next.detail)
        assertEquals("in 3h", next.soon)
        assertEquals("a", next.segmentId)
    }

    @Test
    fun oneUnderWayIsNow() {
        val next = NextUp.of(listOf(entry("a", -10 * MINUTE_MILLIS)), now).single()
        assertEquals("Now · Lisbon", next.eyebrow)
        assertEquals("Thu 14 May, 09:40 · London Heathrow", next.detail)
    }

    @Test
    fun furtherOffItSaysTheDayAndTime() {
        assertEquals("Thu 14 May 09:40", NextUp.of(listOf(entry("a", 30 * hour)), now).single().soon)
    }

    @Test
    fun endedBookingsDropOffAndAtMostThreeShow() {
        val entries = listOf(entry("old", -3 * hour), entry("d", 9 * hour), entry("b", hour), entry("c", 2 * hour), entry("e", 10 * hour))
        assertEquals(listOf("b", "c", "d"), NextUp.of(entries, now).map { it.segmentId })
    }

    @Test
    fun nothingComingUp() {
        assertEquals(listOf(NextUp.NOTHING), NextUp.of(emptyList(), now))
        assertEquals(listOf(NextUp.NOTHING), NextUp.of(listOf(entry("old", -3 * hour)), now))
    }

    @Test
    fun itReadsWhatThePageSends() {
        val sent = listOf(entry("a", hour), entry("b", 2 * hour))
        val array = JSONArray(sent.map { it.toJson() }).put(JSONArray().put("not an entry"))
        assertEquals(sent, WidgetEntry.listFromJson(array))
    }
}
