package com.waymark.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.Panel
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.components.Stat
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

    val segments = state.dossier?.segments.orEmpty()

    ScreenScaffold(title = "The numbers", onBack = onBack) {
        if (report == null || !report.hasDistance) {
            Footnote("Nothing to count yet. Add a flight and the numbers fill in.")
            return@ScreenScaffold
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

        // — Every leg ————————————————————————————————————————————————
        val legs = segments
            .filter { it.origin.hasCoordinates && it.destination.hasCoordinates }
            .filter { it.origin.name != it.destination.name }
        if (legs.isNotEmpty()) {
            SectionHeader("Every leg")
            Panel(modifier = Modifier.fillMaxWidth()) {
                legs.forEachIndexed { index, segment ->
                    if (index > 0) {
                        Spacer(Modifier.height(WaymarkSpacing.snug))
                        Hairline()
                        Spacer(Modifier.height(WaymarkSpacing.snug))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${segment.origin.shortLabel} → " +
                                segment.destination.shortLabel,
                            style = Waymark.type.bodySmall,
                            color = colors.textBody,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Geo.formatDistance(
                                Geo.distanceKm(segment.origin, segment.destination)
                            ),
                            style = Waymark.type.dataSmall,
                            color = colors.textDim,
                        )
                    }
                    Text(
                        text = listOf(
                            TimeText.durationBetween(
                                segment.startEpochMillis,
                                segment.endEpochMillis,
                            ),
                            "${Geo.bearingDegrees(
                                LatLon(segment.origin.latitude, segment.origin.longitude),
                                LatLon(
                                    segment.destination.latitude,
                                    segment.destination.longitude,
                                ),
                            ).toInt()}°",
                        ).joinToString("  ·  "),
                        style = Waymark.type.hint,
                        color = colors.textFaint,
                    )
                }
            }
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

        Footnote(
            "One hue throughout: these charts carry magnitude in length and identity " +
                "in labels, because the palette's two accents are too close to tell " +
                "apart by colour alone."
        )
    }
}

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
