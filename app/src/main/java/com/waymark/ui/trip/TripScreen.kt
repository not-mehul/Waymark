package com.waymark.ui.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Idea
import com.waymark.domain.model.Segment
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.LocalReminderPermission
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.TabRail
import com.waymark.ui.components.UndoBar
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.export.TripExport
import com.waymark.ui.share.TripCodeModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The command centre: one trip, three views of it.
 *
 * The header carries the trip's name, its dates, and one control. Everything
 * else lives in [TripMenu], because a row of icons a traveler cannot name is
 * not navigation, it is decoration with a tap target.
 *
 * On a wide screen — a tablet, an unfolded foldable — the booking a traveler
 * taps opens beside the timeline instead of over it, so the day stays in view
 * while one part of it is read.
 */
@Composable
fun TripScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onAddFlight: () -> Unit,
    onOpenSegment: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenMap: () -> Unit,
    /** The booking screen's content, for the second pane on a wide screen. */
    segmentPane: @Composable (segmentId: String, onClose: () -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reminders = LocalReminderPermission.current
    var schedulingId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var addingPlan by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var editingTrip by rememberSaveable { mutableStateOf(false) }
    var editingReminders by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingCode by rememberSaveable { mutableStateOf(false) }

    // The expanded width class — a tablet, an unfolded foldable, a desktop
    // window — rather than a pixel count, so a resized window is judged the
    // same way the platform judges it.
    val wide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val openSegment: (String) -> Unit = { id -> if (wide) selected = id else onOpenSegment(id) }
    val scheduling: Idea? = schedulingId?.let { id -> state.ideas.firstOrNull { it.id == id } }

    WaymarkBackdrop {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = WaymarkSpacing.screenHorizontal),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(WaymarkSpacing.small))
                    Header(state = state, onBack = onBack, onMenu = { menuOpen = true })
                    Spacer(Modifier.height(WaymarkSpacing.small))

                    state.banner?.let { banner ->
                        NoticeBanner(
                            icon = WaymarkIcons.Bell,
                            headline = banner.headline,
                            detail = banner.detail,
                            onClick = { openSegment(banner.segmentId) },
                            onDismiss = { viewModel.dismissBanner(banner.segmentId) },
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
                            onSelectSegment = openSegment,
                            onFilterTraveler = viewModel::filterBy,
                            onAddFlight = onAddFlight,
                            onAddPlan = { addingPlan = true },
                        )

                        TripTab.IDEAS -> IdeasTab(
                            state = state,
                            onSetStatus = viewModel::setIdeaStatus,
                            onToggleInterest = viewModel::toggleInterest,
                            onDelete = viewModel::deleteIdea,
                            onSchedule = { schedulingId = it.id },
                            onAddIdea = viewModel::addIdea,
                            onPencilDay = viewModel::pencilIdeaFor,
                            onShowDismissed = viewModel::showDismissed,
                        )

                        TripTab.PARTY -> PartyTab(
                            state = state,
                            onAddTraveler = viewModel::addTraveler,
                            onAddExisting = viewModel::addExistingTraveler,
                            onUpdateTraveler = viewModel::updateTraveler,
                            onRemoveTraveler = viewModel::removeTraveler,
                            onFilterTraveler = viewModel::filterBy,
                        )
                    }
                }

                state.undoIdea?.let { idea ->
                    UndoBar(
                        message = "Removed “${idea.title}”",
                        onUndo = viewModel::undoDelete,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = WaymarkSpacing.medium),
                    )
                }
            }

            if (wide) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val shown = selected?.takeIf { id ->
                        state.dossier?.segments?.any { it.id == id } == true
                    }
                    if (shown != null) {
                        segmentPane(shown) { selected = null }
                    } else {
                        Footnote(
                            text = "Pick a booking to read it here.",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(WaymarkSpacing.large),
                        )
                    }
                }
            }
        }
    }

    if (menuOpen) {
        TripMenu(
            archived = state.dossier?.trip?.archived == true,
            onDismiss = { menuOpen = false },
            onOpenAnalytics = onOpenAnalytics,
            onOpenInsights = onOpenInsights,
            onOpenMap = onOpenMap,
            onExportMarkdown = { TripExport.shareMarkdown(context, state) },
            onExportCalendar = { TripExport.shareCalendar(context, state) },
            onShareFile = { TripExport.shareTripFile(context, state) },
            onShareCode = { sharingCode = true },
            onEdit = { editingTrip = true },
            onReminders = { editingReminders = true },
            onArchive = { archive ->
                viewModel.setArchived(archive)
                if (archive) onBack()
            },
            onDelete = { confirmingDelete = true },
            reminderSummary = state.reminders.summary(),
        )
    }

    if (sharingCode) {
        TripCodeModal(
            state = state,
            onShareFile = { TripExport.shareTripFile(context, state) },
            onDismiss = { sharingCode = false },
        )
    }

    if (addingPlan) {
        AddPlanFlow(
            party = state.dossier?.party?.travelers.orEmpty(),
            defaultDate = state.dossier?.trip?.startDate() ?: java.time.LocalDate.now(),
            contextFor = viewModel::planContext,
            isValid = viewModel::planIsValid,
            onDismiss = { addingPlan = false },
            onSave = {
                if (viewModel.addPlan(it)) {
                    addingPlan = false
                    // The first booking of any kind is when a reminder about
                    // it starts to mean something; ask then, once.
                    reminders.requestOnce()
                }
            },
        )
    }

    if (editingReminders) {
        RemindersModal(
            preferences = state.reminders,
            onSet = viewModel::setReminderLead,
            onDismiss = { editingReminders = false },
        )
    }

    if (editingTrip) {
        state.dossier?.let { dossier ->
            EditTripModal(
                trip = dossier.trip,
                bookings = dossier.segments,
                onDismiss = { editingTrip = false },
                onFitToBookings = {
                    viewModel.fitDatesToBookings()
                    editingTrip = false
                },
                onSave = { name, destination, start, end ->
                    viewModel.updateTrip(name, destination, start, end)
                    editingTrip = false
                },
            )
        }
    }

    if (confirmingDelete) {
        DeleteTripModal(
            trip = state.dossier?.trip,
            bookings = state.dossier?.segments?.size ?: 0,
            onDismiss = { confirmingDelete = false },
            onArchiveInstead = {
                confirmingDelete = false
                viewModel.setArchived(true)
                onBack()
            },
            onConfirm = {
                confirmingDelete = false
                viewModel.deleteTrip(onDeleted = onBack)
            },
        )
    }

    scheduling?.let { idea ->
        ScheduleIdeaModal(
            idea = idea,
            // The day it is pencilled in for, or the first day of the trip
            // that has not already gone.
            defaultDate = idea.plannedDate ?: state.dossier?.trip?.let { trip ->
                maxOf(
                    trip.startDate(),
                    java.time.Instant.ofEpochMilli(state.nowMillis)
                        .atZone(Segment.zoneOrUtc(trip.homeZoneId))
                        .toLocalDate(),
                )
            } ?: java.time.LocalDate.now(),
            onDismiss = { schedulingId = null },
            onSchedule = { date, time, minutes ->
                viewModel.scheduleIdea(idea, date, time, minutes)
                schedulingId = null
                viewModel.selectTab(TripTab.TIMELINE)
                reminders.requestOnce()
            },
        )
    }
}

@Composable
private fun Header(state: TripUiState, onBack: () -> Unit, onMenu: () -> Unit) {
    val colors = Waymark.colors
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
                    text = TimeText.dateRange(trip.startDate(), trip.endDate()) +
                        if (trip.archived) " · put away" else "",
                    style = Waymark.type.dataSmall,
                    color = colors.textDim,
                )
            }
        }
        GhostIconButton(
            icon = WaymarkIcons.Menu,
            contentDescription = "More",
            onClick = onMenu,
        )
    }
}
