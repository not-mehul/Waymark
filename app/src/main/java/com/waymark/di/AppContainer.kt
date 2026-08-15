package com.waymark.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.waymark.BuildConfig
import com.waymark.data.local.SecretCipher
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.remote.FlightStatusProvider
import com.waymark.data.remote.HttpFlightStatusProvider
import com.waymark.data.remote.OfflineFlightStatusProvider
import com.waymark.data.repo.FlightRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.PreparationRepository
import com.waymark.data.repo.SampleSeeder
import com.waymark.data.repo.TripRepository
import com.waymark.data.repo.VaultRepository

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

    val cipher: SecretCipher by lazy { SecretCipher() }

    /**
     * Order matters: the live feed is preferred when it has a key and a
     * network, and the offline model backstops it. The offline model is never
     * absent, so a lookup always resolves.
     */
    val flightProviders: List<FlightStatusProvider> by lazy {
        listOf(
            HttpFlightStatusProvider(
                apiKey = BuildConfig.FLIGHT_API_KEY,
                baseUrl = BuildConfig.FLIGHT_API_BASE,
                isOnline = ::isOnline,
            ),
            OfflineFlightStatusProvider(),
        )
    }

    val tripRepository: TripRepository by lazy { TripRepository(database, cipher) }
    val vaultRepository: VaultRepository by lazy { VaultRepository(database, cipher) }
    val flightRepository: FlightRepository by lazy { FlightRepository(database, flightProviders) }
    val ideaRepository: IdeaRepository by lazy { IdeaRepository(database) }
    val preparationRepository: PreparationRepository by lazy {
        PreparationRepository(database, cipher)
    }
    val seeder: SampleSeeder by lazy { SampleSeeder(tripRepository, vaultRepository, ideaRepository, preparationRepository) }

    private fun isOnline(): Boolean {
        val manager = appContext.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
