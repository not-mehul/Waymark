package com.waymark.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Trip
import com.waymark.domain.model.TripStatus
import com.waymark.ui.components.EditorialHeading
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PrimaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.ThemeToggle
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcon
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import java.time.Instant

/**
 * The shelf of trips. Quiet by design: a title, a line of prose, and the
 * itineraries in date order with the one that matters at the top.
 */
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onOpenTrip: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var composing by rememberSaveable { mutableStateOf(false) }

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

            if (state.active.isNotEmpty()) {
                item { SectionHeader("Under way") }
                items(state.active, key = { it.id }) { trip ->
                    TripCard(trip = trip, onClick = { onOpenTrip(trip.id) })
                }
            }

            item { SectionHeader("Ahead") }
            if (state.upcoming.isEmpty()) {
                item { EmptyShelf(loaded = state.loaded) }
            } else {
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

            item {
                EditorialNote(
                    term = "Offline",
                    body = "Schedules, codes and passes are held on this device. " +
                        "Nothing is uploaded, and everything here opens with the radio off.",
                    modifier = Modifier.padding(top = WaymarkSpacing.small),
                )
            }
        }
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
    Column(modifier = Modifier.padding(bottom = WaymarkSpacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                FieldLabel("Travel logistics")
                Spacer(Modifier.height(WaymarkSpacing.snug))
                EditorialHeading(lead = "Waymark", emphasis = "one column")
            }
            ThemeToggle()
        }
        Spacer(Modifier.height(WaymarkSpacing.small))
        Text(
            text = "Every reservation, every traveler, in the order they happen.",
            style = Waymark.type.tagline,
            color = Waymark.colors.textDim,
        )
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

@Composable
private fun EmptyShelf(loaded: Boolean) {
    Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = WaymarkSpacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (loaded) {
                    "No itineraries yet."
                } else {
                    "Reading the vault…"
                },
                style = Waymark.type.hint,
                color = Waymark.colors.textDim,
            )
        }
    }
}
