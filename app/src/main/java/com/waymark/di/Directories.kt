package com.waymark.di

import android.util.Log
import com.waymark.data.catalog.AirportDirectory
import com.waymark.data.catalog.Airports
import com.waymark.data.catalog.Cities
import com.waymark.data.catalog.CityDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled reference data, read into memory once the app has started. */
object Directories {

    /**
     * Read the world directory of stations into memory.
     *
     * Roughly nine thousand rows, half a megabyte, a few hundred milliseconds:
     * worth doing off the main thread, and worth doing at start-up rather than
     * on first keystroke in the airport field. Everything works without it —
     * the curated core list is always present — so a failure here degrades the
     * app rather than stopping it.
     */
    suspend fun loadAirports() = withContext(Dispatchers.IO) {
        if (Airports.directoryLoaded) return@withContext
        runCatching {
            Airports.install(AirportDirectory.readBundled { AirportDirectory.parse(it) })
        }.onFailure { error ->
            Log.w("Waymark", "Station directory unavailable; core list only", error)
        }
    }

    /**
     * The city gazetteer, read after the stations: it is three times the size
     * and only needed when a plan or an idea is placed, never to open a trip.
     * Without it a typed place keeps the zone the trip is in at the time.
     */
    suspend fun loadCities() = withContext(Dispatchers.IO) {
        if (Cities.loaded) return@withContext
        runCatching {
            Cities.install(CityDirectory.readBundled { CityDirectory.parse(it) })
        }.onFailure { error ->
            Log.w("Waymark", "City gazetteer unavailable; typed places keep the trip's zone", error)
        }
    }
}
