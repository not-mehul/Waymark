package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The unscheduled half of the trip: places to see, food to try, walks to take.
 *
 * Nothing here has a time on it, which is the point — a trip is not only its
 * reservations. Anything on the list can be promoted onto the timeline, at
 * which point it becomes an ordinary booking and stops asking for attention.
 */
@Composable
fun IdeasTab(
    state: TripUiState,
    onAdopt: (Idea) -> Unit,
    onSetStatus: (String, IdeaStatus) -> Unit,
    onToggleInterest: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSchedule: (Idea) -> Unit,
    onAddIdea: (String, IdeaKind, String, String?) -> Unit,
) {
    val colors = Waymark.colors
    var composing by remember { mutableStateOf(false) }
    var showingSuggestions by remember { mutableStateOf(true) }
    val party = state.dossier?.party?.travelers.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = WaymarkSpacing.screenBottom),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large),
                modifier = Modifier.padding(bottom = WaymarkSpacing.snug),
            ) {
                Stat(value = state.ideaTally.saved.toString(), label = "On the list")
                Stat(
                    value = state.ideaTally.scheduled.toString(),
                    label = "Scheduled",
                    emphasised = true,
                )
                Stat(value = state.ideaTally.done.toString(), label = "Done")
            }
        }

        state.ideaSections.forEach { section ->
            item(key = "head-${section.kind.name}") {
                SectionHeader(section.kind.heading)
            }
            items(section.ideas, key = { it.id }) { idea ->
                IdeaCard(
                    idea = idea,
                    party = party,
                    onSetStatus = onSetStatus,
                    onToggleInterest = onToggleInterest,
                    onDelete = onDelete,
                    onSchedule = onSchedule,
                )
            }
        }

        if (state.ideaSections.isEmpty()) {
            item {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nothing on the list yet.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    Text(
                        text = "Take something from the suggestions below, or add your own.",
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(WaymarkSpacing.small))
            PrimaryButton(
                text = "Add an idea",
                icon = WaymarkIcons.Plus,
                onClick = { composing = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.suggestions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(WaymarkSpacing.medium))
                SectionHeader(
                    label = "Suggestions",
                    trailing = {
                        GhostIconButton(
                            icon = if (showingSuggestions) {
                                WaymarkIcons.ChevronDown
                            } else {
                                WaymarkIcons.ChevronRight
                            },
                            contentDescription = if (showingSuggestions) "Hide" else "Show",
                            onClick = { showingSuggestions = !showingSuggestions },
                        )
                    },
                )
            }

            if (showingSuggestions) {
                items(state.suggestions, key = { it.id }) { suggestion ->
                    SuggestionCard(suggestion = suggestion, onAdopt = onAdopt)
                }
                item {
                    EditorialNote(
                        term = "Bundled",
                        body = "Suggestions ship with the app for the cities on this trip. " +
                            "Taking one copies it to your list; the rest stay out of the way.",
                        modifier = Modifier.padding(top = WaymarkSpacing.snug),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(WaymarkSpacing.section)) }
    }

    if (composing) {
        AddIdeaModal(
            cities = state.cities,
            onDismiss = { composing = false },
            onAdd = { title, kind, city, note ->
                onAddIdea(title, kind, city, note)
                composing = false
            },
        )
    }
}

@Composable
private fun IdeaCard(
    idea: Idea,
    party: List<com.waymark.domain.model.Traveler>,
    onSetStatus: (String, IdeaStatus) -> Unit,
    onToggleInterest: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSchedule: (Idea) -> Unit,
) {
    val colors = Waymark.colors
    val done = idea.status == IdeaStatus.DONE
    val scheduled = idea.status == IdeaStatus.SCHEDULED
    var expanded by remember(idea.id) { mutableStateOf(false) }

    Panel(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        padding = WaymarkSpacing.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
        ) {
            WaymarkIcon(
                icon = iconFor(idea.kind),
                tint = when {
                    done -> colors.textFaint
                    scheduled -> colors.accentSage
                    else -> colors.accentAmber
                },
                size = 17.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = idea.title,
                    style = Waymark.type.cardTitle.copy(
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    ),
                    color = if (done) colors.textFaint else colors.textHeading,
                )
                Text(
                    text = listOfNotNull(
                        idea.city.ifBlank { null },
                        idea.place?.address,
                        idea.priceBand?.label,
                        idea.typicalMinutes?.let { "${it}m" },
                    ).joinToString("  ·  "),
                    style = Waymark.type.dataSmall,
                    color = colors.textDim,
                )
            }
            if (scheduled) {
                FieldLabel("On the timeline", color = colors.accentSage)
            }
        }

        idea.note?.let { note ->
            Spacer(Modifier.height(WaymarkSpacing.snug))
            Text(
                text = note,
                style = Waymark.type.hint,
                color = if (done) colors.textFaint else colors.textMuted,
            )
        }

        if (expanded) {
            idea.bestTime?.let { best ->
                Spacer(Modifier.height(WaymarkSpacing.snug))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
                ) {
                    WaymarkIcon(WaymarkIcons.Clock, tint = colors.textDim, size = 13.dp)
                    Text(text = best, style = Waymark.type.hint, color = colors.textDim)
                }
            }

            if (party.size > 1) {
                Spacer(Modifier.height(WaymarkSpacing.small))
                FieldLabel("Who wants this")
                Spacer(Modifier.height(WaymarkSpacing.tight))
                Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                    party.forEach { traveler ->
                        val keen = traveler.id in idea.interestedTravelerIds
                        OptionChip(
                            text = traveler.displayName,
                            selected = keen,
                            onClick = { onToggleInterest(idea.id, traveler.id) },
                            leading = {
                                PartyMark(
                                    initials = traveler.initials,
                                    active = keen,
                                    size = 18.dp,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.small))
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
                if (!scheduled) {
                    MutedButton(
                        text = "Schedule",
                        icon = WaymarkIcons.Clock,
                        onClick = { onSchedule(idea) },
                    )
                }
                MutedButton(
                    text = if (done) "Not yet" else "Done",
                    icon = WaymarkIcons.Check,
                    onClick = {
                        onSetStatus(idea.id, if (done) IdeaStatus.SAVED else IdeaStatus.DONE)
                    },
                )
                GhostIconButton(
                    icon = WaymarkIcons.Trash,
                    contentDescription = "Remove ${idea.title}",
                    onClick = { onDelete(idea.id) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: Idea, onAdopt: (Idea) -> Unit) {
    val colors = Waymark.colors
    Panel(faint = true, modifier = Modifier.fillMaxWidth(), padding = WaymarkSpacing.small) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
        ) {
            WaymarkIcon(iconFor(suggestion.kind), tint = colors.textDim, size = 16.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = Waymark.type.bodySmall,
                    color = colors.textHeading,
                )
                suggestion.note?.let {
                    Text(text = it, style = Waymark.type.hint, color = colors.textDim)
                }
                Text(
                    text = listOfNotNull(
                        suggestion.city,
                        suggestion.kind.label,
                        suggestion.priceBand?.label,
                    ).joinToString("  ·  "),
                    style = Waymark.type.fieldLabel,
                    color = colors.textFaint,
                )
            }
            GhostIconButton(
                icon = WaymarkIcons.Plus,
                contentDescription = "Save ${suggestion.title}",
                onClick = { onAdopt(suggestion) },
                tint = colors.accentAmber,
            )
        }
    }
}

private fun iconFor(kind: IdeaKind) = when (kind) {
    IdeaKind.SIGHT -> WaymarkIcons.Compass
    IdeaKind.DISH -> WaymarkIcons.Coffee
    IdeaKind.EATERY -> WaymarkIcons.Coffee
    IdeaKind.WALK -> WaymarkIcons.MapPin
    IdeaKind.ACTIVITY -> WaymarkIcons.Ticket
    IdeaKind.SHOP -> WaymarkIcons.Search
}
