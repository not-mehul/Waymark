package com.waymark.ui.costs

import com.waymark.data.catalog.Countries
import com.waymark.data.local.OnlineStore
import com.waymark.data.repo.CostRepository
import com.waymark.data.repo.TripRepository
import com.waymark.domain.logic.CostSplitter
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.Whereabouts
import com.waymark.domain.model.ExchangeRate
import com.waymark.domain.model.Expense
import com.waymark.domain.model.ExpenseCategory
import com.waymark.domain.model.Money
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.domain.model.TripDossier
import com.waymark.online.RatesUpdater
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CostsUiState(
    val loaded: Boolean = false,
    val settleCurrency: String = Money.localCurrency(),
    /** Whether the settling currency was chosen, rather than taken from the phone. */
    val settleChosen: Boolean = false,
    val party: List<Traveler> = emptyList(),
    /** Newest day first. */
    val expenses: List<Expense> = emptyList(),
    val summary: CostSplitter.Summary? = null,
    val rates: Map<String, ExchangeRate> = emptyMap(),
    /** Currencies spent in, other than the settling one. */
    val foreign: List<String> = emptyList(),
    val online: Boolean = false,
    val ratesNote: String? = null,
    val updating: Boolean = false,
) {
    private val names: Map<String, String> = party.associate { it.id to it.displayName }

    fun nameOf(travelerId: String): String = names[travelerId] ?: "Someone no longer on the trip"
}

/** What one expense is, as the sheet fills it in. */
data class ExpenseDraft(
    val description: String,
    val amount: Money,
    val paidBy: String,
    /** Empty for everyone. */
    val splitAmong: Set<String>,
    val spentOn: LocalDate,
    val category: ExpenseCategory,
)

/**
 * The trip's costs, for the Costs tab: the expenses, the balances and the
 * payments that settle them, and the rates behind any conversion.
 */
class CostsModel(
    private val tripId: String,
    private val scope: CoroutineScope,
    dossier: Flow<TripDossier?>,
    private val repository: CostRepository,
    private val updater: RatesUpdater,
    private val online: OnlineStore,
) {
    private val note = MutableStateFlow<String?>(null)
    private val updating = MutableStateFlow(false)
    private var latest: TripDossier? = null

    val state: StateFlow<CostsUiState> = combine(
        dossier,
        repository.observe(tripId),
        online.enabled,
        note,
        updating,
    ) { trip, costs, allowed, ratesNote, busy ->
        latest = trip
        val party = trip?.party?.travelers.orEmpty()
        val settle = costs.settleCurrency ?: Money.localCurrency()
        val rates = costs.rates.associateBy { it.currency }
        CostsUiState(
            loaded = trip != null,
            settleCurrency = settle,
            settleChosen = costs.settleCurrency != null,
            party = party,
            expenses = costs.expenses.sortedWith(
                compareByDescending<Expense> { it.spentOn }.thenByDescending { it.addedAtMillis }
            ),
            summary = CostSplitter.summarize(costs.expenses, party.map { it.id }, settle, costs.rates),
            rates = rates,
            foreign = costs.expenses.map { it.amount.currency }.filter { it != settle }.distinct().sorted(),
            online = allowed,
            ratesNote = ratesNote,
            updating = busy,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), CostsUiState())

    fun save(draft: ExpenseDraft, existing: Expense? = null) {
        val expense = Expense(
            id = existing?.id ?: TripRepository.newId("exp"),
            tripId = tripId,
            description = draft.description.trim(),
            amount = draft.amount,
            paidBy = draft.paidBy,
            splitAmong = draft.splitAmong,
            spentOn = draft.spentOn,
            category = draft.category,
            addedAtMillis = existing?.addedAtMillis ?: System.currentTimeMillis(),
        )
        scope.launch {
            // The first expense fixes the settling currency, so a phone later
            // set to another region does not quietly change what is owed.
            if (!state.value.settleChosen) repository.setSettleCurrency(tripId, state.value.settleCurrency)
            repository.save(expense)
            if (expense.amount.currency != state.value.settleCurrency) refreshRates()
        }
    }

    fun delete(expenseId: String) {
        scope.launch { repository.delete(expenseId) }
    }

    fun settleIn(currency: String) {
        scope.launch {
            repository.setSettleCurrency(tripId, currency)
            note.value = null
            refreshRates()
        }
    }

    fun setRate(currency: String, perUnit: Double) {
        scope.launch { repository.setManualRate(tripId, currency, perUnit, LocalDate.now()) }
    }

    fun clearRate(currency: String) {
        scope.launch {
            repository.clearRate(tripId, currency)
            refreshRates()
        }
    }

    fun setOnline(enabled: Boolean) {
        scope.launch {
            online.set(enabled)
            if (enabled) refreshRates(force = true) else note.value = null
        }
    }

    /**
     * Fetch today's rates when there is a currency without one, or with a
     * live one more than a few days old — the bank publishes on working days,
     * so a weekend's rate is Friday's.
     */
    fun refreshRates(force: Boolean = false) {
        scope.launch {
            val costs = repository.current(tripId)
            val settle = costs.settleCurrency ?: Money.localCurrency()
            val used = costs.expenses.map { it.amount.currency }.toSet() - settle
            val rates = costs.rates.associateBy { it.currency }
            val stale = used.any { currency ->
                val rate = rates[currency]
                rate == null || (!rate.manual && rate.asOf.isBefore(LocalDate.now().minusDays(STALE_DAYS)))
            }
            if (used.isEmpty() || (!force && !stale)) return@launch
            updating.value = true
            note.value = when (val outcome = updater.refresh(tripId, settle, used)) {
                is RatesUpdater.Outcome.Updated -> buildString {
                    append("Rates of ${TimeText.dayCompact(outcome.asOf)}, ")
                    append("as the European Central Bank published them.")
                    if (outcome.missing.isNotEmpty()) {
                        append(" It publishes none for ${outcome.missing.sorted().joinToString(", ")}: type one in.")
                    }
                }

                RatesUpdater.Outcome.NotAllowed -> null

                is RatesUpdater.Outcome.Failed ->
                    "Could not update the rates (${outcome.reason}). The last ones are kept."
            }
            updating.value = false
        }
    }

    /** The currency of wherever the trip is on [date]; the settling one when that is not known. */
    fun currencyOn(date: LocalDate): String {
        val trip = latest ?: return state.value.settleCurrency
        val zone = Segment.zoneOrUtc(trip.trip.homeZoneId)
        val noon = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val place = Whereabouts.at(trip.segments, noon)
        return Countries.of(place)?.currencyCode ?: state.value.settleCurrency
    }

    private companion object {
        const val STALE_DAYS = 3L
    }
}
