package com.waymark.ui.trip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.DisruptionAlert
import com.waymark.domain.model.Idea
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.TabRail
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.export.TripExport
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The command centre: one trip, five views of it.
 *
 * The header carries the trip's name, its dates, and one control. Everything
 * else that used to sit up here — five unlabelled glyphs and a theme switch —
 * moved into [TripMenu], because a row of icons a traveler cannot name is not
 * navigation, it is decoration with a tap target.
 */
@Composable
fun TripScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onAddFlight: () -> Unit,
    onOpenSegment: (String) -> Unit,
    onOpenPass: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val context = LocalContext.current
    var scheduling by remember { mutableStateOf<Idea?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var addingPlan by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    WaymarkBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(WaymarkSpacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back to itineraries",
                    onClick = onBack,
                    modifier = Modifier.offset(x = -WaymarkSpacing.snug),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.dossier?.trip?.name ?: "…",
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.dossier?.trip?.let { trip ->
                        Text(
                            text = TimeText.dateRange(trip.startDate(), trip.endDate()),
                            style = Waymark.type.dataSmall,
                            color = colors.textDim,
                        )
                    }
                }
                GhostIconButton(
                    icon = WaymarkIcons.Menu,
                    contentDescription = "More",
                    onClick = { menuOpen = true },
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.small))

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

            TabRail(
                labels = TripTab.entries.map { it.label },
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
                    onAddPlan = { addingPlan = true },
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

    if (menuOpen) {
        TripMenu(
            onDismiss = { menuOpen = false },
            onOpenAnalytics = onOpenAnalytics,
            onOpenPacking = onOpenPacking,
            onOpenInsights = onOpenInsights,
            onOpenMap = onOpenMap,
            onExport = { TripExport.share(context, state) },
            onDelete = { confirmingDelete = true },
        )
    }

    if (addingPlan) {
        AddPlanFlow(
            party = state.dossier?.party?.travelers.orEmpty(),
            defaultDate = state.dossier?.trip?.startDate() ?: java.time.LocalDate.now(),
            onDismiss = { addingPlan = false },
            onSave = {
                viewModel.addPlan(it)
                addingPlan = false
            },
        )
    }

    if (confirmingDelete) {
        DeleteTripModal(
            trip = state.dossier?.trip,
            bookings = state.dossier?.segments?.size ?: 0,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                viewModel.deleteTrip(onDeleted = onBack)
            },
        )
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
