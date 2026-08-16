package com.waymark.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.Stat
import com.waymark.ui.components.StatRow
import com.waymark.ui.map.ChartPlace
import com.waymark.ui.map.ChartRoute
import com.waymark.ui.map.MapProjection
import com.waymark.ui.map.WorldMap
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The trip as geography. Every leg to scale, every place with coordinates, and
 * a choice of two projections — a chart to read a city by, a globe to see what
 * a long haul actually is.
 */
@Composable
fun MapTab(
    state: TripUiState,
    onSelectSegment: (String) -> Unit,
) {
    val segments = state.dossier?.segments.orEmpty()
    var projection by rememberSaveable { mutableStateOf(MapProjection.FLAT) }

    // Saved ideas with coordinates sit on the chart beside the bookings: the
    // "what is near where we are staying" question is a map question.
    val ideaPlaces = remember(state.ideas) {
        state.ideas
            .filter { it.hasLocation && it.status != IdeaStatus.DISMISSED }
            .map { idea ->
                ChartPlace(
                    id = idea.scheduledSegmentId ?: idea.id,
                    label = idea.title,
                    position = LatLon(idea.place!!.latitude, idea.place.longitude),
                    kind = ChartPlace.Kind.STOP,
                )
            }
    }

    val bookedPlaces = remember(segments) {
        segments
            .flatMap { segment -> listOf(segment.origin to segment, segment.destination to segment) }
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
    }

    val places = remember(bookedPlaces, ideaPlaces) {
        // A booked place wins over an idea at the same spot.
        val booked = bookedPlaces.map { it.label }.toSet()
        bookedPlaces + ideaPlaces.filterNot { it.label in booked }
    }

    val routes = remember(segments, state.dossier?.statuses) {
        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
            .map { segment ->
                val status = state.dossier?.statusFor(segment)
                ChartRoute(
                    id = segment.id,
                    from = LatLon(segment.origin.latitude, segment.origin.longitude),
                    to = LatLon(segment.destination.latitude, segment.destination.longitude),
                    flying = segment is Segment.Flight,
                    aircraft = status?.position?.let { LatLon(it.latitude, it.longitude) },
                    emphasis = status?.state?.isAirborne == true,
                )
            }
    }

    val totalKm = remember(segments) {
        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .sumOf { Geo.distanceKm(it.origin, it.destination) }
    }
    val flownKm = remember(segments) {
        segments.filterIsInstance<Segment.Flight>()
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .sumOf { Geo.distanceKm(it.origin, it.destination) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
    ) {
        WorldMap(
            places = places,
            routes = routes,
            projection = projection,
            // Idea marks not yet on the timeline have no segment to open; they
            // stay as marks rather than leading nowhere.
            onSelect = { id -> if (segments.any { it.id == id }) onSelectSegment(id) },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .clip(WaymarkShapes.panel),
        ) {
            SegmentedToggle(
                options = MapProjection.entries.map { it.label },
                selectedIndex = MapProjection.entries.indexOf(projection),
                onSelect = { projection = MapProjection.entries[it] },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(WaymarkSpacing.snug)
                    .width(150.dp),
            )
            Legend(modifier = Modifier.align(Alignment.TopEnd).padding(WaymarkSpacing.snug))
        }

        StatRow {
            Stat(value = Geo.formatDistance(totalKm), label = "Total")
            Stat(value = Geo.formatDistance(flownKm), label = "Flown", emphasised = true)
            Stat(value = places.size.toString(), label = "Places")
        }

        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
            .forEach { segment ->
                LegRow(segment = segment, onClick = { onSelectSegment(segment.id) })
            }

        Spacer(Modifier.height(WaymarkSpacing.section))
    }
}

/**
 * Three marks, three words. The old version explained the same thing in two
 * paragraphs of prose beneath the map, where nobody looking at a mark was.
 */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val colors = Waymark.colors
    Column(
        modifier = modifier
            .clip(WaymarkShapes.chip)
            .background(colors.mapWater.copy(alpha = 0.82f))
            .padding(horizontal = WaymarkSpacing.snug, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            colors.accentAmber to "Station",
            colors.accentSage to "Stay",
            colors.textDim to "On the list",
        ).forEach { (tint, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Spacer(
                    Modifier
                        .height(7.dp)
                        .width(7.dp)
                        .clip(WaymarkShapes.chip)
                        .background(tint)
                )
                Text(text = label, style = Waymark.type.fieldLabel, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun LegRow(segment: Segment, onClick: () -> Unit) {
    val colors = Waymark.colors
    val distance = Geo.distanceKm(segment.origin, segment.destination)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WaymarkShapes.control)
            .clickable(onClick = onClick)
            .padding(vertical = WaymarkSpacing.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${segment.origin.shortLabel} → ${segment.destination.shortLabel}",
            style = Waymark.type.bodySmall,
            color = colors.textBody,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Geo.formatDistance(distance),
            style = Waymark.type.dataSmall,
            color = colors.textDim,
        )
    }
}

private fun kindOf(place: Place, segment: Segment): ChartPlace.Kind = when {
    place.code != null -> ChartPlace.Kind.STATION
    segment is Segment.Lodging -> ChartPlace.Kind.STAY
    else -> ChartPlace.Kind.STOP
}
