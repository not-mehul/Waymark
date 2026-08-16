package com.waymark.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.BuildConfig
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import java.time.Instant

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
    onOpenTrip: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var composing by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }

    WaymarkBackdrop {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding(),
            contentPadding = PaddingValues(
                start = WaymarkSpacing.screenHorizontal,
                end = WaymarkSpacing.screenHorizontal,
                top = WaymarkSpacing.screenTop,
                bottom = WaymarkSpacing.screenBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
        ) {
            item { Masthead() }

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
            item { VersionLine(onClick = { about = true }) }
        }
    }

    if (about) {
        AboutModal(onDismiss = { about = false })
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

@Composable
private fun Masthead() {
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
        ThemeToggle()
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
private fun VersionLine(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WaymarkSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Waymark ${BuildConfig.VERSION_NAME} · offline",
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
