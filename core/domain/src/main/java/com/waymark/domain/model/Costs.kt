package com.waymark.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/**
 * An amount of one currency, held in its smallest unit — cents, pence, yen —
 * so adding up a trip never meets a binary fraction.
 */
data class Money(val minor: Long, val currency: String) {

    /** The amount as a decimal, in whole units. */
    val amount: BigDecimal get() = BigDecimal.valueOf(minor, digits(currency))

    /** "€12.50", "¥1,200", in the reader's own conventions. */
    fun format(locale: Locale = Locale.getDefault()): String = runCatching {
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(this@Money.currency)
            maximumFractionDigits = digits(this@Money.currency)
            minimumFractionDigits = digits(this@Money.currency)
        }.format(amount)
    }.getOrElse { "${amount.toPlainString()} ${this.currency}" }

    operator fun plus(other: Money): Money {
        require(other.currency == currency) { "Adding $currency to ${other.currency}" }
        return Money(minor + other.minor, currency)
    }

    companion object {
        /** How many decimal places [currency] is written with: 2 for EUR, 0 for JPY, 3 for KWD. */
        fun digits(currency: String): Int =
            runCatching { Currency.getInstance(currency).defaultFractionDigits }
                .getOrDefault(2)
                .coerceAtLeast(0)

        /**
         * What a person typed, read as an amount of [currency]: "12.5", "12,50",
         * "1 200", "1,200.00". Null for anything that is not a positive amount
         * with no more decimals than the currency has.
         */
        fun parse(text: String, currency: String): Money? {
            val cleaned = text.trim().filterNot { it == ' ' || it == '\u00A0' || it == '\'' }
            if (cleaned.isEmpty()) return null
            val places = digits(currency)
            val decimal = decimalSeparator(cleaned, places)
            val whole = if (decimal == null) cleaned else cleaned.substringBeforeLast(decimal)
            val fraction = if (decimal == null) "" else cleaned.substringAfterLast(decimal)
            val normalised = whole.filterNot { it == ',' || it == '.' } + if (decimal == null) "" else ".$fraction"
            val value = normalised.toBigDecimalOrNull() ?: return null
            if (value.signum() <= 0 || value.scale() > places) return null
            return Money(value.movePointRight(places).setScale(0, RoundingMode.UNNECESSARY).toLong(), currency)
        }

        /**
         * Which of '.' and ',' marks the decimals in [text], if either does.
         * With both, the later one. With one, used once, it is the decimal
         * mark unless exactly three digits follow it in a currency that does
         * not have three decimals — "1,200" is twelve hundred, "12,50" twelve
         * and a half. Used more than once, it groups thousands.
         */
        private fun decimalSeparator(text: String, places: Int): Char? {
            val dot = text.lastIndexOf('.')
            val comma = text.lastIndexOf(',')
            if (dot >= 0 && comma >= 0) return if (dot > comma) '.' else ','
            val mark = when {
                dot >= 0 -> '.'
                comma >= 0 -> ','
                else -> return null
            }
            if (text.count { it == mark } > 1) return null
            val after = text.length - text.lastIndexOf(mark) - 1
            return if (after == 3 && places != 3) null else mark
        }

        /** The currency of where the phone is set, or euros when it has none. */
        fun localCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "EUR"
    }
}

enum class ExpenseCategory(val label: String) {
    TRAVEL("Travel"),
    STAY("Stays"),
    FOOD("Food and drink"),
    ACTIVITY("Things to do"),
    SHOPPING("Shopping"),
    OTHER("Other"),
}

/**
 * Something one traveler paid for and the party shares.
 *
 * [splitAmong] names who shares it; empty means the whole party as it is
 * now, so somebody added to the trip later shares the hotel too, and
 * somebody taken off stops sharing what they were never on.
 */
data class Expense(
    val id: String,
    val tripId: String,
    val description: String,
    val amount: Money,
    val paidBy: String,
    val splitAmong: Set<String> = emptySet(),
    val spentOn: LocalDate,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val addedAtMillis: Long = 0L,
)

/** How many units of the trip's settling currency one unit of [currency] is worth. */
data class ExchangeRate(
    val currency: String,
    val perUnit: Double,
    val asOf: LocalDate,
    /** Typed by the traveler, and so never replaced by a live rate. */
    val manual: Boolean,
)

/** A trip's spending: what was spent, what it is settled in, and at what rates. */
data class TripCosts(
    /** Null until somebody chooses; the phone's own currency is offered. */
    val settleCurrency: String? = null,
    val expenses: List<Expense> = emptyList(),
    val rates: List<ExchangeRate> = emptyList(),
) {
    companion object {
        val EMPTY = TripCosts()
    }
}
