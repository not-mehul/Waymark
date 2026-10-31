package com.waymark.ui.costs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.waymark.domain.model.ExchangeRate
import com.waymark.domain.model.Expense
import com.waymark.domain.model.ExpenseCategory
import com.waymark.domain.model.Money
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.DangerButton
import com.waymark.ui.components.DateField
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.util.Currency

/**
 * One expense: what it was, how much and in what, who paid, and who it was
 * for. The currency starts as the one where the trip is that day.
 */
@Composable
fun ExpenseModal(
    existing: Expense?,
    party: List<Traveler>,
    settleCurrency: String,
    currencyOn: (LocalDate) -> String,
    onSave: (ExpenseDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var day by rememberSaveable { mutableStateOf(existing?.spentOn ?: today) }
    var currency by rememberSaveable { mutableStateOf(existing?.amount?.currency ?: currencyOn(today)) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.amount?.toPlainString().orEmpty()) }
    var paidBy by rememberSaveable { mutableStateOf(existing?.paidBy ?: party.firstOrNull()?.id.orEmpty()) }
    var among by rememberSaveable {
        mutableStateOf(existing?.splitAmong?.ifEmpty { null }?.toList() ?: party.map { it.id })
    }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: ExpenseCategory.FOOD) }
    var otherCurrency by rememberSaveable { mutableStateOf(false) }

    val money = Money.parse(amount, currency)
    val valid = description.isNotBlank() && money != null && paidBy.isNotEmpty() && among.isNotEmpty()

    WaymarkModal(
        title = if (existing == null) "Add an expense" else "Edit the expense",
        eyebrow = "Costs",
        onDismiss = onDismiss,
    ) {
        WaymarkTextField(
            value = description,
            onValueChange = { description = it },
            label = "What it was",
            placeholder = "Dinner, the taxi from the station…",
        )

        WaymarkTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "How much, in $currency",
            placeholder = "0.00",
            mono = true,
            keyboardType = KeyboardType.Decimal,
            hint = if (amount.isNotBlank() && money == null) "That is not an amount of $currency." else null,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            (listOf(currencyOn(day), settleCurrency, currency)).distinct().forEach { code ->
                OptionChip(text = code, selected = code == currency && !otherCurrency, onClick = {
                    currency = code
                    otherCurrency = false
                })
            }
            OptionChip(text = "Other", selected = otherCurrency, onClick = { otherCurrency = true })
        }
        if (otherCurrency) {
            var typed by rememberSaveable { mutableStateOf("") }
            WaymarkTextField(
                value = typed,
                onValueChange = { text ->
                    typed = text.uppercase().take(3)
                    if (isCurrency(typed)) currency = typed
                },
                label = "Currency code",
                placeholder = "CHF, JPY, THB…",
                mono = true,
                hint = if (typed.length == 3 && !isCurrency(typed)) "Not a currency code." else null,
            )
        }

        DateField(value = day, onValueChange = { day = it }, label = "Day")

        FieldLabel("Paid by")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            party.forEach { traveler ->
                OptionChip(
                    text = traveler.displayName,
                    selected = traveler.id == paidBy,
                    onClick = { paidBy = traveler.id },
                )
            }
        }

        FieldLabel("For")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            party.forEach { traveler ->
                val selected = traveler.id in among
                OptionChip(
                    text = traveler.displayName,
                    selected = selected,
                    onClick = { among = if (selected) among - traveler.id else among + traveler.id },
                )
            }
        }
        if (among.isEmpty()) {
            Text(text = "Somebody has to share it.", style = Waymark.type.hint, color = Waymark.colors.danger)
        }

        FieldLabel("Kind")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            ExpenseCategory.entries.forEach { kind ->
                OptionChip(text = kind.label, selected = kind == category, onClick = { category = kind })
            }
        }

        PrimaryButton(
            text = "Save",
            enabled = valid,
            onClick = {
                onSave(
                    ExpenseDraft(
                        description = description,
                        amount = requireNotNull(money),
                        paidBy = paidBy,
                        // Everyone ticked is stored as everyone, so a later addition to the party shares it too.
                        splitAmong = among.toSet().takeIf { it != party.map { p -> p.id }.toSet() } ?: emptySet(),
                        spentOn = day,
                        category = category,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        onDelete?.let { delete ->
            DangerButton(text = "Remove", onClick = delete, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A rate typed by hand: what one unit of [currency] is worth in the settling currency. */
@Composable
fun RateModal(
    currency: String,
    settleCurrency: String,
    current: ExchangeRate?,
    onSave: (Double) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current?.perUnit?.let(::rateText).orEmpty()) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    WaymarkModal(title = "1 $currency is worth", eyebrow = "Rate", onDismiss = onDismiss) {
        WaymarkTextField(
            value = text,
            onValueChange = { text = it },
            label = "In $settleCurrency",
            mono = true,
            keyboardType = KeyboardType.Decimal,
            hint = "From a receipt, a card statement or a bureau's board. A rate typed here is kept, " +
                "and a live one never replaces it.",
        )
        PrimaryButton(
            text = "Use this rate",
            enabled = value != null,
            onClick = { value?.let(onSave) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (current?.manual == true) {
            MutedButton(text = "Forget it, use the live rate", onClick = onClear, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The currency a trip is settled in. Changing it means fresh rates for everything. */
@Composable
fun CurrencyModal(current: String, suggestions: List<String>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    WaymarkModal(title = "Settle in", eyebrow = "Currency", onDismiss = onDismiss) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
        ) {
            suggestions.forEach { code ->
                OptionChip(text = code, selected = code == current, onClick = { onChoose(code) })
            }
        }
        WaymarkTextField(
            value = typed,
            onValueChange = { typed = it.uppercase().take(3) },
            label = "Or another",
            placeholder = "USD, GBP, CHF…",
            mono = true,
        )
        PrimaryButton(
            text = "Settle in ${typed.ifBlank { "…" }}",
            enabled = isCurrency(typed),
            onClick = { onChoose(typed) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Rates typed for the old currency are forgotten; live ones are fetched again.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )
    }
}

private fun isCurrency(code: String): Boolean =
    code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess
