package com.waymark.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.IdeaStatus
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.domain.model.chronological
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Panel
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SegmentedToggle
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * The map, full bleed — or the same trip as a list.
 *
 * The map is a picture, and a picture says nothing to a screen reader; the
 * list says what the map shows, leg by leg, with the distance each covers and
 * a way into the booking. It is one tap away for everyone, not hidden behind
 * an accessibility setting, because "which leg was the long one" is easier
 * read than looked for.
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
    var asList by rememberSaveable { mutableStateOf(false) }

    if (asList) {
        MapList(segments = segments, onBack = { asList = false }, onOpenSegment = onOpenSegment)
        return
    }

    val places = remember(segments, state.ideas) {
        // One mark per place, each with its own id: a flight has a mark at
        // either end, and both open the same booking.
        val booked = segments
            .flatMap { listOf(Triple(it.origin, it, "from"), Triple(it.destination, it, "to")) }
            .filter { (place, _, _) -> place.hasCoordinates }
            .distinctBy { (place, _, _) -> place.code ?: place.name }
            .map { (place, segment, end) ->
                ChartPlace(
                    id = "${segment.id}:$end",
                    label = place.shortLabel,
                    position = place.position!!,
                    kind = kindOf(place, segment),
                    segmentId = segment.id,
                )
            }
        // Saved ideas with coordinates sit beside the bookings: "what is near
        // where we are staying" is a map question.
        val names = booked.map { it.label }.toSet()
        booked + state.ideas
            .filter { it.hasLocation && it.status != IdeaStatus.DISMISSED }
            .mapNotNull { idea ->
                val position = idea.place?.position ?: return@mapNotNull null
                ChartPlace(
                    id = "idea:${idea.id}",
                    label = idea.title,
                    position = position,
                    kind = ChartPlace.Kind.STOP,
                    segmentId = idea.scheduledSegmentId,
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
                    from = segment.origin.position!!,
                    to = segment.destination.position!!,
                    flying = segment is Segment.Flight,
                )
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WorldMap(
            places = places,
            routes = routes,
            projection = projection,
            description = "Map of the trip: ${routes.size} legs between ${places.size} places. " +
                "The list button above reads them out.",
            // Idea marks not yet on the timeline have no booking to open; they
            // stay as marks rather than leading nowhere.
            onSelect = onOpenSegment,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(WaymarkSpacing.snug),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GhostIconButton(icon = WaymarkIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
            GhostIconButton(
                icon = WaymarkIcons.List,
                contentDescription = "Show as a list",
                onClick = { asList = true },
            )
        }

        SegmentedToggle(
            options = MapProjection.entries.map { it.label },
            selectedIndex = MapProjection.entries.indexOf(projection),
            onSelect = { projection = MapProjection.entries[it] },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(WaymarkSpacing.medium)
                .width(180.dp),
        )
    }
}

/** Every leg that goes somewhere, in order, with how far it goes. */
@Composable
private fun MapList(
    segments: List<Segment>,
    onBack: () -> Unit,
    onOpenSegment: (String) -> Unit,
) {
    val colors = Waymark.colors
    val legs = remember(segments) {
        segments.chronological().filter { it.origin.name != it.destination.name || it.origin.code != null }
    }
    val stays = remember(segments) { segments.chronological().filterIsInstance<Segment.Lodging>() }

    ScreenScaffold(title = "Where the trip goes", onBack = onBack, spacing = WaymarkSpacing.small) {
        if (legs.isEmpty() && stays.isEmpty()) {
            EmptyLine("Nothing on the map yet.")
            return@ScreenScaffold
        }
        if (legs.isNotEmpty()) SectionHeader("Legs")
        legs.forEach { segment ->
            Panel(onClick = { onOpenSegment(segment.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = segment.title, style = Waymark.type.bodySmall, color = colors.textBody)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        TimeText.dayCompact(segment.start.toLocalDate()),
                        "${segment.origin.name} to ${segment.destination.name}",
                        distanceOf(segment)?.let { Geo.formatDistance(it) },
                    ).joinToString(" · "),
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        }
        if (stays.isNotEmpty()) SectionHeader("Stays")
        stays.forEach { stay ->
            Panel(onClick = { onOpenSegment(stay.id) }, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = stay.propertyName, style = Waymark.type.bodySmall, color = colors.textBody)
                    Text(
                        text = listOf(
                            stay.origin.city.ifBlank { stay.origin.name },
                            if (stay.nights == 1L) "1 night" else "${stay.nights} nights",
                        )
                            .joinToString(" · "),
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
            }
        }
        val total = legs.mapNotNull(::distanceOf).sum()
        if (total > 0) Footnote("${Geo.formatDistance(total)} in all, as the crow flies.")
    }
}

private fun distanceOf(segment: Segment): Double? =
    if (segment.origin.hasCoordinates && segment.destination.hasCoordinates) {
        Geo.distanceKm(segment.origin, segment.destination).takeIf { it > 0.0 }
    } else {
        null
    }

private fun kindOf(place: Place, segment: Segment): ChartPlace.Kind = when {
    place.code != null -> ChartPlace.Kind.STATION
    segment is Segment.Lodging -> ChartPlace.Kind.STAY
    else -> ChartPlace.Kind.STOP
}
