package com.waymark.online

import com.waymark.data.local.OnlineStore
import com.waymark.data.repo.CostRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The day's reference rates, as the European Central Bank publishes them, read
 * through Frankfurter: about thirty currencies, every working day, no key.
 */
data class LiveRates(
    /** Units of each currency per euro; the euro itself is 1. */
    val perEuro: Map<String, Double>,
    val date: LocalDate,
) {
    /**
     * How many units of [settle] one unit of each of [currencies] is worth,
     * for those both sides of which the bank publishes.
     */
    fun perUnitIn(settle: String, currencies: Collection<String>): Map<String, Double> {
        val settlePerEuro = perEuro[settle] ?: return emptyMap()
        return currencies.filter { it != settle }.mapNotNull { currency ->
            perEuro[currency]?.takeIf { it > 0.0 }?.let { currency to settlePerEuro / it }
        }.toMap()
    }

    companion object {
        const val URL = "https://api.frankfurter.dev/v1/latest?base=EUR"

        fun parse(text: String): LiveRates {
            val root = Json.parseToJsonElement(text).jsonObject
            val rates = root["rates"]?.jsonObject ?: throw IOException("No rates in the reply")
            val base = root["base"]?.jsonPrimitive?.content ?: "EUR"
            if (base != "EUR") throw IOException("Rates against $base, not the euro")
            val date = root["date"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: throw IOException("No date in the reply")
            val perEuro = rates.mapValues { (_, value) -> value.jsonPrimitive.double } + ("EUR" to 1.0)
            return LiveRates(perEuro, date)
        }
    }
}

/** Brings a trip's rates up to date, when the traveler has let the app go online. */
class RatesUpdater(
    private val costs: CostRepository,
    private val online: OnlineStore,
    private val fetch: Fetch = Http,
) {
    sealed interface Outcome {
        /** Updated; [missing] are the currencies the bank does not publish. */
        data class Updated(val asOf: LocalDate, val missing: Set<String>) : Outcome

        data object NotAllowed : Outcome

        data class Failed(val reason: String) : Outcome
    }

    suspend fun refresh(tripId: String, settle: String, currencies: Set<String>): Outcome {
        if (!online.isEnabled()) return Outcome.NotAllowed
        val foreign = currencies - settle
        if (foreign.isEmpty()) return Outcome.Updated(LocalDate.now(), emptySet())
        val live = try {
            LiveRates.parse(fetch(LiveRates.URL))
        } catch (error: IOException) {
            return Outcome.Failed(error.message ?: "No connection")
        } catch (error: IllegalArgumentException) {
            // Malformed JSON, or a date that is not one.
            return Outcome.Failed("The rates service replied with something unreadable")
        }
        val perUnit = live.perUnitIn(settle, foreign)
        costs.applyLiveRates(tripId, perUnit, live.date)
        val missing = if (settle !in live.perEuro) foreign else foreign - perUnit.keys
        return Outcome.Updated(live.date, missing)
    }
}
