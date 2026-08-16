package com.waymark.ui.trip

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Idea
import com.waymark.domain.model.IdeaKind
import com.waymark.domain.model.IdeaStatus
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.MutedButton
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.LocalDate

/**
 * The unscheduled half of the trip: places to see, food to try, walks to take.
 *
 * Nothing here has a time on it, which is the point — a trip is not only its
 * bookings. Anything on the list can be promoted onto the timeline, at
 * which point it becomes an ordinary booking and stops asking for attention.
 */
@Composable
fun IdeasTab(
    state: TripUiState,
    onSetStatus: (String, IdeaStatus) -> Unit,
    onToggleInterest: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSchedule: (Idea) -> Unit,
    onAddIdea: (String, IdeaKind, String, String?) -> Unit,
    onPencilDay: (String, LocalDate?) -> Unit,
    tripDays: List<LocalDate>,
) {
    val colors = Waymark.colors
    var composing by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(IdeaGrouping.KIND) }
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

        // The same list, read three ways: by what it is, by where it is, and
        // by the day it is pencilled in for.
        item {
            SegmentedToggle(
                options = IdeaGrouping.entries.map { it.label },
                selectedIndex = IdeaGrouping.entries.indexOf(grouping),
                onSelect = { grouping = IdeaGrouping.entries[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WaymarkSpacing.snug),
            )
        }

        when (grouping) {
            IdeaGrouping.KIND -> state.ideaSections.forEach { section ->
                item(key = "kind-${section.kind.name}") {
                    SectionHeader(section.kind.heading)
                }
                items(section.ideas, key = { "kind-${it.id}" }) { idea ->
                    IdeaCard(
                        idea = idea,
                        party = party,
                        tripDays = tripDays,
                        onSetStatus = onSetStatus,
                        onToggleInterest = onToggleInterest,
                        onDelete = onDelete,
                        onSchedule = onSchedule,
                        onPencilDay = onPencilDay,
                    )
                }
            }

            IdeaGrouping.PLACE -> state.ideasByCity.forEach { group ->
                item(key = "city-${group.city}") {
                    SectionHeader(
                        label = group.city,
                        trailing = {
                            Text(
                                text = "${group.outstanding} open",
                                style = Waymark.type.fieldLabel,
                                color = colors.textFaint,
                            )
                        },
                    )
                }
                items(group.ideas, key = { "city-${it.id}" }) { idea ->
                    IdeaCard(
                        idea = idea,
                        party = party,
                        tripDays = tripDays,
                        onSetStatus = onSetStatus,
                        onToggleInterest = onToggleInterest,
                        onDelete = onDelete,
                        onSchedule = onSchedule,
                        onPencilDay = onPencilDay,
                    )
                }
            }

            IdeaGrouping.DAY -> {
                state.dayPlan.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        SectionHeader(
                            label = TimeText.dayCompact(day.date),
                            trailing = {
                                Text(
                                    text = TimeText.duration(day.estimatedMinutes),
                                    style = Waymark.type.fieldLabel,
                                    color = if (day.estimatedMinutes > FULL_DAY_MINUTES) {
                                        colors.danger
                                    } else {
                                        colors.textFaint
                                    },
                                )
                            },
                        )
                    }
                    items(day.ideas, key = { "day-${it.id}" }) { idea ->
                        IdeaCard(
                            idea = idea,
                            party = party,
                            tripDays = tripDays,
                            onSetStatus = onSetStatus,
                            onToggleInterest = onToggleInterest,
                            onDelete = onDelete,
                            onSchedule = onSchedule,
                            onPencilDay = onPencilDay,
                        )
                    }
                }
                if (state.dayPlan.undated.isNotEmpty()) {
                    item(key = "day-undated") { SectionHeader("No day yet") }
                    items(state.dayPlan.undated, key = { "undated-${it.id}" }) { idea ->
                        IdeaCard(
                            idea = idea,
                            party = party,
                            tripDays = tripDays,
                            onSetStatus = onSetStatus,
                            onToggleInterest = onToggleInterest,
                            onDelete = onDelete,
                            onSchedule = onSchedule,
                            onPencilDay = onPencilDay,
                        )
                    }
                }
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
    tripDays: List<LocalDate>,
    onSetStatus: (String, IdeaStatus) -> Unit,
    onToggleInterest: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSchedule: (Idea) -> Unit,
    onPencilDay: (String, LocalDate?) -> Unit,
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
            when {
                scheduled -> FieldLabel("On the timeline", color = colors.accentSage)
                idea.plannedDate != null -> FieldLabel(
                    TimeText.dayCompact(idea.plannedDate),
                    color = colors.accentAmber,
                )
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

            if (tripDays.isNotEmpty() && !scheduled) {
                Spacer(Modifier.height(WaymarkSpacing.small))
                FieldLabel("Pencil it in for")
                Spacer(Modifier.height(WaymarkSpacing.tight))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                ) {
                    tripDays.forEach { day ->
                        OptionChip(
                            text = TimeText.dayCompact(day),
                            selected = idea.plannedDate == day,
                            // Tapping the day it already holds rubs it out.
                            onClick = {
                                onPencilDay(idea.id, if (idea.plannedDate == day) null else day)
                            },
                        )
                    }
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

/** A day with more than this pencilled in is a day nobody will keep. */
private const val FULL_DAY_MINUTES = 480

enum class IdeaGrouping(val label: String) {
    KIND("By kind"),
    PLACE("By place"),
    DAY("By day"),
}
