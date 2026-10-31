package com.waymark.domain.logic

import com.waymark.domain.model.ExchangeRate
import com.waymark.domain.model.Expense
import com.waymark.domain.model.ExpenseCategory
import com.waymark.domain.model.Money
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostSplitterTest {

    private val day = LocalDate.of(2026, 5, 14)
    private val party = listOf("mara", "julian", "ines")
    private var added = 0L

    private fun spent(
        amount: Long,
        paidBy: String,
        currency: String = "EUR",
        among: Set<String> = emptySet(),
        category: ExpenseCategory = ExpenseCategory.OTHER,
    ) = Expense(
        id = "e${added++}",
        tripId = "t",
        description = "something",
        amount = Money(amount, currency),
        paidBy = paidBy,
        splitAmong = among,
        spentOn = day,
        category = category,
        addedAtMillis = added,
    )

    @Test
    fun `an even split leaves the payer owed by everyone else`() {
        val summary = CostSplitter.summarize(listOf(spent(9_000, "mara")), party, "EUR", emptyList())
        assertEquals(9_000, summary.total)
        assertEquals(listOf(6_000L, -3_000L, -3_000L), summary.balances.map { it.net })
        assertEquals(
            setOf(CostSplitter.Transfer("julian", "mara", 3_000), CostSplitter.Transfer("ines", "mara", 3_000)),
            summary.transfers.toSet(),
        )
    }

    @Test
    fun `the cent that cannot be shared goes to the first in the party, and the shares add up`() {
        val summary = CostSplitter.summarize(listOf(spent(1_000, "julian")), party, "EUR", emptyList())
        assertEquals(listOf(334L, 333L, 333L), summary.balances.map { it.share })
        assertEquals(1_000L, summary.balances.sumOf { it.share })
        assertEquals(0L, summary.balances.sumOf { it.net })
    }

    @Test
    fun `a split among some of the party leaves the others out of it`() {
        val summary = CostSplitter.summarize(
            listOf(spent(4_000, "mara", among = setOf("mara", "ines"))),
            party,
            "EUR",
            emptyList(),
        )
        assertEquals(listOf(2_000L, 0L, -2_000L), summary.balances.map { it.net })
        assertEquals(listOf(CostSplitter.Transfer("ines", "mara", 2_000)), summary.transfers)
    }

    @Test
    fun `everybody paying their share settles with no transfers`() {
        val summary = CostSplitter.summarize(
            listOf(spent(3_000, "mara"), spent(3_000, "julian"), spent(3_000, "ines")),
            party,
            "EUR",
            emptyList(),
        )
        assertTrue(summary.settled)
    }

    @Test
    fun `settling never takes more transfers than people less one`() {
        val expenses = listOf(
            spent(12_000, "mara"),
            spent(2_500, "julian"),
            spent(700, "ines", among = setOf("ines", "julian")),
            spent(5_100, "julian", among = setOf("mara", "julian")),
        )
        val summary = CostSplitter.summarize(expenses, party, "EUR", emptyList())
        assertTrue(summary.transfers.size <= party.size - 1)
        // Applying the transfers brings everyone to zero.
        val after = summary.balances.associate { it.travelerId to it.net }.toMutableMap()
        summary.transfers.forEach {
            after[it.from] = after.getValue(it.from) + it.amount
            after[it.to] = after.getValue(it.to) - it.amount
        }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun `a foreign expense is converted once, at the trip's rate`() {
        val rates = listOf(ExchangeRate("JPY", 0.0062, day, manual = true))
        val summary = CostSplitter.summarize(listOf(spent(12_000, "mara", currency = "JPY")), party, "EUR", rates)
        // ¥12,000 at 0.0062 is €74.40.
        assertEquals(7_440L, summary.total)
    }

    @Test
    fun `an expense with no rate is listed, not counted`() {
        val expense = spent(5_000, "mara", currency = "CHF")
        val summary = CostSplitter.summarize(listOf(expense, spent(3_000, "ines")), party, "EUR", emptyList())
        assertEquals(listOf(expense), summary.unconverted)
        assertEquals(3_000L, summary.total)
    }

    @Test
    fun `somebody who left the party is still owed what they paid`() {
        val summary = CostSplitter.summarize(
            listOf(spent(3_000, "olu", among = setOf("olu", "mara"))),
            party,
            "EUR",
            emptyList(),
        )
        assertEquals(1_500L, summary.balances.single { it.travelerId == "olu" }.net)
        assertEquals(listOf(CostSplitter.Transfer("mara", "olu", 1_500)), summary.transfers)
    }

    @Test
    fun `spending is totalled by category`() {
        val summary = CostSplitter.summarize(
            listOf(
                spent(1_000, "mara", category = ExpenseCategory.FOOD),
                spent(500, "ines", category = ExpenseCategory.FOOD),
                spent(9_000, "julian", category = ExpenseCategory.STAY),
            ),
            party,
            "EUR",
            emptyList(),
        )
        assertEquals(mapOf(ExpenseCategory.STAY to 9_000L, ExpenseCategory.FOOD to 1_500L), summary.byCategory)
    }

    @Test
    fun `typed amounts are read the way people write them`() {
        assertEquals(1_250L, Money.parse("12.5", "EUR")?.minor)
        assertEquals(1_250L, Money.parse("12,50", "EUR")?.minor)
        assertEquals(120_000L, Money.parse("1,200", "EUR")?.minor)
        assertEquals(120_050L, Money.parse("1.200,50", "EUR")?.minor)
        assertEquals(120_050L, Money.parse("1,200.50", "USD")?.minor)
        assertEquals(120_000L, Money.parse("1 200", "EUR")?.minor)
        assertEquals(1_200L, Money.parse("1200", "JPY")?.minor)
        assertEquals(1_250L, Money.parse("1.250", "KWD")?.minor)
        // Three digits after a lone separator group thousands, as much of Europe writes them.
        assertEquals(1_234_500L, Money.parse("12.345", "EUR")?.minor)
        assertNull(Money.parse("12.3456", "EUR"))
        assertNull(Money.parse("0", "EUR"))
        assertNull(Money.parse("-5", "EUR"))
        assertNull(Money.parse("lunch", "EUR"))
        assertNull(Money.parse("12.5", "JPY"))
    }

    @Test
    fun `an amount is written in its currency's own decimals`() {
        assertEquals("€12.50", Money(1_250, "EUR").format(Locale.UK))
        assertEquals("¥1,200", Money(1_200, "JPY").format(Locale.JAPAN).replace('￥', '¥'))
    }
}
