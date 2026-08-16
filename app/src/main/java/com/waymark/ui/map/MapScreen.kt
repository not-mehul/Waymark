package com.waymark.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.LatLon
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * The map, full bleed, and nothing else.
 *
 * It used to be a tab with the map at the top and a column of statistics and
 * leg distances beneath it — the same numbers the Numbers screen already had,
 * in a second place, so neither screen was the answer to "how far is this
 * trip". The figures live in Numbers now; this is the picture.
 */
@Composable
fun MapScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onOpenSegment: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val segments = state.dossier?.segments.orEmpty()
    var projection by rememberSaveable { mutableStateOf(MapProjection.FLAT) }

    val places = remember(segments, state.ideas) {
        val booked = segments
            .flatMap { listOf(it.origin to it, it.destination to it) }
            .filter { (place, _) -> place.hasCoordinates }
            .distinctBy { (place, _) -> place.name }
            .map { (place, segment) ->
                ChartPlace(
                    id = segment.id,
                    label = place.shortLabel,
                    position = LatLon(place.latitude, place.longitude),
                    kind = kindOf(place, segment),
                )
            }
        // Saved ideas with coordinates sit beside the bookings: "what is near
        // where we are staying" is a map question.
        val names = booked.map { it.label }.toSet()
        booked + state.ideas
            .filter { it.hasLocation && it.status != IdeaStatus.DISMISSED }
            .map { idea ->
                ChartPlace(
                    id = idea.scheduledSegmentId ?: idea.id,
                    label = idea.title,
                    position = LatLon(idea.place!!.latitude, idea.place.longitude),
                    kind = ChartPlace.Kind.STOP,
                )
            }
            .filterNot { it.label in names }
    }

    val routes = remember(segments) {
        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
            .map { segment ->
                ChartRoute(
                    id = segment.id,
                    from = LatLon(segment.origin.latitude, segment.origin.longitude),
                    to = LatLon(segment.destination.latitude, segment.destination.longitude),
                    flying = segment is Segment.Flight,
                )
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WorldMap(
            places = places,
            routes = routes,
            projection = projection,
            // Idea marks not yet on the timeline have no segment to open; they
            // stay as marks rather than leading nowhere.
            onSelect = { id -> if (segments.any { it.id == id }) onOpenSegment(id) },
            modifier = Modifier.fillMaxSize(),
        )

        GhostIconButton(
            icon = WaymarkIcons.ArrowLeft,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(WaymarkSpacing.snug),
        )

        SegmentedToggle(
            options = MapProjection.entries.map { it.label },
            selectedIndex = MapProjection.entries.indexOf(projection),
            onSelect = { projection = MapProjection.entries[it] },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(WaymarkSpacing.medium)
                .width(180.dp),
        )
    }
}

private fun kindOf(place: Place, segment: Segment): ChartPlace.Kind = when {
    place.code != null -> ChartPlace.Kind.STATION
    segment is Segment.Lodging -> ChartPlace.Kind.STAY
    else -> ChartPlace.Kind.STOP
}
