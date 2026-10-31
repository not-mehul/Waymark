package com.waymark.data.repo

import androidx.room.withTransaction
import com.waymark.data.local.Mappers
import com.waymark.data.local.TripCostsEntity
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.model.ExchangeRate
import com.waymark.domain.model.Expense
import com.waymark.domain.model.TripCosts
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * What a trip costs: its expenses, the currency they are settled in, and the
 * rates they are converted at. A rate the traveler typed is theirs; a live
 * rate fetched later never replaces it.
 */
class CostRepository(private val database: WaymarkDatabase) {

    private val costs = database.costDao()

    fun observe(tripId: String): Flow<TripCosts> = combine(
        costs.observeSettings(tripId),
        costs.observeExpenses(tripId),
        costs.observeRates(tripId),
    ) { settings, expenses, rates ->
        TripCosts(
            settleCurrency = settings?.settleCurrency,
            expenses = expenses.map(Mappers::toExpense),
            rates = rates.map(Mappers::toRate),
        )
    }

    suspend fun save(expense: Expense) {
        costs.upsertExpense(Mappers.toEntity(expense))
    }

    suspend fun delete(expenseId: String) {
        costs.deleteExpense(expenseId)
    }

    /**
     * Settle in [currency] from now on. Every rate the trip had was a price
     * in the old currency, so they go — live ones are fetched again, and
     * the traveler is asked again for any they typed.
     */
    suspend fun setSettleCurrency(tripId: String, currency: String) {
        database.withTransaction {
            if (costs.settings(tripId)?.settleCurrency == currency) return@withTransaction
            costs.upsertSettings(TripCostsEntity(tripId, currency))
            costs.deleteRatesFor(tripId)
        }
    }

    suspend fun setManualRate(tripId: String, currency: String, perUnit: Double, today: LocalDate) {
        costs.upsertRates(listOf(Mappers.toEntity(tripId, ExchangeRate(currency, perUnit, today, manual = true))))
    }

    /** Back to the live rate, or to none until one is fetched. */
    suspend fun clearRate(tripId: String, currency: String) {
        costs.deleteRate(tripId, currency)
    }

    /**
     * Rates fetched for [tripId], as units of its settling currency per unit
     * of each currency. Rates typed by hand are left as they are.
     */
    suspend fun applyLiveRates(tripId: String, perUnit: Map<String, Double>, asOf: LocalDate) {
        database.withTransaction {
            val manual = costs.rates(tripId).filter { it.manual }.map { it.currency }.toSet()
            costs.upsertRates(
                perUnit.filterKeys { it !in manual }.map { (currency, rate) ->
                    Mappers.toEntity(tripId, ExchangeRate(currency, rate, asOf, manual = false))
                }
            )
        }
    }

    /** The trip's costs, read once, for an export. */
    suspend fun current(tripId: String): TripCosts = database.withTransaction {
        TripCosts(
            settleCurrency = costs.settings(tripId)?.settleCurrency,
            expenses = costs.expenses(tripId).map(Mappers::toExpense),
            rates = costs.rates(tripId).map(Mappers::toRate),
        )
    }
}
