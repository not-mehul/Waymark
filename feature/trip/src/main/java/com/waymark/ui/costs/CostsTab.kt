package com.waymark.ui.costs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.CostSplitter
import com.waymark.domain.logic.Measure
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Expense
import com.waymark.domain.model.Money
import com.waymark.ui.charts.BarSeries
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SecondaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * What the trip has cost and who owes whom. Expenses are added as they
 * happen, in whatever currency they were paid; the tab settles them in one.
 */
@Composable
fun CostsTab(model: CostsModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var rateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    var explainingOnline by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.online, state.foreign) { model.refreshRates() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        val summary = state.summary
        fun money(minor: Long) = Money(minor, state.settleCurrency).format()

        if (summary != null && summary.total > 0) {
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large),
                ) {
                    Stat(value = money(summary.total), label = "spent together", emphasised = true)
                    if (state.party.isNotEmpty()) {
                        Stat(value = money(summary.total / state.party.size), label = "a head")
                    }
                }
                if (summary.byCategory.size > 1) {
                    Spacer(Modifier.height(WaymarkSpacing.medium))
                    BarSeries(
                        measures = summary.byCategory.entries.mapIndexed { index, (category, minor) ->
                            Measure(category.label, minor.toDouble(), money(minor), emphasis = index == 0)
                        }.sortedByDescending { it.value },
                    )
                }
            }

            SectionHeader("Settle up")
            Settlement(summary, state, ::money)
        }

        if (summary != null && summary.unconverted.isNotEmpty()) {
            val currencies = summary.unconverted.map { it.amount.currency }.distinct()
            NoticeBanner(
                icon = WaymarkIcons.Info,
                headline = "Not counted yet: ${summary.unconverted.size} in ${currencies.joinToString(", ")}",
                detail = "There is no rate to ${state.settleCurrency} for " +
                    "${if (currencies.size == 1) "it" else "them"}. Tap to type one in.",
                onClick = { rateFor = currencies.first() },
            )
        }

        PrimaryButton(
            text = "Add an expense",
            icon = WaymarkIcons.Plus,
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.party.isNotEmpty(),
        )
        if (state.party.isEmpty() && state.loaded) {
            Footnote("Add somebody to the party first: an expense is paid by someone.")
        }

        SectionHeader("Expenses")
        if (state.expenses.isEmpty()) {
            EmptyLine("Nothing spent yet. Add what was paid, by whom, and for whom.")
        }
        state.expenses.groupBy { it.spentOn }.forEach { (day, onDay) ->
            FieldLabel(TimeText.dayCompact(day))
            onDay.forEach { expense ->
                ExpenseRow(expense, state, onClick = { editingId = expense.id })
            }
        }

        SectionHeader("Currency")
        Panel(modifier = Modifier.fillMaxWidth(), onClick = { choosingCurrency = true }) {
            FieldLabel("Settled in")
            Text(text = state.settleCurrency, style = Waymark.type.data, color = colors.textHeading)
            Text(
                text = "Every expense is converted to this, once, at the rate below.",
                style = Waymark.type.hint,
                color = colors.textDim,
            )
        }
        state.foreign.forEach { currency ->
            val rate = state.rates[currency]
            Panel(modifier = Modifier.fillMaxWidth(), onClick = { rateFor = currency }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "1 $currency = " +
                            if (rate == null) "?" else "${rateText(rate.perUnit)} ${state.settleCurrency}",
                        style = Waymark.type.data,
                        color = if (rate == null) colors.accentAmber else colors.textHeading,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            rate == null -> "tap to set"
                            rate.manual -> "yours"
                            else -> "live · ${TimeText.dayCompact(rate.asOf)}"
                        },
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        state.ratesNote?.let { Footnote(it) }
        if (state.foreign.isNotEmpty()) {
            if (state.online) {
                SecondaryButton(
                    text = if (state.updating) "Updating…" else "Update the rates",
                    onClick = { model.refreshRates(force = true) },
                    enabled = !state.updating,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SecondaryButton(
                    text = "Use live rates",
                    onClick = { explainingOnline = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(WaymarkSpacing.section))
    }

    if (adding || editingId != null) {
        val existing = state.expenses.firstOrNull { it.id == editingId }
        ExpenseModal(
            existing = existing,
            party = state.party,
            settleCurrency = state.settleCurrency,
            currencyOn = model::currencyOn,
            onSave = { draft -> model.save(draft, existing); adding = false; editingId = null },
            onDelete = existing?.let { { model.delete(it.id); editingId = null } },
            onDismiss = { adding = false; editingId = null },
        )
    }

    rateFor?.let { currency ->
        RateModal(
            currency = currency,
            settleCurrency = state.settleCurrency,
            current = state.rates[currency],
            onSave = { model.setRate(currency, it); rateFor = null },
            onClear = { model.clearRate(currency); rateFor = null },
            onDismiss = { rateFor = null },
        )
    }

    if (choosingCurrency) {
        CurrencyModal(
            current = state.settleCurrency,
            suggestions = (listOf(state.settleCurrency, Money.localCurrency()) + state.foreign).distinct(),
            onChoose = { model.settleIn(it); choosingCurrency = false },
            onDismiss = { choosingCurrency = false },
        )
    }

    if (explainingOnline) {
        OnlineExtrasModal(enabled = state.online, onSet = model::setOnline, onDismiss = { explainingOnline = false })
    }
}

@Composable
private fun Settlement(summary: CostSplitter.Summary, state: CostsUiState, money: (Long) -> String) {
    val colors = Waymark.colors
    Panel(modifier = Modifier.fillMaxWidth()) {
        if (summary.transfers.isEmpty()) {
            Text(text = "Everyone has paid their share.", style = Waymark.type.body, color = colors.textBody)
        }
        summary.transfers.forEach { transfer ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.nameOf(transfer.from)} pays ${state.nameOf(transfer.to)}",
                    style = Waymark.type.body,
                    color = colors.textHeading,
                    modifier = Modifier.weight(1f),
                )
                Text(text = money(transfer.amount), style = Waymark.type.data, color = colors.accentAmber)
            }
            Spacer(Modifier.height(WaymarkSpacing.snug))
        }
        Spacer(Modifier.height(WaymarkSpacing.snug))
        summary.balances.filter { it.paid != 0L || it.share != 0L }.forEach { balance ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.nameOf(balance.travelerId),
                    style = Waymark.type.bodySmall,
                    color = colors.textBody,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "paid ${money(balance.paid)} · share ${money(balance.share)}",
                    style = Waymark.type.dataSmall,
                    color = colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, state: CostsUiState, onClick: () -> Unit) {
    val colors = Waymark.colors
    Panel(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.description, style = Waymark.type.cardTitle, color = colors.textHeading)
                Text(
                    text = buildString {
                        append("${state.nameOf(expense.paidBy)} paid · ${expense.category.label}")
                        if (expense.splitAmong.isNotEmpty() && expense.splitAmong.size < state.party.size) {
                            append(" · for ")
                            append(expense.splitAmong.joinToString(", ") { state.nameOf(it) })
                        }
                    },
                    style = Waymark.type.dataSmall,
                    color = colors.textDim,
                )
            }
            Text(text = expense.amount.format(), style = Waymark.type.data, color = colors.textHeading)
        }
    }
}

/** A rate as people read one: four significant figures, no exponent. */
internal fun rateText(perUnit: Double): String =
    java.math.BigDecimal(perUnit).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString()
