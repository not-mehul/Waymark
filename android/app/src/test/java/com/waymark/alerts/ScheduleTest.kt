package com.waymark.alerts

import com.waymark.Spans.MINUTE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject

class ScheduleTest {

    private val hour = 60 * MINUTE_MILLIS
    private val departs = 1_800_000_000_000L
    private val lands = departs + 6 * hour + 5 * MINUTE_MILLIS

    private val reminder = ScheduledReminder(
        signature = "reminder|seg-1|$departs",
        tripId = "trip-1",
        segmentId = "seg-1",
        headline = "BA286 departs in 3h",
        detail = "SFO Terminal I",
        startMillis = departs,
        fireAtMillis = departs - 3 * hour,
        usefulUntilMillis = departs + 1,
    )
    private val flight = JourneyWindow(
        tripId = "trip-1",
        segmentId = "seg-1",
        title = "BA286 · SFO → LHR",
        flight = true,
        showFromMillis = departs - 3 * hour,
        startMillis = departs,
        endMillis = lands,
    )
    private val schedule = Schedule(listOf(reminder), listOf(flight))

    @Test
    fun aReminderIsDueFromItsMomentUntilItIsNoLongerUseful() {
        assertTrue(schedule.due(reminder.fireAtMillis - 1).isEmpty())
        assertEquals(listOf(reminder), schedule.due(reminder.fireAtMillis))
        assertEquals(listOf(reminder), schedule.due(departs))
        assertTrue(schedule.due(departs + 1).isEmpty())
    }

    @Test
    fun theJourneyCountsDownToDepartureThenFillsUntilLanding() {
        assertNull(schedule.journey(departs - 4 * hour))

        val waiting = schedule.journey(departs - 40 * MINUTE_MILLIS)!!
        assertEquals("Departs in 40m", waiting.headline)
        assertEquals("40m", waiting.chip)
        assertEquals(0, waiting.progress)
        assertEquals(departs, waiting.targetMillis)

        val halfway = schedule.journey(departs + (lands - departs) / 2)!!
        assertEquals(LiveJourney.PROGRESS_MAX / 2, halfway.progress)
        assertTrue(halfway.headline.startsWith("Lands in "))
        assertEquals(lands, halfway.targetMillis)

        assertNull(schedule.journey(lands))
    }

    @Test
    fun aTrainArrivesRatherThanLands() {
        val train = flight.copy(flight = false, title = "Eurostar · LON → PAR")
        val journey = Schedule(emptyList(), listOf(train)).journey(departs + MINUTE_MILLIS)!!
        assertTrue(journey.headline.startsWith("Arrives in "))
    }

    @Test
    fun theAlarmIsSetForWhicheverComesFirst() {
        // Long before: the reminder and the window open together, three hours out.
        assertEquals(departs - 3 * hour, schedule.nextAlarmAt(departs - 10 * hour))
        // While the journey shows, every quarter of an hour.
        val now = departs - 2 * hour
        assertEquals(now + LiveJourney.TICK_MILLIS, schedule.nextAlarmAt(now))
        // At departure itself, if that is sooner.
        assertEquals(departs, schedule.nextAlarmAt(departs - 5 * MINUTE_MILLIS))
        // Nothing left: no alarm.
        assertNull(schedule.nextAlarmAt(lands))
    }

    @Test
    fun itReadsWhatThePageSends() {
        val json = JSONObject(
            """
            {"reminders":[{"signature":"${reminder.signature}","tripId":"trip-1","segmentId":"seg-1",
              "moment":"START","label":"BA286","headline":"BA286 departs in 3h","detail":"SFO Terminal I",
              "startEpochMillis":$departs,"fireAtMillis":${reminder.fireAtMillis},"usefulUntilMillis":${departs + 1}},
              {"signature":"broken"}],
             "journeys":[{"tripId":"trip-1","segmentId":"seg-1","title":"BA286 · SFO → LHR","kind":"FLIGHT",
              "showFromMillis":${flight.showFromMillis},"startMillis":$departs,"endMillis":$lands}]}
            """.trimIndent(),
        )
        assertEquals(schedule, Schedule.fromJson(json))
        assertEquals(schedule, Schedule.fromJson(schedule.toJson()))
    }

    @Test
    fun nothingSentIsNothingScheduled() {
        assertEquals(Schedule.EMPTY, Schedule.fromJson(JSONObject()))
    }
}
