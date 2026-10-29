package com.waymark.ui.trip

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.PartySplitAnalyzer
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.ConfirmModal
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.components.WaymarkTextField
import com.waymark.ui.people.TravelerProfileModal
import com.waymark.ui.people.profileSummary
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.Instant

/**
 * Who is travelling, what each of them is actually booked on, and the windows
 * where the party is not together.
 */
@Composable
fun PartyTab(
    state: TripUiState,
    onAddTraveler: (String, String?) -> Unit,
    onAddExisting: (String) -> Unit,
    onUpdateTraveler: (Traveler) -> Unit,
    onRemoveTraveler: (String) -> Unit,
    onFilterTraveler: (String?) -> Unit,
) {
    val colors = Waymark.colors
    val dossier = state.dossier
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingId by rememberSaveable { mutableStateOf<String?>(null) }
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
                        icon = WaymarkIcons.Pencil,
                        contentDescription = "Edit ${entry.traveler.displayName}",
                        onClick = { editingId = entry.traveler.id },
                    )
                    GhostIconButton(
                        icon = WaymarkIcons.Trash,
                        contentDescription = "Remove ${entry.traveler.displayName}",
                        onClick = { removingId = entry.traveler.id },
                    )
                }

                profileSummary(entry.traveler)?.let { summary ->
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    Text(text = summary, style = Waymark.type.hint, color = colors.textDim)
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
        SectionHeader("Apart")

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
                        // Nothing booked in this window is usually somebody at
                        // the hotel, not somebody lost, so it is stated in the
                        // same voice as the rest of the panel.
                        Text(
                            text = "Nothing booked: " + window.unaccounted.mapNotNull {
                                dossier?.party?.byId(it)?.displayName
                            }.joinToString(", "),
                            style = Waymark.type.hint,
                            color = colors.textDim,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(WaymarkSpacing.section))
    }

    if (adding) {
        AddTravelerModal(
            others = state.otherTravelers,
            onDismiss = { adding = false },
            onAdd = { name, nickname ->
                onAddTraveler(name, nickname)
                adding = false
            },
            onAddExisting = { id ->
                onAddExisting(id)
                adding = false
            },
        )
    }

    editingId?.let { id ->
        dossier?.party?.byId(id)?.let { traveler ->
            TravelerProfileModal(
                traveler = traveler,
                onDismiss = { editingId = null },
                onSave = {
                    onUpdateTraveler(it)
                    editingId = null
                },
            )
        }
    }

    removingId?.let { id ->
        val traveler = dossier?.party?.byId(id)
        if (traveler != null) {
            // Bookings nobody else is on cannot stay: there would be no one
            // left on them. Say how many before they go.
            val onlyTheirs = dossier.segments.count { it.travelerIds == setOf(id) }
            ConfirmModal(
                title = "Take ${traveler.displayName} off this trip",
                message = if (onlyTheirs == 0) {
                    "Their name comes off every booking they share with the others."
                } else {
                    val bookings = if (onlyTheirs == 1) {
                        "1 booking only they are on goes"
                    } else {
                        "$onlyTheirs bookings only they are on go"
                    }
                    "$bookings with them, and their name comes off the rest."
                },
                detail = "Their profile stays if they are on another trip.",
                confirmLabel = "Remove",
                onDismiss = { removingId = null },
                onConfirm = {
                    onRemoveTraveler(id)
                    removingId = null
                },
            )
        }
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
private fun AddTravelerModal(
    others: List<Traveler>,
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit,
    onAddExisting: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }

    WaymarkModal(title = "Add traveler", eyebrow = "Party", onDismiss = onDismiss) {
        // Somebody who has travelled with you before comes with their seat,
        // their meal and their loyalty numbers already filled in.
        if (others.isNotEmpty()) {
            FieldLabel("Travelled with you before")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
            ) {
                others.forEach { other ->
                    OptionChip(
                        text = other.displayName,
                        selected = false,
                        onClick = { onAddExisting(other.id) },
                        leading = { PartyMark(initials = other.initials, size = 18.dp) },
                    )
                }
            }
            FieldLabel("Or someone new")
        }
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
