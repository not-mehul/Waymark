package com.waymark.ui.trips

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.share.TripArchive
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.NoticeBanner
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.WaymarkModal
import com.waymark.ui.share.Barcodes
import com.waymark.ui.share.CODE_PREFIX
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The shelf of trips.
 *
 * The masthead is the whole of the app's front door: the name, one line
 * underneath it, and then the itineraries. It used to carry a tracked eyebrow,
 * a two-line serif lockup with an italic second word, a tagline, and a
 * footnote about offline storage — five pieces of copy before the first trip.
 * The name and the tagline say it; the rest was the app talking about itself.
 */
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    /** The installed version, for the line at the foot of the shelf and About. */
    versionName: String,
    onOpenTrip: (String) -> Unit,
    onOpenPeople: () -> Unit,
    /** A backup or trip file the traveler picked, to be shown before it is opened. */
    onOpenFile: (Uri) -> Unit,
    /** What a trip's QR code carries, read from a picture. */
    onOpenCode: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var composing by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val backUp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TripArchive.MIME)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.backUp(context, uri) { count ->
            notice = when (count) {
                null -> "The backup could not be written there."
                1 -> "One trip backed up."
                else -> "$count trips backed up."
            }
        }
    }
    // Any type: file managers and mail apps disagree about what a .json is.
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onOpenFile)
    }
    val readCode = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = Barcodes.read(context, uri)
            if (text != null && text.startsWith(CODE_PREFIX)) {
                onOpenCode(text.removePrefix(CODE_PREFIX))
            } else {
                notice = "There is no Waymark trip code in that picture."
            }
        }
    }

    WaymarkBackdrop {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(
                start = WaymarkSpacing.screenHorizontal,
                end = WaymarkSpacing.screenHorizontal,
                top = WaymarkSpacing.screenTop,
                bottom = WaymarkSpacing.screenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
        ) {
            item { Masthead(onMenu = { menuOpen = true }) }

            notice?.let { message ->
                item {
                    NoticeBanner(icon = WaymarkIcons.Info, headline = message, onDismiss = { notice = null })
                }
            }

            // Under way first, then ahead, then behind. Headings appear only
            // where there is more than one group to tell apart.
            val grouped = state.active.isNotEmpty() &&
                (state.upcoming.isNotEmpty() || state.past.isNotEmpty())

            if (state.active.isNotEmpty()) {
                if (grouped) item { SectionHeader("Under way") }
                items(state.active, key = { it.id }) { trip ->
                    TripCard(trip = trip, onClick = { onOpenTrip(trip.id) })
                }
            }

            if (state.upcoming.isEmpty() && state.active.isEmpty()) {
                item {
                    EmptyShelf(
                        loaded = state.loaded,
                        // Offered only on a genuinely empty shelf: a traveler
                        // who has trips of their own has no use for a demo, and
                        // one who has just deleted their last trip does not
                        // want somebody else's back in its place.
                        onLoadExample = if (state.isEmpty) {
                            { viewModel.loadExample(onOpenTrip) }
                        } else {
                            null
                        },
                    )
                }
            } else {
                if (grouped && state.upcoming.isNotEmpty()) item { SectionHeader("Ahead") }
                items(state.upcoming, key = { it.id }) { trip ->
                    TripCard(trip = trip, onClick = { onOpenTrip(trip.id) })
                }
            }

            if (state.past.isNotEmpty()) {
                item { SectionHeader("Behind") }
                items(state.past, key = { it.id }) { trip ->
                    TripCard(trip = trip, onClick = { onOpenTrip(trip.id) }, dimmed = true)
                }
            }

            // Put-away trips stay out of sight until asked for: they are kept,
            // not shelved, and say nothing — no reminders, no widget.
            if (state.archived.isNotEmpty()) {
                item {
                    Text(
                        text = if (showArchived) "Hide what is put away" else "Put away (${state.archived.size})",
                        style = Waymark.type.control,
                        color = Waymark.colors.textDim,
                        modifier = Modifier
                            .clip(WaymarkShapes.control)
                            .clickable(role = Role.Button) { showArchived = !showArchived }
                            .padding(horizontal = WaymarkSpacing.small, vertical = WaymarkSpacing.snug),
                    )
                }
                if (showArchived) {
                    items(state.archived, key = { it.id }) { trip ->
                        TripCard(trip = trip, onClick = { onOpenTrip(trip.id) }, dimmed = true)
                    }
                }
            }

            item {
                Spacer(Modifier.height(WaymarkSpacing.snug))
                PrimaryButton(
                    text = "New itinerary",
                    icon = WaymarkIcons.Plus,
                    onClick = { composing = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The version, where versions go, and the only way into About.
            item { VersionLine(versionName = versionName, onClick = { about = true }) }
        }
    }

    if (about) {
        AboutModal(versionName = versionName, onDismiss = { about = false })
    }

    if (menuOpen) {
        ShelfMenu(
            onDismiss = { menuOpen = false },
            onPeople = onOpenPeople,
            onBackUp = { backUp.launch(BACKUP_NAME) },
            onRestore = { restore.launch(arrayOf("*/*")) },
            onReadCode = {
                readCode.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }

    if (composing) {
        NewTripModal(
            onDismiss = { composing = false },
            onCreate = { name, destination, start, end ->
                viewModel.createTrip(name, destination, start, end) { id ->
                    composing = false
                    onOpenTrip(id)
                }
            },
        )
    }
}

/** Everything about the shelf as a whole: who travels, and the trips as files. */
@Composable
private fun ShelfMenu(
    onDismiss: () -> Unit,
    onPeople: () -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onReadCode: () -> Unit,
) {
    WaymarkModal(title = "Waymark", eyebrow = "Every trip", onDismiss = onDismiss) {
        listOf(
            Triple(WaymarkIcons.Users, "People", onPeople),
            Triple(WaymarkIcons.Download, "Back up every trip", onBackUp),
            Triple(WaymarkIcons.Archive, "Open a backup or a trip file", onRestore),
            Triple(WaymarkIcons.QrCode, "Read a trip code from a picture", onReadCode),
        ).forEachIndexed { index, (icon, label, action) ->
            if (index > 0) Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WaymarkShapes.control)
                    .clickable(role = Role.Button) {
                        onDismiss()
                        action()
                    }
                    .padding(vertical = WaymarkSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
            ) {
                WaymarkIcon(icon, tint = Waymark.colors.textDim, size = 18.dp)
                Text(text = label, style = Waymark.type.body, color = Waymark.colors.textBody)
            }
        }
        Footnote(
            "A backup is one file with every trip in it. Keep it wherever you keep things; " +
                "open it on a new phone to bring everything across."
        )
    }
}

/** `waymark-backup-2026-10-22.json` — dated, so a folder of them sorts itself. */
private val BACKUP_NAME: String get() = "waymark-backup-${java.time.LocalDate.now()}.json"

@Composable
private fun Masthead(onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = WaymarkSpacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Waymark",
                style = Waymark.type.pageTitle,
                color = Waymark.colors.textHeading,
            )
            Text(
                text = "Your whole trip, offline.",
                style = Waymark.type.tagline,
                color = Waymark.colors.textDim,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeToggle()
            GhostIconButton(icon = WaymarkIcons.Menu, contentDescription = "More", onClick = onMenu)
        }
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val colors = Waymark.colors
    val now = Instant.now()
    val status = trip.status(now)
    val days = trip.daysUntilStart(now)

    Panel(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            ) {
                Text(
                    text = trip.name,
                    style = Waymark.type.cardTitle,
                    color = if (dimmed) colors.textMuted else colors.textHeading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trip.destinationSummary,
                    style = Waymark.type.hint,
                    color = colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WaymarkIcon(WaymarkIcons.ChevronRight, tint = colors.textFaint, size = 18.dp)
        }

        Spacer(Modifier.height(WaymarkSpacing.small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = TimeText.dateRange(trip.startDate(), trip.endDate()),
                style = Waymark.type.dataSmall,
                color = colors.textMuted,
            )
            Text(
                text = when {
                    status == TripStatus.ACTIVE -> "Under way"
                    status == TripStatus.PAST -> "Complete"
                    days == 0L -> "Departs today"
                    days == 1L -> "Departs tomorrow"
                    else -> "In $days days"
                },
                style = Waymark.type.dataSmall,
                color = when (status) {
                    TripStatus.ACTIVE -> colors.accentBright
                    TripStatus.PAST -> colors.textFaint
                    TripStatus.UPCOMING -> colors.accentAmber
                },
            )
        }
    }
}

/** The version, and the door to About behind it. */
@Composable
private fun VersionLine(versionName: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WaymarkSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Waymark $versionName · offline",
            style = Waymark.type.hint,
            color = Waymark.colors.textFaint,
            modifier = Modifier
                .clip(WaymarkShapes.control)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = WaymarkSpacing.small, vertical = WaymarkSpacing.snug),
        )
    }
}

/**
 * What a fresh install opens on.
 *
 * Waymark used to write the London & Paris example into the database on first
 * run, so the first thing a new traveler saw was a stranger's holiday that they
 * had to delete before starting their own. The example is worth having — it is
 * the only way to see the timeline, the map and the numbers with something in
 * them — so it is offered here rather than installed, one line quieter than the
 * button that starts a real trip.
 */
@Composable
private fun EmptyShelf(loaded: Boolean, onLoadExample: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WaymarkSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        Text(
            text = if (loaded) "Nothing planned yet." else "Reading the shelf…",
            style = Waymark.type.hint,
            color = Waymark.colors.textDim,
        )
        if (loaded && onLoadExample != null) {
            Text(
                text = "Load the worked example",
                style = Waymark.type.control,
                color = Waymark.colors.accentAmber,
                modifier = Modifier
                    .clip(WaymarkShapes.control)
                    .clickable(role = Role.Button, onClick = onLoadExample)
                    .padding(
                        horizontal = WaymarkSpacing.small,
                        vertical = WaymarkSpacing.snug,
                    ),
            )
        }
    }
}
