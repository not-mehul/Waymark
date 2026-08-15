package com.waymark.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.TripAnalytics
import com.waymark.domain.model.Segment
import com.waymark.ui.charts.BarSeries
import com.waymark.ui.charts.DayColumn
import com.waymark.ui.charts.DayLoadChart
import com.waymark.ui.charts.Meter
import com.waymark.ui.charts.RingFigure
import com.waymark.ui.charts.SplitBar
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.Panel
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.Stat
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.map.ChartPlace
import com.waymark.ui.map.ChartRoute
import com.waymark.ui.map.Globe
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The trip, counted — and turned once on its axis.
 *
 * Every chart here is single-hue by design. The palette's two accents sit too
 * close together to carry identity: measured, amber against sage separates by
 * ΔE 8.6 to normal vision and 5.8 under protanopia, well under the floor for
 * telling two marks apart. So length carries magnitude, position and a written
 * label carry identity, and colour is left to do one job — emphasis.
 */
@Composable
fun AnalyticsScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val report = state.analytics
    var spinning by remember { mutableStateOf(false) }

    val segments = state.dossier?.segments.orEmpty()
    val places = remember(segments) {
        segments
            .flatMap { listOf(it.origin to it, it.destination to it) }
            .filter { (place, _) -> place.hasCoordinates }
            .distinctBy { (place, _) -> place.name }
            .map { (place, segment) ->
                ChartPlace(
                    id = segment.id,
                    label = place.shortLabel,
                    position = LatLon(place.latitude, place.longitude),
                    kind = when {
                        place.code != null -> ChartPlace.Kind.STATION
                        segment is Segment.Lodging -> ChartPlace.Kind.STAY
                        else -> ChartPlace.Kind.STOP
                    },
                )
            }
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

    WaymarkBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostIconButton(
                    icon = WaymarkIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                SectionLabel("The numbers")
            }

            Text(
                text = "This trip, counted",
                style = Waymark.type.screenTitle,
                color = colors.textHeading,
            )

            // — The globe ————————————————————————————————————————————————
            Panel(padding = WaymarkSpacing.snug, modifier = Modifier.fillMaxWidth()) {
                Globe(
                    places = places,
                    routes = routes,
                    spinning = spinning,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Drag to turn the world.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                    OptionChip(
                        text = if (spinning) "Stop" else "Spin",
                        selected = spinning,
                        onClick = { spinning = !spinning },
                    )
                }
            }

            if (report == null || !report.hasDistance) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nothing to count yet. Add a flight and the numbers fill in.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                Spacer(Modifier.height(WaymarkSpacing.section))
                return@Column
            }

            // — Headline figures ——————————————————————————————————————————
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                RingFigure(
                    fraction = (report.flownKm / report.totalDistanceKm).toFloat(),
                    figure = Geo.formatDistance(report.totalDistanceKm),
                    caption = "Travelled",
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
                    modifier = Modifier.padding(start = WaymarkSpacing.large),
                ) {
                    Stat(
                        value = report.countriesVisited.size.toString(),
                        label = "Countries",
                        emphasised = true,
                    )
                    Stat(value = report.timeZonesCrossed.toString(), label = "Time zones")
                    Stat(value = report.dayLoads.size.toString(), label = "Days")
                }
            }

            report.longestLegLabel?.let { leg ->
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    FieldLabel("Longest leg")
                    Spacer(Modifier.height(WaymarkSpacing.tight))
                    Text(text = leg, style = Waymark.type.cardTitle, color = colors.textHeading)
                    Text(
                        text = Geo.formatDistance(report.longestLegKm),
                        style = Waymark.type.dataSmall,
                        color = colors.accentAmber,
                    )
                }
            }

            // — Distance by mode ——————————————————————————————————————————
            SectionHeader("How far, by what")
            Panel(modifier = Modifier.fillMaxWidth()) {
                BarSeries(measures = report.distanceByMode)
            }

            // — The shape of the days —————————————————————————————————————
            SectionHeader("The shape of the days")
            Panel(modifier = Modifier.fillMaxWidth()) {
                DayLoadChart(
                    days = report.dayLoads.map { load ->
                        DayColumn(
                            label = load.date.format(DAY_LABEL),
                            moving = load.movingHours,
                            booked = load.bookedHours,
                            today = load.date == Instant.ofEpochMilli(state.nowMillis)
                                .atZone(Segment.zoneOrUtc(state.dossier?.trip?.homeZoneId ?: "UTC"))
                                .toLocalDate(),
                        )
                    },
                )
                Spacer(Modifier.height(WaymarkSpacing.small))
                Hairline()
                Spacer(Modifier.height(WaymarkSpacing.small))
                report.busiestDay?.let { busiest ->
                    Text(
                        text = "Busiest: ${TimeText.dayCompact(busiest.date)}, " +
                            "${busiest.busyHours.roundToInt()} hours accounted for.",
                        style = Waymark.type.bodySmall,
                        color = colors.textMuted,
                    )
                }
                report.quietestDay?.let { quietest ->
                    Text(
                        text = "Quietest: ${TimeText.dayCompact(quietest.date)}, " +
                            "${quietest.busyHours.roundToInt()} hours.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
            }

            // — Where the time goes ———————————————————————————————————————
            SectionHeader("Where the time goes")
            Panel(modifier = Modifier.fillMaxWidth()) {
                SplitBar(
                    parts = listOf(
                        "Moving ${report.hoursMoving.roundToInt()}h" to report.hoursMoving,
                        "Booked ${report.hoursBooked.roundToInt()}h" to report.hoursBooked,
                        "Yours ${report.hoursAtRest.roundToInt()}h" to report.hoursAtRest,
                    ),
                )
                Spacer(Modifier.height(WaymarkSpacing.small))
                Meter(
                    fraction = report.bookedShare.toFloat(),
                    label = "Waking hours with something on them",
                    value = "${(report.bookedShare * 100).roundToInt()}%",
                )
            }

            if (report.nightsByCity.isNotEmpty()) {
                SectionHeader("Nights, by place")
                Panel(modifier = Modifier.fillMaxWidth()) {
                    BarSeries(measures = report.nightsByCity)
                }
            }

            // — The list ——————————————————————————————————————————————————
            SectionHeader("The list")
            Panel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat(value = report.ideasDone.toString(), label = "Done", emphasised = true)
                    Stat(value = report.ideasScheduled.toString(), label = "Scheduled")
                    Stat(value = report.ideasOutstanding.toString(), label = "Still on it")
                }
                Spacer(Modifier.height(WaymarkSpacing.small))
                val considered = report.ideasDone + report.ideasScheduled + report.ideasOutstanding
                Meter(
                    fraction = if (considered == 0) 0f else report.ideasDone.toFloat() / considered,
                    label = "Ticked off",
                    value = "${report.ideasDone} of $considered",
                )
            }

            // — Carbon ————————————————————————————————————————————————————
            SectionHeader("Carbon")
            Panel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${report.carbonKg.roundToInt()} kg CO₂e",
                    style = Waymark.type.verdict,
                    color = colors.textHeading,
                )
                Spacer(Modifier.height(WaymarkSpacing.snug))
                Text(
                    text = "A model, not a measurement: distance times a published factor " +
                        "per mode — ${(TripAnalytics.EmissionFactors.LONG_HAUL_FLIGHT * 1000).roundToInt()} g " +
                        "per passenger-kilometre long-haul, " +
                        "${(TripAnalytics.EmissionFactors.TRAIN * 1000).roundToInt()} g by train. " +
                        "Lodging and meals are excluded rather than guessed at.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }

            EditorialNote(
                term = "One hue",
                body = "These charts carry magnitude in length and identity in labels. " +
                    "The palette's accents are too close to tell apart by colour alone, " +
                    "so they are never asked to.",
                modifier = Modifier.padding(top = WaymarkSpacing.small),
            )
            Spacer(Modifier.height(WaymarkSpacing.section))
        }
    }
}

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
