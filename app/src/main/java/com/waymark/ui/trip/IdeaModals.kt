package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.PriceBand
import com.waymark.ui.components.ChoiceCard
import com.waymark.ui.components.DateField
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.StepFlow
import com.waymark.ui.components.StepFooter
import com.waymark.ui.components.StepQuestion
import com.waymark.ui.components.TimeField
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate
import java.time.LocalTime

/**
 * Add something the traveler heard about, one question at a time.
 *
 * What kind, what it is, a few optional details — the address, the price, the
 * best time to go, how long it takes — and, only when a trip touches more
 * than one city, where. The note rides along with the name because "the
 * bookshop off Charing Cross Road" and "Anna said the upstairs room" are one
 * thought.
 */
@Composable
fun AddIdeaModal(
    cities: List<String>,
    onDismiss: () -> Unit,
    onAdd: (IdeaDraft) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var kind by rememberSaveable { mutableStateOf<IdeaKind?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf<PriceBand?>(null) }
    var bestTime by rememberSaveable { mutableStateOf("") }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(cities.firstOrNull().orEmpty()) }

    val needsCity = cities.size > 1
    val steps = listOf("Kind", "What", "Details") + if (needsCity) listOf("Where") else emptyList()
    val last = steps.lastIndex

    fun draft() = IdeaDraft(
        title = title,
        kind = kind ?: IdeaKind.SEE,
        city = city,
        note = note.ifBlank { null },
        address = address,
        priceBand = price,
        bestTime = bestTime.ifBlank { null },
        minutes = minutesText.filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 },
    )

    StepFlow(
        steps = steps,
        index = step,
        title = "Add to the list",
        onStep = { step = it },
        onDismiss = onDismiss,
    ) { current ->
        when (current) {
            0 -> {
                StepQuestion("What sort of thing is it?")
                IdeaKind.entries.forEach { option ->
                    ChoiceCard(
                        title = option.label,
                        detail = option.hint,
                        icon = iconFor(option),
                        selected = kind == option,
                        // Choosing is the answer; there is nothing else on
                        // this step to confirm.
                        onClick = { kind = option; step = 1 },
                    )
                }
            }

            1 -> {
                StepQuestion(
                    when (kind) {
                        IdeaKind.EAT -> "What is it, and where did you hear about it?"
                        IdeaKind.SHOP -> "What are you looking for?"
                        else -> "What is it?"
                    }
                )
                WaymarkTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = when (kind) {
                        IdeaKind.EAT -> "Cacio e pepe at Da Enzo"
                        IdeaKind.SHOP -> "The paper shop near the Pantheon"
                        IdeaKind.DO -> "Cooking class in Trastevere"
                        else -> "Sir John Soane's Museum"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                WaymarkTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Anything worth remembering",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                StepFooter(
                    onBack = { step = 0 },
                    forwardLabel = "Next",
                    forwardEnabled = title.isNotBlank(),
                    onForward = { step = 2 },
                )
            }

            2 -> {
                StepQuestion("Anything else worth knowing?")
                WaymarkTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address",
                    placeholder = "13 Lincoln's Inn Fields",
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel("Price")
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                    PriceBand.entries.forEach { band ->
                        OptionChip(
                            text = band.label,
                            selected = price == band,
                            onClick = { price = if (price == band) null else band },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    WaymarkTextField(
                        value = bestTime,
                        onValueChange = { bestTime = it },
                        label = "Best time",
                        placeholder = "Weekday mornings",
                        modifier = Modifier.weight(2f),
                    )
                    WaymarkTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it },
                        label = "Minutes",
                        placeholder = "90",
                        mono = true,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                StepFooter(
                    onBack = { step = 1 },
                    forwardLabel = if (needsCity) "Next" else "Add",
                    forwardEnabled = title.isNotBlank(),
                    finishing = !needsCity,
                    onForward = { if (needsCity) step = last else onAdd(draft()) },
                )
            }

            else -> {
                StepQuestion("Which city?")
                cities.forEach { option ->
                    ChoiceCard(
                        title = option,
                        detail = "",
                        icon = WaymarkIcons.MapPin,
                        selected = city.equals(option, ignoreCase = true),
                        onClick = { city = option },
                    )
                }
                StepFooter(
                    onBack = { step = 2 },
                    forwardLabel = "Add",
                    forwardEnabled = city.isNotBlank(),
                    finishing = true,
                    onForward = { onAdd(draft()) },
                )
            }
        }
    }
}

/**
 * Promote an idea onto the timeline. Duration is pre-filled from the idea's own
 * estimate, because a museum is not a coffee.
 */
@Composable
fun ScheduleIdeaModal(
    idea: Idea,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSchedule: (LocalDate, LocalTime, Int?) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(defaultDate) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(10, 0)) }
    var minutesText by rememberSaveable { mutableStateOf((idea.typicalMinutes ?: 60).toString()) }

    val minutes = minutesText.filter { it.isDigit() }.toIntOrNull()

    WaymarkModal(title = idea.title, eyebrow = "Put it on the day", onDismiss = onDismiss) {
        Text(
            text = idea.note ?: "It moves to the timeline and off the list.",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            DateField(
                value = date,
                onValueChange = { date = it },
                label = "Date",
                modifier = Modifier.weight(1f),
            )
            TimeField(
                value = time,
                onValueChange = { time = it },
                label = "From",
                modifier = Modifier.weight(1f),
            )
        }
        WaymarkTextField(
            value = minutesText,
            onValueChange = { minutesText = it },
            label = "How long, in minutes",
            placeholder = "90",
            mono = true,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(WaymarkSpacing.tight))
        PrimaryButton(
            text = "Schedule it",
            onClick = { onSchedule(date, time, minutes) },
            modifier = Modifier.fillMaxWidth(),
        )
        MutedButton(text = "Not now", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** The glyph for each kind, shared by the picker and the board. */
fun iconFor(kind: IdeaKind): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    IdeaKind.SEE -> WaymarkIcons.Sights
    IdeaKind.EAT -> WaymarkIcons.Fork
    IdeaKind.DO -> WaymarkIcons.Activity
    IdeaKind.SHOP -> WaymarkIcons.Shop
}
