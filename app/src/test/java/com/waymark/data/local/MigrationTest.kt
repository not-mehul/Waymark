package com.waymark.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waymark.data.repo.TripRepository
import com.waymark.domain.model.Segment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every database an earlier build could have left on a phone, opened by this
 * one.
 *
 * Each old schema is written out as that build's tables, filled with a trip,
 * and handed to Room with the app's real migrations. Room then checks the
 * result against today's entities — the same check that runs on a traveler's
 * phone, where a mismatch means the app cannot open at all.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var opened: WaymarkDatabase? = null

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun `the first builds, before the ideas board`() {
        write(1, OldSchema.v1(withIdeas = false, plannedDay = false, documents = false))
        val database = open()
        assertTripSurvived(database, bookedWith = null)
        assertEquals(0, runBlocking { database.ideaDao().forTrip(TRIP).size })
    }

    @Test
    fun `version 1 with ideas that could not yet be planned for a day`() {
        write(1, OldSchema.v1(withIdeas = true, plannedDay = false, documents = false))
        val database = open()
        assertTripSurvived(database, bookedWith = null)
        val idea = runBlocking { database.ideaDao().forTrip(TRIP) }.single()
        // Version 2 folded the six idea kinds into four.
        assertEquals("EAT", idea.kind)
        assertNull(idea.plannedDateEpochDay)
    }

    @Test
    fun `the last version 1, with documents and packing`() {
        write(1, OldSchema.v1(withIdeas = true, plannedDay = true, documents = true))
        val database = open()
        assertTripSurvived(database, bookedWith = null)
        assertEquals(1, runBlocking { database.ideaDao().forTrip(TRIP).size })
    }

    @Test
    fun `version 2, with the vault — only the vendor comes across`() {
        write(2, OldSchema.v2())
        val database = open()
        assertTripSurvived(database, bookedWith = "British Airways")
        val segment = runBlocking { database.segmentDao().find(SEGMENT) }!!
        assertNull(segment.confirmationCode)
    }

    @Test
    fun `early version 3, which still had travel documents`() {
        write(3, OldSchema.v3(documents = true))
        assertTripSurvived(open(), bookedWith = "British Airways", reference = "K7QH2P")
    }

    @Test
    fun `version 3`() {
        write(3, OldSchema.v3(documents = false))
        val database = open()
        assertTripSurvived(database, bookedWith = "British Airways", reference = "K7QH2P")

        // A reminder raised before version 4 was posted when raised, and now
        // knows when its booking starts.
        val alert = runBlocking { database.alertDao().find("reminder:$SEGMENT:$START") }!!
        assertEquals("BA286", alert.label)
        assertTrue(alert.posted)
        assertFalse(alert.acknowledged)
        assertEquals(START, alert.startEpochMillis)
    }

    @Test
    fun `foreign keys hold after the tables they point at are rebuilt`() {
        write(3, OldSchema.v3(documents = false))
        val database = open()
        runBlocking { TripRepository(database).deleteTrip(TRIP) }
        // Deleting the trip cascades to its bookings, party and ideas.
        assertEquals(0, runBlocking { database.segmentDao().all().size })
        assertEquals(0, runBlocking { database.travelerDao().allMembers().size })
        assertEquals(0, runBlocking { database.ideaDao().forTrip(TRIP).size })
    }

    private fun assertTripSurvived(database: WaymarkDatabase, bookedWith: String?, reference: String? = null) {
        runBlocking {
            val bundle = TripRepository(database).bundles().single()
            assertEquals("London", bundle.trip.name)
            assertEquals(listOf("Mara Ellison"), bundle.travelers.map { it.fullName })
            val flight = bundle.segments.single() as Segment.Flight
            assertEquals("BA286", flight.designator)
            assertEquals("LHR", flight.destination.code)
            assertEquals(START, flight.startEpochMillis)
            assertEquals(bookedWith, flight.bookedWith)
            assertEquals(reference, flight.confirmationCode)
        }
    }

    private fun write(version: Int, statements: List<String>) {
        context.deleteDatabase(NAME)
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements.forEach(db::execSQL)
            db.version = version
        }
    }

    private fun open(): WaymarkDatabase =
        Room.databaseBuilder(context, WaymarkDatabase::class.java, NAME)
            .addMigrations(*WaymarkDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also {
                opened = it
                // Opening is lazy; touching the database runs the migrations.
                it.openHelper.writableDatabase
            }

    private companion object {
        const val NAME = "migration-test.db"
        const val TRIP = "trip-1"
        const val SEGMENT = "seg-1"
        const val START = 1_778_800_800_000L
    }
}

/** The tables each earlier version created, and one trip in them. */
private object OldSchema {

    private const val TRIP = "trip-1"
    private const val SEGMENT = "seg-1"
    private const val START = 1_778_800_800_000L
    private const val END = START + 10 * 3_600_000L

    private val place = listOf("Heathrow", "LHR", "London", "United Kingdom", "51.47", "-0.4543", "Europe/London", "")
        .joinToString("\u001F")
    private val origin = listOf("San Francisco International", "SFO", "San Francisco", "United States", "37.6213", "-122.379", "America/Los_Angeles", "")
        .joinToString("\u001F")

    private const val TRIPS =
        "CREATE TABLE `trips` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `destinationSummary` TEXT NOT NULL, " +
            "`startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER NOT NULL, `homeZoneId` TEXT NOT NULL, " +
            "`coverPlace` TEXT, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    private const val TRAVELERS =
        "CREATE TABLE `travelers` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `nickname` TEXT, " +
            "`seatPreference` TEXT NOT NULL, `mealPreference` TEXT, `loyaltyRefs` TEXT, `documentRef` TEXT, " +
            "`contactPhone` TEXT, PRIMARY KEY(`id`))"

    /** Unchanged since the first build, so it must match today's entity exactly. */
    private val MEMBERS = listOf(
        "CREATE TABLE `trip_members` (`tripId` TEXT NOT NULL, `travelerId` TEXT NOT NULL, " +
            "`position` INTEGER NOT NULL, PRIMARY KEY(`tripId`, `travelerId`), " +
            "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`travelerId`) REFERENCES `travelers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX `index_trip_members_travelerId` ON `trip_members` (`travelerId`)",
        "CREATE INDEX `index_trip_members_tripId` ON `trip_members` (`tripId`)",
    )

    private const val DETAIL_BEFORE_TICKETS =
        "`carrierCode` TEXT, `flightNumber` TEXT, `departureTerminal` TEXT, `departureGate` TEXT, " +
            "`arrivalTerminal` TEXT, `arrivalGate` TEXT, `aircraft` TEXT, `cabin` TEXT, `seats` TEXT, "

    private const val DETAIL_AFTER_TICKETS =
        "`operatedBy` TEXT, `propertyName` TEXT, `roomDescription` TEXT, `checkInNote` TEXT, " +
            "`groundMode` TEXT, `provider` TEXT, `pickupInstruction` TEXT, `experienceName` TEXT, " +
            "`category` TEXT, `curatedBy` TEXT, "

    private const val SEGMENT_HEAD =
        "CREATE TABLE `segments` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
            "`startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER NOT NULL, " +
            "`startZoneId` TEXT NOT NULL, `endZoneId` TEXT NOT NULL, `travelerIds` TEXT, "

    private const val SEGMENT_TAIL =
        "PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    /** Versions 1 and 2: a pointer into the vault instead of a reference. */
    private val SEGMENTS_WITH_VAULT = listOf(
        SEGMENT_HEAD + "`reservationId` TEXT, `note` TEXT, `originPlace` TEXT NOT NULL, `destinationPlace` TEXT, " +
            DETAIL_BEFORE_TICKETS + DETAIL_AFTER_TICKETS + SEGMENT_TAIL,
        "CREATE INDEX `index_segments_tripId` ON `segments` (`tripId`)",
        "CREATE INDEX `index_segments_startEpochMillis` ON `segments` (`startEpochMillis`)",
    )

    private val SEGMENTS_V3 = listOf(
        SEGMENT_HEAD + "`confirmationCode` TEXT, `bookedWith` TEXT, `note` TEXT, `originPlace` TEXT NOT NULL, " +
            "`destinationPlace` TEXT, " + DETAIL_BEFORE_TICKETS + "`ticketNumbers` TEXT, " +
            DETAIL_AFTER_TICKETS + SEGMENT_TAIL,
        "CREATE INDEX `index_segments_tripId` ON `segments` (`tripId`)",
        "CREATE INDEX `index_segments_startEpochMillis` ON `segments` (`startEpochMillis`)",
    )

    private fun ideas(plannedDay: Boolean) = listOf(
        "CREATE TABLE `ideas` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `city` TEXT NOT NULL, `place` TEXT, `note` TEXT, `priceBand` TEXT, " +
            "`typicalMinutes` INTEGER, `bestTime` TEXT, " +
            (if (plannedDay) "`plannedDateEpochDay` INTEGER, " else "") +
            "`interestedTravelerIds` TEXT, `status` TEXT NOT NULL, `scheduledSegmentId` TEXT, " +
            "`source` TEXT NOT NULL, `addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX `index_ideas_tripId` ON `ideas` (`tripId`)",
        "CREATE INDEX `index_ideas_status` ON `ideas` (`status`)",
        "INSERT INTO ideas (id, tripId, title, kind, city, status, source, addedAtMillis) " +
            "VALUES ('idea-1', '$TRIP', 'Dishoom', 'DISH', 'London', 'SAVED', 'USER', 1)",
    )

    private const val ALERTS =
        "CREATE TABLE `raised_alerts` (`signature` TEXT NOT NULL, `segmentId` TEXT NOT NULL, " +
            "`designator` TEXT NOT NULL, `headline` TEXT NOT NULL, `detail` TEXT NOT NULL, " +
            "`severity` TEXT NOT NULL, `raisedAtMillis` INTEGER NOT NULL, `acknowledged` INTEGER NOT NULL, " +
            "PRIMARY KEY(`signature`))"

    /** The vault, the passes and the packing list: dropped by version 3. */
    private val VAULT = listOf(
        "CREATE TABLE `reservations` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `segmentId` TEXT, " +
            "`label` TEXT NOT NULL, `vendor` TEXT NOT NULL, `kind` TEXT NOT NULL, `travelerIds` TEXT, " +
            "`secretsSealed` TEXT, `documentUri` TEXT, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE `boarding_passes` (`id` TEXT NOT NULL, `segmentId` TEXT NOT NULL, PRIMARY KEY(`id`))",
    )
    private const val DOCUMENTS =
        "CREATE TABLE `documents` (`id` TEXT NOT NULL, `travelerId` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
            "`label` TEXT NOT NULL, PRIMARY KEY(`id`))"
    private const val PACKING =
        "CREATE TABLE `packing_items` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "PRIMARY KEY(`id`))"
    private const val FLIGHT_STATUS =
        "CREATE TABLE `flight_status` (`segmentId` TEXT NOT NULL, `designator` TEXT NOT NULL, PRIMARY KEY(`segmentId`))"

    private val tripRows = listOf(
        "INSERT INTO trips VALUES ('$TRIP', 'London', 'London and Paris', $START, $END, " +
            "'America/Los_Angeles', 'cover', 0)",
        "INSERT INTO travelers VALUES ('t-1', 'Mara Ellison', NULL, 'WINDOW', NULL, NULL, 'passport-1', NULL)",
        "INSERT INTO trip_members VALUES ('$TRIP', 't-1', 0)",
    )

    private val alertRow =
        "INSERT INTO raised_alerts VALUES ('reminder:$SEGMENT:$START', '$SEGMENT', 'BA286', " +
            "'BA286 in 3h', 'SFO to LHR', 'INFO', ${START - 3 * 3_600_000L}, 0)"

    private fun flightWithVault(reservation: Boolean) = listOfNotNull(
        "INSERT INTO segments (id, tripId, kind, startEpochMillis, endEpochMillis, startZoneId, endZoneId, " +
            "reservationId, originPlace, destinationPlace, carrierCode, flightNumber) VALUES ('$SEGMENT', '$TRIP', " +
            "'FLIGHT', $START, $END, 'America/Los_Angeles', 'Europe/London', 'res-1', '$origin', '$place', 'BA', '286')",
        if (reservation) {
            "INSERT INTO reservations VALUES ('res-1', '$TRIP', '$SEGMENT', 'BA286', 'British Airways', 'FLIGHT', " +
                "NULL, 'sealed', NULL, 1)"
        } else {
            null
        },
    )

    fun v1(withIdeas: Boolean, plannedDay: Boolean, documents: Boolean): List<String> =
        listOf(TRIPS, TRAVELERS) + MEMBERS + SEGMENTS_WITH_VAULT + ALERTS + VAULT + FLIGHT_STATUS +
            (if (documents) listOf(DOCUMENTS, PACKING) else emptyList()) +
            tripRows + flightWithVault(reservation = false) + alertRow +
            (if (withIdeas) ideas(plannedDay) else emptyList())

    fun v2(): List<String> =
        listOf(TRIPS, TRAVELERS) + MEMBERS + SEGMENTS_WITH_VAULT + ALERTS + VAULT + DOCUMENTS + PACKING +
            tripRows + flightWithVault(reservation = true) + alertRow + ideas(plannedDay = true)

    fun v3(documents: Boolean): List<String> =
        listOf(TRIPS, TRAVELERS) + MEMBERS + SEGMENTS_V3 + ALERTS +
            (if (documents) listOf(DOCUMENTS) else emptyList()) +
            tripRows + alertRow + ideas(plannedDay = true) +
            "INSERT INTO segments (id, tripId, kind, startEpochMillis, endEpochMillis, startZoneId, endZoneId, " +
            "confirmationCode, bookedWith, originPlace, destinationPlace, carrierCode, flightNumber) VALUES " +
            "('$SEGMENT', '$TRIP', 'FLIGHT', $START, $END, 'America/Los_Angeles', 'Europe/London', 'K7QH2P', " +
            "'British Airways', '$origin', '$place', 'BA', '286')"
}
