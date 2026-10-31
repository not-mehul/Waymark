package com.waymark.online

import com.waymark.data.local.Mappers
import com.waymark.data.local.OnlineStore
import com.waymark.data.local.WeatherDao
import com.waymark.domain.logic.WeatherPlan
import com.waymark.domain.model.WeatherBasis
import com.waymark.domain.model.WeatherDay
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Daily weather from Open-Meteo, which needs no account and no key: the
 * forecast for the next two weeks, and the record for the same dates a year
 * ago for anything further out.
 */
object OpenMeteo {

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val ARCHIVE = "https://archive-api.open-meteo.com/v1/archive"

    fun forecastUrl(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate): String =
        "$FORECAST?${place(latitude, longitude)}&start_date=$from&end_date=$to&timezone=auto" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"

    fun archiveUrl(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate): String =
        "$ARCHIVE?${place(latitude, longitude)}&start_date=$from&end_date=$to&timezone=auto" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"

    private fun place(latitude: Double, longitude: Double) =
        "latitude=%.2f&longitude=%.2f".format(Locale.ROOT, latitude, longitude)

    /** The days in a reply, dated as the reply dates them. Days with no reading are left out. */
    fun parse(text: String, basis: WeatherBasis): List<WeatherDay> {
        val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IOException("Not a weather reply")
        root["reason"]?.let { throw IOException(it.jsonPrimitive.content) }
        val daily = root["daily"]?.jsonObject ?: throw IOException("No daily weather in the reply")
        val dates = daily["time"]?.jsonArray ?: throw IOException("No dates in the reply")
        fun column(name: String): JsonArray = daily[name]?.jsonArray ?: JsonArray(emptyList())
        val codes = column("weather_code")
        val highs = column("temperature_2m_max")
        val lows = column("temperature_2m_min")
        val chances = column("precipitation_probability_max")
        val sums = column("precipitation_sum")
        return dates.indices.mapNotNull { index ->
            val date = runCatching { LocalDate.parse(dates[index].jsonPrimitive.content) }.getOrNull()
            val code = codes.getOrNull(index).number()?.toInt()
            val high = highs.getOrNull(index).number()
            val low = lows.getOrNull(index).number()
            if (date == null || code == null || high == null || low == null) return@mapNotNull null
            WeatherDay(
                date = date,
                code = code,
                highC = high,
                lowC = low,
                rainChance = chances.getOrNull(index).number()?.toInt(),
                rainMm = sums.getOrNull(index).number(),
                basis = basis,
            )
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.number(): Double? =
        if (this == null || this is JsonNull) {
            null
        } else {
            jsonPrimitive.doubleOrNull ?: jsonPrimitive.intOrNull?.toDouble()
        }
}

/** The weather a trip's stops need: kept on the phone, refreshed when allowed. */
class WeatherRepository(
    private val dao: WeatherDao,
    private val online: OnlineStore,
    private val fetch: Fetch = Http,
) {
    /** A stop and what is known of its days' weather. */
    data class StopWeather(val stop: WeatherPlan.Stop, val days: List<WeatherDay>)

    sealed interface Outcome {
        data object Updated : Outcome

        data object NotAllowed : Outcome

        data class Failed(val reason: String) : Outcome
    }

    fun observe(stops: List<WeatherPlan.Stop>): Flow<List<StopWeather>> {
        val days = stops.flatMap { it.days }
        if (days.isEmpty()) return flowOf(emptyList())
        return dao.observe(stops.map { it.key }.distinct(), days.min().toEpochDay(), days.max().toEpochDay())
            .map { rows ->
                val byKey = rows.groupBy { it.placeKey }
                stops.map { stop ->
                    val wanted = stop.days.toSet()
                    StopWeather(
                        stop,
                        byKey[stop.key].orEmpty()
                            .map(Mappers::toWeather)
                            .filter { it.date in wanted }
                            .sortedBy { it.date },
                    )
                }
            }
    }

    /**
     * Fetch what the stops are missing: a forecast older than [FORECAST_TTL_MILLIS]
     * is fetched again, last year's record only once.
     */
    suspend fun refresh(stops: List<WeatherPlan.Stop>, today: LocalDate, nowMillis: Long): Outcome {
        if (!online.isEnabled()) return Outcome.NotAllowed
        dao.prune(today.toEpochDay())
        return try {
            stops.forEach { stop -> refreshStop(stop, today, nowMillis) }
            Outcome.Updated
        } catch (error: IOException) {
            Outcome.Failed(error.message ?: "No connection")
        } catch (error: IllegalArgumentException) {
            Outcome.Failed("The weather service replied with something unreadable")
        }
    }

    private suspend fun refreshStop(stop: WeatherPlan.Stop, today: LocalDate, nowMillis: Long) {
        val (soon, later) = stop.days.partition { WeatherPlan.basisFor(it, today) == WeatherBasis.FORECAST }
        val cached = dao.forPlace(stop.key, stop.days.min().toEpochDay(), stop.days.max().toEpochDay())
            .associateBy { it.epochDay }

        val staleForecast = soon.any { day ->
            val row = cached[day.toEpochDay()]
            row == null ||
                row.basis != WeatherBasis.FORECAST.name ||
                nowMillis - row.fetchedAtMillis > FORECAST_TTL_MILLIS
        }
        if (staleForecast) {
            val days = OpenMeteo.parse(
                fetch(OpenMeteo.forecastUrl(stop.latitude, stop.longitude, soon.min(), soon.max())),
                WeatherBasis.FORECAST,
            )
            dao.upsert(days.map { Mappers.toEntity(stop.key, it, nowMillis) })
        }

        val missingRecord = later.filter { cached[it.toEpochDay()] == null }
        if (missingRecord.isNotEmpty()) {
            val first = missingRecord.min().minusYears(1)
            val last = missingRecord.max().minusYears(1)
            val record = OpenMeteo.parse(
                fetch(OpenMeteo.archiveUrl(stop.latitude, stop.longitude, first, last)),
                WeatherBasis.LAST_YEAR,
            ).associateBy { it.date }
            // Each trip day takes the reading from a year before it — the 29th
            // of February takes the 28th's.
            val days = missingRecord.mapNotNull { day -> record[day.minusYears(1)]?.copy(date = day) }
            dao.upsert(days.map { Mappers.toEntity(stop.key, it, nowMillis) })
        }
    }

    companion object {
        /** A forecast is fetched again after three hours. */
        const val FORECAST_TTL_MILLIS = 3 * 60 * 60 * 1000L
    }
}
