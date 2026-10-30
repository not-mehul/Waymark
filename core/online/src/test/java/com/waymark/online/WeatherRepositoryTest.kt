package com.waymark.online

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waymark.data.local.OnlineStore
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.WeatherPlan
import com.waymark.domain.model.Place
import com.waymark.domain.model.WeatherBasis
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WaymarkDatabase
    private val online = OnlineStore(context)
    private val asked = mutableListOf<String>()

    private val today = LocalDate.of(2026, 5, 1)
    private val lisbon = Place(name = "Lisbon", city = "Lisbon", country = "PT", latitude = 38.72, longitude = -9.14)

    /** Three days in the forecast's reach, two beyond it. */
    private val stop = WeatherPlan.Stop(
        lisbon,
        listOf(3L, 4L, 5L, 20L, 21L).map { today.plusDays(it) },
    )

    private val fetch = Fetch { url ->
        asked += url
        val archive = "archive-api" in url
        val days = if (archive) stop.days.drop(3).map { it.minusYears(1) } else stop.days.take(3)
        val dates = days.joinToString(",") { "\"$it\"" }
        val numbers = days.joinToString(",") { "20.0" }
        val codes = days.joinToString(",") { "1" }
        val extra = if (archive) "\"precipitation_sum\":[${days.joinToString(",") { "0.0" }}]" else
            "\"precipitation_probability_max\":[${days.joinToString(",") { "10" }}]"
        """{"daily":{"time":[$dates],"weather_code":[$codes],"temperature_2m_max":[$numbers],
            "temperature_2m_min":[$numbers],$extra}}"""
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WaymarkDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
        runBlocking { online.set(false) }
    }

    private fun repository() = WeatherRepository(database.weatherDao(), online, fetch)

    @Test
    fun `with online extras off, nothing is asked of anyone`() = runBlocking {
        online.set(false)
        assertEquals(WeatherRepository.Outcome.NotAllowed, repository().refresh(listOf(stop), today, 0L))
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `the forecast covers the next days and last year the rest, and both are kept`() = runBlocking {
        online.set(true)
        val repository = repository()
        assertEquals(WeatherRepository.Outcome.Updated, repository.refresh(listOf(stop), today, 0L))
        assertEquals(2, asked.size)

        val days = repository.observe(listOf(stop)).first().single().days
        assertEquals(stop.days, days.map { it.date })
        assertEquals(
            listOf(WeatherBasis.FORECAST, WeatherBasis.FORECAST, WeatherBasis.FORECAST, WeatherBasis.LAST_YEAR, WeatherBasis.LAST_YEAR),
            days.map { it.basis },
        )
    }

    @Test
    fun `a fresh forecast and last year's record are not fetched again`() = runBlocking {
        online.set(true)
        val repository = repository()
        repository.refresh(listOf(stop), today, 0L)
        repository.refresh(listOf(stop), today, 60_000L)
        assertEquals(2, asked.size)
        // Three hours on, only the forecast is asked for again.
        repository.refresh(listOf(stop), today, WeatherRepository.FORECAST_TTL_MILLIS + 60_000L)
        assertEquals(3, asked.size)
        assertTrue("archive-api" !in asked.last())
    }
}
