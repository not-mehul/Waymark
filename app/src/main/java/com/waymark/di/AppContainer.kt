package com.waymark.di

import android.content.Context
import com.waymark.data.local.SecretCipher
import com.waymark.data.local.WaymarkDatabase
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

    val tripRepository: TripRepository by lazy { TripRepository(database, cipher) }
    val vaultRepository: VaultRepository by lazy { VaultRepository(database, cipher) }
    val flightRepository: FlightRepository by lazy { FlightRepository(database) }
    val ideaRepository: IdeaRepository by lazy { IdeaRepository(database) }
    val preparationRepository: PreparationRepository by lazy {
        PreparationRepository(database, cipher)
    }
    val seeder: SampleSeeder by lazy { SampleSeeder(tripRepository, vaultRepository, ideaRepository, preparationRepository) }
}
