package com.waymark.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waymark.data.catalog.SampleItinerary
import com.waymark.data.local.Mappers
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.share.TripArchive
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.copyCommon
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The rules the repository keeps, against a real SQLite. */
@RunWith(AndroidJUnit4::class)
class TripRepositoryTest {

    private lateinit var database: WaymarkDatabase
    private lateinit var trips: TripRepository
    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val bundle = TripArchive.TripBundle(sample.trip, sample.travelers, sample.segments, sample.ideas)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WaymarkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trips = TripRepository(database)
        runBlocking { trips.importBundles(listOf(bundle)) }
    }

    @After
    fun tearDown() = database.close()

    private fun dossier() = runBlocking { trips.observeDossier(sample.trip.id).first()!! }

    @Test
    fun `an imported trip reads back whole`() {
        val back = runBlocking { trips.bundles() }.single()
        assertEquals(sample.trip, back.trip)
        assertEquals(sample.travelers.map { it.id }, back.travelers.map { it.id })
        assertEquals(sample.segments.map { it.id }.toSet(), back.segments.map { it.id }.toSet())
        assertEquals(sample.ideas.map { it.id }.toSet(), back.ideas.map { it.id }.toSet())
    }

    @Test
    fun `importing the same trip again replaces it rather than adding a second`() {
        val fewer = bundle.copy(segments = bundle.segments.drop(1))
        runBlocking { trips.importBundles(listOf(fewer)) }
        val back = runBlocking { trips.bundles() }
        assertEquals(1, back.size)
        assertEquals(sample.segments.size - 1, back.single().segments.size)
        assertEquals(setOf(sample.trip.id), runBlocking { trips.existingTripIds(listOf(sample.trip.id, "other")) })
    }

    @Test
    fun `a booking with everyone ticked is stored as everyone`() {
        val everyone = sample.travelers.map { it.id }.toSet()
        val flight = sample.segments.filterIsInstance<Segment.Flight>().first()
        runBlocking { trips.saveSegment(flight.copy(travelerIds = everyone)) }
        assertEquals(emptySet<String>(), dossier().segments.first { it.id == flight.id }.travelerIds)
    }

    @Test
    fun `a booking saved outside the trip's dates widens the trip`() {
        val flight = sample.segments.filterIsInstance<Segment.Flight>().first()
        val later = flight.copy(
            id = "seg-late",
            startEpochMillis = sample.trip.endEpochMillis + 86_400_000L,
            endEpochMillis = sample.trip.endEpochMillis + 2 * 86_400_000L,
        )
        runBlocking { trips.saveSegment(later) }
        assertEquals(later.endEpochMillis, dossier().trip.endEpochMillis)
    }

    @Test
    fun `removing a traveler takes their own bookings and keeps the shared ones`() {
        val leaving = sample.travelers.first()
        val stays = sample.travelers.drop(1).map { it.id }.toSet()
        val alone = sample.segments.first().copyAsOnly(leaving.id, "seg-alone")
        runBlocking {
            trips.saveSegment(alone)
            trips.removeTraveler(sample.trip.id, leaving.id)
        }
        val after = dossier()
        assertTrue(after.segments.none { it.id == "seg-alone" })
        assertTrue(after.party.travelers.none { it.id == leaving.id })
        after.segments.forEach { assertTrue(it.travelerIds.isEmpty() || it.travelerIds.all { id -> id in stays }) }
        // On no other trip, so the profile goes too.
        assertNull(runBlocking { database.travelerDao().find(leaving.id) })
    }

    @Test
    fun `a profile on another trip outlives being taken off this one`() {
        val traveler = sample.travelers.first()
        val second = runBlocking {
            trips.createTrip("Lisbon", "Lisbon", 0L, 86_400_000L, "Europe/Lisbon").also {
                trips.addExistingTraveler(it, traveler.id)
                trips.removeTraveler(sample.trip.id, traveler.id)
            }
        }
        val kept: Traveler? = runBlocking { database.travelerDao().find(traveler.id) }?.let(Mappers::toTraveler)
        assertEquals(traveler.fullName, kept?.fullName)
        assertEquals(1, runBlocking { trips.observeTripCounts().first() }[traveler.id])
        assertTrue(second.isNotEmpty())
    }

    @Test
    fun `deleting a trip removes everything on it`() {
        runBlocking { trips.deleteTrip(sample.trip.id) }
        assertTrue(runBlocking { trips.bundles() }.isEmpty())
        assertTrue(runBlocking { database.segmentDao().all() }.isEmpty())
        assertTrue(runBlocking { database.travelerDao().all() }.isEmpty())
    }

    @Test
    fun `an archived trip stays quiet`() {
        runBlocking { trips.setArchived(sample.trip.id, true) }
        val now = sample.trip.startEpochMillis - 86_400_000L
        assertTrue(runBlocking { trips.reminderItinerary(now) }.isEmpty())
        assertTrue(runBlocking { trips.nextAcrossTrips(now) }.isEmpty())
    }

    @Test
    fun `the widget reads the next few bookings soonest first`() {
        val now = sample.trip.startEpochMillis - 86_400_000L
        val next = runBlocking { trips.nextAcrossTrips(now, limit = 3) }
        assertEquals(3, next.size)
        assertEquals(next.map { it.second.startEpochMillis }.sorted(), next.map { it.second.startEpochMillis })
        assertTrue(next.all { it.first.id == sample.trip.id })
    }

    private fun Segment.copyAsOnly(travelerId: String, id: String): Segment =
        copyCommon(id = id, travelerIds = setOf(travelerId))
}
