package com.waymark.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Place
import com.waymark.domain.model.Segment
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.Panel
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.Stat
import com.waymark.ui.map.ChartPlace
import com.waymark.ui.map.ChartRoute
import com.waymark.ui.map.RouteChart
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing

/**
 * The trip as geography: every leg drawn to scale, every place the app has
 * coordinates for, and an aircraft where one is currently in the air.
 */
@Composable
fun MapTab(
    state: TripUiState,
    onSelectSegment: (String) -> Unit,
) {
    val colors = Waymark.colors
    val segments = state.dossier?.segments.orEmpty()

    val places = remember(segments) {
        segments
            .flatMap { segment ->
                listOf(segment.origin to segment, segment.destination to segment)
            }
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

    val routes = remember(segments, state.dossier?.statuses) {
        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
            .map { segment ->
                val status = state.dossier?.statusFor(segment)
                val airborne = status?.state?.isAirborne == true
                ChartRoute(
                    id = segment.id,
                    from = LatLon(segment.origin.latitude, segment.origin.longitude),
                    to = LatLon(segment.destination.latitude, segment.destination.longitude),
                    flying = segment is Segment.Flight,
                    aircraft = status?.position?.let { LatLon(it.latitude, it.longitude) },
                    emphasis = airborne,
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
        Panel(padding = WaymarkSpacing.snug, modifier = Modifier.fillMaxWidth()) {
            RouteChart(
                places = places,
                routes = routes,
                onSelect = onSelectSegment,
                modifier = Modifier.height(340.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large)) {
            Stat(value = Geo.formatDistance(totalKm), label = "Total distance")
            Stat(value = Geo.formatDistance(flownKm), label = "In the air", emphasised = true)
            Stat(value = places.size.toString(), label = "Places")
        }

        SectionHeader("Legs")
        segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
            .forEach { segment ->
                Panel(
                    onClick = { onSelectSegment(segment.id) },
                    padding = WaymarkSpacing.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${segment.origin.shortLabel} → ${segment.destination.shortLabel}",
                        style = Waymark.type.cardTitle,
                        color = colors.textHeading,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOf(
                            Geo.formatDistance(Geo.distanceKm(segment.origin, segment.destination)),
                            TimeText.durationBetween(
                                segment.startEpochMillis,
                                segment.endEpochMillis,
                            ),
                            "${Geo.bearingDegrees(
                                LatLon(segment.origin.latitude, segment.origin.longitude),
                                LatLon(segment.destination.latitude, segment.destination.longitude),
                            ).toInt()}°",
                        ).joinToString("  ·  "),
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                }
            }

        EditorialNote(
            term = "Drawn, not downloaded",
            body = "Legs follow great circles, so a line that looks bent is the short " +
                "way round. Coordinates come from the bundled station list and from " +
                "places you enter.",
        )
        Spacer(Modifier.height(WaymarkSpacing.section))
    }
}

private fun kindOf(place: Place, segment: Segment): ChartPlace.Kind = when {
    place.code != null -> ChartPlace.Kind.STATION
    segment is Segment.Lodging -> ChartPlace.Kind.STAY
    else -> ChartPlace.Kind.STOP
}
