package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Idea
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The command centre. One trip, five views of it, and a header that says what
 * is happening now without being asked.
 */
@Composable
fun TripScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onAddFlight: () -> Unit,
    onAddPlan: () -> Unit,
    onOpenSegment: (String) -> Unit,
    onOpenPass: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenAnalytics: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    var scheduling by remember { mutableStateOf<Idea?>(null) }

    WaymarkBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(WaymarkSpacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back to itineraries",
                    onClick = onBack,
                    modifier = Modifier.padding(start = 0.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostIconButton(
                        icon = WaymarkIcons.Refresh,
                        contentDescription = "Refresh flight status",
                        onClick = viewModel::refresh,
                        tint = if (state.refreshing) colors.accentAmber else null,
                    )
                    GhostIconButton(
                        icon = WaymarkIcons.Globe,
                        contentDescription = "The numbers",
                        onClick = onOpenAnalytics,
                    )
                    GhostIconButton(
                        icon = WaymarkIcons.Check,
                        contentDescription = "Packing",
                        onClick = onOpenPacking,
                    )
                    GhostIconButton(
                        icon = WaymarkIcons.Compass,
                        contentDescription = "Destination notes",
                        onClick = onOpenInsights,
                    )
                    ThemeToggle()
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.small))

            val dossier = state.dossier
            Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
                SectionLabel(dossier?.trip?.destinationSummary ?: "Itinerary")
                Text(
                    text = dossier?.trip?.name ?: "…",
                    style = Waymark.type.screenTitle,
                    color = colors.textHeading,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                dossier?.trip?.let { trip ->
                    Text(
                        text = TimeText.dateRange(trip.startDate(), trip.endDate()) +
                            " · ${dossier.party.travelers.size} traveler" +
                            if (dossier.party.travelers.size == 1) "" else "s",
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.medium))

            state.alerts.firstOrNull()?.let { alert ->
                NoticeBanner(
                    icon = if (alert.severity == DisruptionAlert.Severity.CRITICAL) {
                        WaymarkIcons.Alert
                    } else {
                        WaymarkIcons.Info
                    },
                    headline = alert.headline,
                    detail = alert.detail,
                    critical = alert.severity == DisruptionAlert.Severity.CRITICAL,
                )
                Spacer(Modifier.height(WaymarkSpacing.small))
            }

            SegmentedToggle(
                options = TripTab.entries.map { it.label },
                selectedIndex = TripTab.entries.indexOf(tab),
                onSelect = { viewModel.selectTab(TripTab.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(WaymarkSpacing.medium))

            when (tab) {
                TripTab.TIMELINE -> TimelineTab(
                    state = state,
                    onSelectSegment = onOpenSegment,
                    onFilterTraveler = viewModel::filterBy,
                    onAddFlight = onAddFlight,
                    onAddPlan = onAddPlan,
                )

                TripTab.IDEAS -> IdeasTab(
                    state = state,
                    onAdopt = viewModel::adopt,
                    onSetStatus = viewModel::setIdeaStatus,
                    onToggleInterest = viewModel::toggleInterest,
                    onDelete = viewModel::deleteIdea,
                    onSchedule = { scheduling = it },
                    onAddIdea = viewModel::addIdea,
                    onPencilDay = viewModel::pencilIdeaFor,
                    tripDays = viewModel.tripDays(),
                )

                TripTab.MAP -> MapTab(state = state, onSelectSegment = onOpenSegment)

                TripTab.PARTY -> PartyTab(
                    state = state,
                    onAddTraveler = viewModel::addTraveler,
                    onRemoveTraveler = viewModel::removeTraveler,
                    onFilterTraveler = viewModel::filterBy,
                )

                TripTab.VAULT -> VaultTab(
                    state = state,
                    onOpenPass = onOpenPass,
                    onOpenSegment = onOpenSegment,
                    onAddDocument = viewModel::addDocument,
                    onDeleteDocument = viewModel::deleteDocument,
                )
            }
        }
    }

    scheduling?.let { idea ->
        ScheduleIdeaModal(
            idea = idea,
            // Default to the first day of the trip that has not already gone.
            defaultDate = state.dossier?.trip?.let { trip ->
                maxOf(
                    trip.startDate(),
                    java.time.Instant.ofEpochMilli(state.nowMillis)
                        .atZone(com.waymark.domain.model.Segment.zoneOrUtc(trip.homeZoneId))
                        .toLocalDate(),
                )
            } ?: java.time.LocalDate.now(),
            onDismiss = { scheduling = null },
            onSchedule = { date, time, minutes ->
                viewModel.scheduleIdea(idea, date, time, minutes)
                scheduling = null
                viewModel.selectTab(TripTab.TIMELINE)
            },
        )
    }
}
