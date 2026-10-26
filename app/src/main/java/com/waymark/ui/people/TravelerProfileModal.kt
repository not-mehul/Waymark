package com.waymark.ui.people

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.waymark.domain.model.SeatPreference
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.WaymarkSpacing

/**
 * One person's standing details: what goes on every booking they are on.
 *
 * A profile belongs to the person, not the trip — the same Mara is on the
 * London trip and the Lisbon one — so an edit here shows up everywhere she
 * travels. Seat and meal preference used to be displayed and never settable;
 * loyalty numbers and a phone number were stored and never shown.
 */
@Composable
fun TravelerProfileModal(
    traveler: Traveler,
    onDismiss: () -> Unit,
    onSave: (Traveler) -> Unit,
) {
    var fullName by rememberSaveable(traveler.id) { mutableStateOf(traveler.fullName) }
    var nickname by rememberSaveable(traveler.id) { mutableStateOf(traveler.nickname.orEmpty()) }
    var seat by rememberSaveable(traveler.id) { mutableStateOf(traveler.seatPreference) }
    var meal by rememberSaveable(traveler.id) { mutableStateOf(traveler.mealPreference.orEmpty()) }
    var loyalty by rememberSaveable(traveler.id) { mutableStateOf(traveler.loyaltyRefs.joinToString("\n")) }
    var phone by rememberSaveable(traveler.id) { mutableStateOf(traveler.contactPhone.orEmpty()) }

    WaymarkModal(title = traveler.displayName, eyebrow = "Profile", onDismiss = onDismiss) {
        WaymarkTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full name",
            placeholder = "As printed on the passport",
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = "Known as",
            placeholder = "Optional",
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("Seat")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
        ) {
            SeatPreference.entries.forEach { option ->
                OptionChip(
                    text = seatLabel(option),
                    selected = seat == option,
                    onClick = { seat = option },
                )
            }
        }

        WaymarkTextField(
            value = meal,
            onValueChange = { meal = it },
            label = "Meal",
            placeholder = "Vegetarian, no nuts",
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = loyalty,
            onValueChange = { loyalty = it },
            label = "Loyalty numbers, one per line",
            placeholder = "BA Executive Club 12345678",
            singleLine = false,
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
        WaymarkTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            placeholder = "+44 20 7946 0000",
            mono = true,
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            MutedButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "Save",
                icon = WaymarkIcons.Check,
                enabled = fullName.isNotBlank(),
                onClick = {
                    onSave(
                        traveler.copy(
                            fullName = fullName.trim(),
                            nickname = nickname.trim().ifBlank { null },
                            seatPreference = seat,
                            mealPreference = meal.trim().ifBlank { null },
                            loyaltyRefs = loyalty.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            contactPhone = phone.trim().ifBlank { null },
                        )
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

fun seatLabel(preference: SeatPreference): String = when (preference) {
    SeatPreference.NONE -> "No preference"
    SeatPreference.WINDOW -> "Window"
    SeatPreference.AISLE -> "Aisle"
    SeatPreference.EXIT_ROW -> "Exit row"
    SeatPreference.BULKHEAD -> "Bulkhead"
}

/** "Window · Vegetarian · BA 12345678" — the standing details in one line. */
fun profileSummary(traveler: Traveler): String? = listOfNotNull(
    traveler.seatPreference.takeIf { it != SeatPreference.NONE }?.let(::seatLabel),
    traveler.mealPreference,
    traveler.loyaltyRefs.firstOrNull()?.let { first ->
        if (traveler.loyaltyRefs.size > 1) "$first +${traveler.loyaltyRefs.size - 1}" else first
    },
    traveler.contactPhone,
).joinToString(" · ").ifBlank { null }
