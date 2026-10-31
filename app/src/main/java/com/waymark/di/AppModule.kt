package com.waymark.di

import android.content.Context
import com.waymark.data.local.OnlineStore
import com.waymark.data.local.ReminderStore
import com.waymark.data.local.ThemeStore
import com.waymark.data.local.WaymarkDatabase
import com.waymark.data.repo.AlertRepository
import com.waymark.data.repo.CostRepository
import com.waymark.data.repo.IdeaRepository
import com.waymark.data.repo.SampleSeeder
import com.waymark.data.repo.TripRepository
import com.waymark.online.RatesUpdater
import com.waymark.online.WeatherRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The app's one graph: a database, the stores and repositories over it.
 *
 * The core modules know nothing of Hilt — they are plain classes with plain
 * constructors, as their tests build them — so they are provided here, in
 * the one module that composes the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WaymarkDatabase = WaymarkDatabase.get(context)

    @Provides
    @Singleton
    fun reminderStore(@ApplicationContext context: Context): ReminderStore = ReminderStore(context)

    @Provides
    @Singleton
    fun themeStore(@ApplicationContext context: Context): ThemeStore = ThemeStore(context)

    @Provides
    @Singleton
    fun tripRepository(database: WaymarkDatabase): TripRepository = TripRepository(database)

    @Provides
    @Singleton
    fun alertRepository(database: WaymarkDatabase): AlertRepository = AlertRepository(database)

    @Provides
    @Singleton
    fun ideaRepository(database: WaymarkDatabase): IdeaRepository = IdeaRepository(database)

    @Provides
    @Singleton
    fun costRepository(database: WaymarkDatabase): CostRepository = CostRepository(database)

    /** Whether the app may go online for the weather and the rates: off until the traveler says. */
    @Provides
    @Singleton
    fun onlineStore(@ApplicationContext context: Context): OnlineStore = OnlineStore(context)

    @Provides
    @Singleton
    fun ratesUpdater(costs: CostRepository, online: OnlineStore): RatesUpdater = RatesUpdater(costs, online)

    @Provides
    @Singleton
    fun weatherRepository(database: WaymarkDatabase, online: OnlineStore): WeatherRepository =
        WeatherRepository(database.weatherDao(), online)

    @Provides
    @Singleton
    fun seeder(database: WaymarkDatabase, trips: TripRepository, ideas: IdeaRepository): SampleSeeder =
        SampleSeeder(database, trips, ideas)
}
