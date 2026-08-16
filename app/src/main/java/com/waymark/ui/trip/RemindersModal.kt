package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.Lead
import com.waymark.domain.logic.ReminderCategory
import com.waymark.domain.logic.ReminderPreferences
import com.waymark.domain.logic.TimeText
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.LocalReminderPermission
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * How far ahead of each kind of booking to be told about it.
 *
 * One row per category, each with the same row of lead times, because the
 * question is identical for all four and a control that changes shape between
 * rows would only make it look harder than it is. "Off" is the first choice
 * rather than a separate switch: switching a category off *is* choosing a lead
 * time, and giving it a toggle of its own would mean two controls that can
 * disagree.
 *
 * The permission notice sits at the top rather than beside a row, because it
 * governs all four: Android's answer is one answer for the app.
 */
@Composable
fun RemindersModal(
    preferences: ReminderPreferences,
    onSet: (ReminderCategory, Lead) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Waymark.colors
    val permission = LocalReminderPermission.current

    WaymarkModal(
        title = "Reminders",
        eyebrow = if (preferences.anyOn) "Before it starts" else "All off",
        onDismiss = onDismiss,
    ) {
        if (!permission.granted) {
            NoticeBanner(
                icon = WaymarkIcons.Bell,
                headline = "Android is not letting Waymark notify you",
                detail = "Tap to allow it. Nothing below will fire until you do.",
                onClick = { permission.request() },
            )
            Spacer(Modifier.height(WaymarkSpacing.snug))
        }

        ReminderCategory.entries.forEachIndexed { index, category ->
            if (index > 0) Hairline()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
            ) {
                WaymarkIcon(
                    icon = iconFor(category),
                    tint = if (preferences[category].isOn) colors.accentAmber else colors.textFaint,
                    size = 17.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        style = Waymark.type.bodySmall,
                        color = colors.textBody,
                    )
                    Text(
                        text = category.detail,
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                Text(
                    text = leadLabel(preferences[category]),
                    style = Waymark.type.dataSmall,
                    color = if (preferences[category].isOn) colors.accentAmber else colors.textFaint,
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.snug))

            // Nine choices do not fit across a phone, and wrapping them into
            // three ragged rows per category makes four categories unreadable.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            ) {
                items(Lead.CHOICES.size) { position ->
                    val lead = Lead.CHOICES[position]
                    OptionChip(
                        text = leadLabel(lead),
                        selected = preferences[category] == lead,
                        onClick = { onSet(category, lead) },
                    )
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.snug))
        }
    }
}

private fun leadLabel(lead: Lead): String =
    if (lead.isOn) TimeText.duration(lead.minutes) else "Off"

private fun iconFor(category: ReminderCategory) = when (category) {
    ReminderCategory.FLIGHTS -> WaymarkIcons.Plane
    ReminderCategory.GROUND -> WaymarkIcons.Train
    ReminderCategory.BOOKINGS -> WaymarkIcons.Ticket
    ReminderCategory.STAYS -> WaymarkIcons.Bed
}
