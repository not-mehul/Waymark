package com.waymark.ui.trip

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.Instant

/**
 * Who is travelling, what each of them is actually booked on, and — the part
 * group travel gets wrong — the windows where the party is not together.
 */
@Composable
fun PartyTab(
    state: TripUiState,
    onAddTraveler: (String, String?) -> Unit,
    onRemoveTraveler: (String) -> Unit,
    onFilterTraveler: (String?) -> Unit,
) {
    val colors = Waymark.colors
    val dossier = state.dossier
    var adding by remember { mutableStateOf(false) }
    val coverage = remember(dossier) { dossier?.let(PartySplitAnalyzer::coverage).orEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        state.partyNotes.forEach { note ->
            NoticeBanner(icon = WaymarkIcons.Info, headline = note)
        }

        SectionHeader("Travelers")

        coverage.forEach { entry ->
            Panel(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onFilterTraveler(entry.traveler.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
                ) {
                    PartyMark(
                        initials = entry.traveler.initials,
                        active = state.travelerFilter == null ||
                            state.travelerFilter == entry.traveler.id,
                        size = 34.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.traveler.fullName,
                            style = Waymark.type.cardTitle,
                            color = colors.textHeading,
                        )
                        Text(
                            text = buildString {
                                append("${entry.flightCount} flight")
                                if (entry.flightCount != 1) append("s")
                                append(" · ${entry.segmentCount} booking")
                                if (entry.segmentCount != 1) append("s")
                                if (!entry.hasLodging) append(" · no lodging")
                            },
                            style = Waymark.type.dataSmall,
                            color = colors.textDim,
                        )
                    }
                    GhostIconButton(
                        icon = WaymarkIcons.Trash,
                        contentDescription = "Remove ${entry.traveler.displayName}",
                        onClick = { onRemoveTraveler(entry.traveler.id) },
                    )
                }

                if (entry.traveler.seatPreference.name != "NONE" ||
                    entry.traveler.mealPreference != null
                ) {
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    Text(
                        text = listOfNotNull(
                            entry.traveler.seatPreference.name.takeIf { it != "NONE" }
                                ?.lowercase()?.replace('_', ' '),
                            entry.traveler.mealPreference,
                        ).joinToString(" · "),
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }

                if (entry.missingFromSegments.isNotEmpty()) {
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    FieldLabel("Not travelling on")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.missingFromSegments.joinToString(", ") { it.title },
                        style = Waymark.type.hint,
                        color = colors.textFaint,
                    )
                }
            }
        }

        PrimaryButton(
            text = "Add traveler",
            icon = WaymarkIcons.Users,
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(WaymarkSpacing.small))
        SectionHeader("Split itineraries")

        if (state.splits.isEmpty()) {
            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "The party stays together for the whole trip.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        } else {
            state.splits.forEach { window ->
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = TimeText.clock(
                                Instant.ofEpochMilli(window.startMillis)
                                    .atZone(Segment.zoneOrUtc(zoneFor(state, window.startMillis)))
                            ) + " – " + TimeText.clock(
                                Instant.ofEpochMilli(window.endMillis)
                                    .atZone(Segment.zoneOrUtc(zoneFor(state, window.endMillis)))
                            ),
                            style = Waymark.type.data,
                            color = colors.textHeading,
                        )
                        Text(
                            text = TimeText.duration(window.durationMinutes),
                            style = Waymark.type.dataSmall,
                            color = colors.accentAmber,
                        )
                    }
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    window.groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            group.travelerIds.forEach { id ->
                                dossier?.party?.byId(id)?.let {
                                    PartyMark(initials = it.initials, size = 22.dp)
                                }
                            }
                            Text(
                                text = group.label,
                                style = Waymark.type.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    if (window.unaccounted.isNotEmpty()) {
                        Text(
                            text = "Unaccounted: " + window.unaccounted.mapNotNull {
                                dossier?.party?.byId(it)?.displayName
                            }.joinToString(", "),
                            style = Waymark.type.hint,
                            color = colors.danger,
                        )
                    }
                }
            }
        }


        Spacer(Modifier.height(WaymarkSpacing.section))
    }

    if (adding) {
        AddTravelerModal(
            onDismiss = { adding = false },
            onAdd = { name, nickname ->
                onAddTraveler(name, nickname)
                adding = false
            },
        )
    }
}

/** Split windows are clock instants; show them in the zone the party is in. */
private fun zoneFor(state: TripUiState, millis: Long): String =
    state.dossier?.segments
        ?.firstOrNull { millis in it.startEpochMillis..it.endEpochMillis }
        ?.startZoneId
        ?: state.dossier?.trip?.homeZoneId
        ?: "UTC"

@Composable
private fun AddTravelerModal(onDismiss: () -> Unit, onAdd: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }

    WaymarkModal(title = "Add traveler", eyebrow = "Party", onDismiss = onDismiss) {
        WaymarkTextField(
            value = name,
            onValueChange = { name = it },
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
        PrimaryButton(
            text = "Add",
            onClick = { onAdd(name, nickname.ifBlank { null }) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
