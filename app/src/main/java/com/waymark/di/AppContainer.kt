package com.waymark.di

import android.content.Context
import android.util.Log
import com.waymark.data.catalog.AirportDirectory
import com.waymark.data.catalog.Airports
import com.waymark.data.local.ReminderStore
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.repo.AlertRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.SampleSeeder
import com.waymark.data.repo.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hand-rolled composition root.
 *
 * The graph is a dozen objects deep; a dependency-injection framework would
 * add a build step and a code generator to save perhaps thirty lines. This is
 * the whole wiring of the app, readable top to bottom.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: WaymarkDatabase by lazy { WaymarkDatabase.get(appContext) }

    val reminderStore: ReminderStore by lazy { ReminderStore(appContext) }

    val tripRepository: TripRepository by lazy { TripRepository(database) }
    val alertRepository: AlertRepository by lazy { AlertRepository(database) }
    val ideaRepository: IdeaRepository by lazy { IdeaRepository(database) }
    val seeder: SampleSeeder by lazy { SampleSeeder(tripRepository, ideaRepository) }

    /**
     * Read the world directory of stations into memory.
     *
     * Roughly nine thousand rows, half a megabyte, a few hundred milliseconds:
     * worth doing off the main thread, and worth doing at start-up rather than
     * on first keystroke in the airport field. Everything works without it —
     * the curated core list is always present — so a failure here degrades the
     * app rather than stopping it.
     */
    suspend fun loadAirportDirectory() = withContext(Dispatchers.IO) {
        if (Airports.directoryLoaded) return@withContext
        runCatching {
            appContext.assets.open(AirportDirectory.ASSET_NAME).bufferedReader().use { reader ->
                Airports.install(AirportDirectory.parse(reader.lineSequence()))
            }
        }.onFailure { error ->
            Log.w("Waymark", "Station directory unavailable; core list only", error)
        }
    }
}
