package com.waymark.ui.share

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waymark.data.catalog.SampleItinerary
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.repo.TripRepository
import com.waymark.data.share.TripArchive
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What arrives from outside is shown first and written only when confirmed. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShareViewModelTest {

    private lateinit var database: WaymarkDatabase
    private lateinit var trips: TripRepository
    private lateinit var share: ShareViewModel
    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WaymarkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trips = TripRepository(database)
        share = ShareViewModel(trips)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun incoming(): ShareViewModel.Incoming =
        runBlocking { withTimeout(10_000) { share.incoming.filterNotNull().first() } }

    @Test
    fun `an email with a flight in it offers the flight`() {
        share.openText("Your flight BA 286 from SFO to LHR on 14 May 2026 departs 16:20, arrives 10:55.")
        val flights = incoming() as ShareViewModel.Incoming.Flights
        assertEquals("BA286", flights.reading.flights.single().designator)
    }

    @Test
    fun `text with no flight in it says so`() {
        share.openText("Lunch on Thursday?")
        assertTrue(incoming() is ShareViewModel.Incoming.Problem)
    }

    @Test
    fun `a trip code is shown before anything is written, then written once confirmed`() {
        val bundle = TripArchive.TripBundle(sample.trip, sample.travelers, sample.segments, sample.ideas)
        val code = TripArchive.toCode(TripArchive.archiveOf(listOf(bundle)))!!
        share.openText("Open this in Waymark: waymark://import?d=${code.payload}")

        val archive = incoming() as ShareViewModel.Incoming.Archive
        assertEquals(sample.trip.name, archive.bundles.single().trip.name)
        assertTrue(archive.replacing.isEmpty())
        assertTrue(runBlocking { trips.bundles() }.isEmpty())

        var opened: String? = null
        share.importArchive { opened = it }
        runBlocking { withTimeout(10_000) { while (opened == null) kotlinx.coroutines.delay(20) } }
        assertEquals(sample.trip.id, opened)
        assertEquals(1, runBlocking { trips.bundles() }.size)
    }

    @Test
    fun `a damaged code is refused with a reason`() {
        share.openCode("not-a-trip")
        assertTrue(incoming() is ShareViewModel.Incoming.Problem)
    }
}
