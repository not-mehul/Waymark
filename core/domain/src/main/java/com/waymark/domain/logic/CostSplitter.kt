package com.waymark.domain.logic

import com.waymark.domain.model.ExchangeRate
import com.waymark.domain.model.Expense
import com.waymark.domain.model.ExpenseCategory
import com.waymark.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Who paid, who owes, and the fewest payments that square it.
 *
 * Everything is worked in the smallest unit of the settling currency. An
 * expense in another currency is converted once, at the trip's rate for it,
 * and then split; the cents a split cannot share evenly go one each to the
 * first people in the party, so the shares always add up to what was paid.
 */
object CostSplitter {

    data class Balance(
        val travelerId: String,
        /** What they paid, converted. */
        val paid: Long,
        /** Their part of everything they share. */
        val share: Long,
    ) {
        /** Positive: they are owed. Negative: they owe. */
        val net: Long get() = paid - share
    }

    /** One payment that settles part of the trip. */
    data class Transfer(val from: String, val to: String, val amount: Long)

    data class Summary(
        val currency: String,
        val total: Long,
        val balances: List<Balance>,
        val transfers: List<Transfer>,
        val byCategory: Map<ExpenseCategory, Long>,
        /** Expenses in a currency the trip has no rate for: listed, not counted. */
        val unconverted: List<Expense>,
    ) {
        val settled: Boolean get() = transfers.isEmpty()
    }

    /** [amount] in [settle], or null when there is no rate for its currency. */
    fun convert(amount: Money, settle: String, rates: Map<String, ExchangeRate>): Long? {
        if (amount.currency == settle) return amount.minor
        val rate = rates[amount.currency]?.perUnit?.takeIf { it > 0.0 } ?: return null
        return amount.amount
            .multiply(BigDecimal.valueOf(rate))
            .setScale(Money.digits(settle), RoundingMode.HALF_EVEN)
            .movePointRight(Money.digits(settle))
            .toLong()
    }

    /**
     * The trip's spending, settled in [settle]. [party] is the trip's party in
     * its own order, which decides who carries an odd cent; anyone who paid or
     * shares an expense but has since left the party is still counted.
     */
    fun summarize(
        expenses: List<Expense>,
        party: List<String>,
        settle: String,
        rates: List<ExchangeRate>,
    ): Summary {
        val byCurrency = rates.associateBy { it.currency }
        val paid = mutableMapOf<String, Long>()
        val share = mutableMapOf<String, Long>()
        val byCategory = mutableMapOf<ExpenseCategory, Long>()
        val unconverted = mutableListOf<Expense>()
        var total = 0L

        for (expense in expenses.sortedBy { it.addedAtMillis }) {
            val amount = convert(expense.amount, settle, byCurrency)
            if (amount == null) {
                unconverted += expense
                continue
            }
            val among = (expense.splitAmong.ifEmpty { party.toSet() })
                .sortedBy { id -> party.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
            if (among.isEmpty()) {
                unconverted += expense
                continue
            }
            total += amount
            paid.merge(expense.paidBy, amount, Long::plus)
            byCategory.merge(expense.category, amount, Long::plus)
            val each = amount / among.size
            val left = amount - each * among.size
            among.forEachIndexed { index, id ->
                share.merge(id, each + if (index < left) 1 else 0, Long::plus)
            }
        }

        val everyone = (party + paid.keys + share.keys).distinct()
        val balances = everyone.map { Balance(it, paid[it] ?: 0L, share[it] ?: 0L) }
        return Summary(
            currency = settle,
            total = total,
            balances = balances,
            transfers = settle(balances),
            byCategory = byCategory.toSortedMap(compareBy { it.ordinal }),
            unconverted = unconverted,
        )
    }

    /**
     * The payments that bring every balance to zero: the largest debt paid to
     * the largest credit, repeatedly. At most one fewer payment than there
     * are people, and nobody both pays and is paid.
     */
    fun settle(balances: List<Balance>): List<Transfer> {
        val owing = balances.filter { it.net < 0 }.map { it.travelerId to -it.net }.toMutableList()
        val owed = balances.filter { it.net > 0 }.map { it.travelerId to it.net }.toMutableList()
        val transfers = mutableListOf<Transfer>()
        while (owing.isNotEmpty() && owed.isNotEmpty()) {
            owing.sortByDescending { it.second }
            owed.sortByDescending { it.second }
            val (debtor, debt) = owing.first()
            val (creditor, credit) = owed.first()
            val amount = minOf(debt, credit)
            transfers += Transfer(from = debtor, to = creditor, amount = amount)
            if (debt == amount) owing.removeAt(0) else owing[0] = debtor to debt - amount
            if (credit == amount) owed.removeAt(0) else owed[0] = creditor to credit - amount
        }
        return transfers
    }
}
