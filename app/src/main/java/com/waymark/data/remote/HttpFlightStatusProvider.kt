package com.waymark.data.remote

import android.util.Log
import com.waymark.domain.model.FlightState
import com.waymark.domain.model.FlightStatus
import com.waymark.domain.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Optional live provider. Off unless a key is supplied at build time, which
 * keeps the app fully functional — and completely offline — by default.
 *
 * The shape here follows AviationStack's `/flights` response; swapping in
 * another vendor means changing [parse] and the URL, nothing else.
 */
class HttpFlightStatusProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val isOnline: () -> Boolean,
) : FlightStatusProvider {

    override val sourceName: String = "Live feed"

    override suspend fun isAvailable(): Boolean = apiKey.isNotBlank() && isOnline()

    override suspend fun fetch(flight: Segment.Flight): FlightStatus? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        val url = URL(
            "$baseUrl/flights?access_key=$apiKey&flight_iata=${flight.designator}&limit=1"
        )
        runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body, flight)
            } finally {
                connection.disconnect()
            }
        }.onFailure { error ->
            Log.w(TAG, "Live status lookup failed for ${flight.designator}", error)
        }.getOrNull()
    }

    private fun parse(body: String, flight: Segment.Flight): FlightStatus? {
        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        val record = data.optJSONObject(0) ?: return null
        val departure = record.optJSONObject("departure")
        val arrival = record.optJSONObject("arrival")

        val scheduledDeparture = departure?.timestamp("scheduled") ?: flight.startEpochMillis
        val estimatedDeparture = departure?.timestamp("estimated")
            ?: departure?.timestamp("actual")
            ?: scheduledDeparture
        val scheduledArrival = arrival?.timestamp("scheduled") ?: flight.endEpochMillis
        val estimatedArrival = arrival?.timestamp("estimated")
            ?: arrival?.timestamp("actual")
            ?: scheduledArrival

        return FlightStatus(
            segmentId = flight.id,
            designator = flight.designator,
            state = stateOf(record.optString("flight_status", "")),
            scheduledDepartureMillis = scheduledDeparture,
            estimatedDepartureMillis = estimatedDeparture,
            scheduledArrivalMillis = scheduledArrival,
            estimatedArrivalMillis = estimatedArrival,
            departureTerminal = departure?.text("terminal") ?: flight.departureTerminal,
            departureGate = departure?.text("gate") ?: flight.departureGate,
            arrivalTerminal = arrival?.text("terminal") ?: flight.arrivalTerminal,
            arrivalGate = arrival?.text("gate") ?: flight.arrivalGate,
            baggageBelt = arrival?.text("baggage"),
            boardingMillis = null,
            position = null,
            progressPercent = 0,
            observedAtMillis = System.currentTimeMillis(),
            source = sourceName,
        )
    }

    private fun stateOf(raw: String): FlightState = when (raw.lowercase()) {
        "scheduled" -> FlightState.SCHEDULED
        "active" -> FlightState.EN_ROUTE
        "landed" -> FlightState.LANDED
        "cancelled", "canceled" -> FlightState.CANCELLED
        "incident", "diverted" -> FlightState.DIVERTED
        else -> FlightState.UNKNOWN
    }

    private fun JSONObject.text(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.timestamp(key: String): Long? = text(key)?.let { value ->
        try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (error: DateTimeParseException) {
            null
        }
    }

    private companion object {
        const val TAG = "WaymarkFlights"
    }
}
