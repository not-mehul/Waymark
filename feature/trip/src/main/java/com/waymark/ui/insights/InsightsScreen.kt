package com.waymark.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.Countries
import com.waymark.domain.logic.TimeAtPlace
import com.waymark.domain.logic.TimeText
import com.waymark.domain.logic.WeatherText
import com.waymark.domain.model.WeatherBasis
import com.waymark.domain.model.WeatherDay
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.Panel
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SecondaryButton
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.costs.OnlineExtrasModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * What to know on arrival, for the places this trip actually stops.
 *
 * "Actually stops" is doing the work. This screen used to list every city any
 * segment touched, in the order the segments happened, which meant a two-hour
 * connection got the same billing as the week that followed it — and only if
 * the city happened to be one of the nine the app had notes for. Destinations
 * are measured now: the place with the most hours in it comes first, and
 * anywhere else with a day or more follows. A connection is not a destination.
 */
@Composable
fun InsightsScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weather by viewModel.weather.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    var explainingOnline by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(weather.online, state.dossier?.segments) { viewModel.weather.refresh() }

    val destinations = state.dossier
        ?.let { TimeAtPlace.destinations(it, travelerFilter = state.travelerFilter) }
        .orEmpty()

    ScreenScaffold(
        title = "On arrival",
        onBack = onBack,
        spacing = WaymarkSpacing.small,
    ) {
        if (destinations.isEmpty()) {
            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Add two bookings and Waymark can work out where this trip " +
                        "actually spends its time.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
            return@ScreenScaffold
        }

        Weather(
            weather = weather,
            onlineAllowed = weather.online,
            onTurnOn = { explainingOnline = true },
            onRefresh = viewModel.weather::refresh,
        )

        destinations.forEachIndexed { index, stay ->
            val country = Countries.of(stay.place)

            SectionHeader(
                label = stay.place.city.ifBlank { stay.place.name },
                trailing = {
                    Text(
                        text = TimeText.duration(stay.minutes),
                        style = Waymark.type.dataSmall,
                        color = if (index == 0) colors.accentAmber else colors.textDim,
                    )
                },
            )

            if (country == null) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No country notes bundled for ${stay.place.country.ifBlank { "here" }}.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
            } else {
                Panel(modifier = Modifier.fillMaxWidth()) {
                    Fact("Money", country.currency)
                    Fact("Power", country.power)
                    Fact("Emergency", country.emergencyNumber)
                    Fact("Roads", country.driving)
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.small))
        }

        Footnote(
            "Country facts, bundled and offline. Several countries run separate " +
                "police, fire and ambulance numbers; where one number reaches all " +
                "three from a foreign handset, that is the one shown."
        )
        Spacer(Modifier.height(WaymarkSpacing.section))
    }

    if (explainingOnline) {
        OnlineExtrasModal(
            enabled = weather.online,
            onSet = viewModel.weather::setOnline,
            onDismiss = { explainingOnline = false },
        )
    }
}

/** The weather where the trip will be, day by day, or how to have it. */
@Composable
private fun Weather(
    weather: WeatherUiState,
    onlineAllowed: Boolean,
    onTurnOn: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = Waymark.colors
    SectionHeader(label = "Weather")
    if (!onlineAllowed && weather.stops.all { it.days.isEmpty() }) {
        Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "The forecast where the trip will be, fetched only if you let Waymark go online.",
                style = Waymark.type.hint,
                color = colors.textDim,
            )
        }
        SecondaryButton(text = "Show the weather", onClick = onTurnOn, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(WaymarkSpacing.small))
        return
    }
    if (weather.stops.isEmpty()) {
        EmptyLine("No days still to come with a place to ask about.")
    }
    weather.stops.forEach { stop ->
        Panel(modifier = Modifier.fillMaxWidth()) {
            FieldLabel(stop.stop.label)
            Spacer(Modifier.height(WaymarkSpacing.tight))
            if (stop.days.isEmpty()) {
                Text(
                    text = if (weather.updating) "Fetching…" else "Not fetched yet.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
            stop.days.forEach { day -> WeatherRow(day) }
        }
    }
    val basis = weather.stops.flatMap { it.days }.map { it.basis }.toSet()
    Footnote(
        buildString {
            if (WeatherBasis.FORECAST in basis) append("Forecasts from Open-Meteo. ")
            if (WeatherBasis.LAST_YEAR in basis) {
                append("Beyond two weeks there is no forecast, so those days show the same dates last year. ")
            }
            weather.note?.let { append(it) }
        }.ifBlank { "From Open-Meteo, kept on the phone for when there is no signal." }
    )
    if (onlineAllowed) {
        SecondaryButton(
            text = if (weather.updating) "Updating…" else "Update the weather",
            onClick = onRefresh,
            enabled = !weather.updating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(WaymarkSpacing.small))
}

@Composable
private fun WeatherRow(day: WeatherDay) {
    val colors = Waymark.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        Text(
            text = TimeText.dayCompact(day.date),
            style = Waymark.type.dataSmall,
            color = colors.textDim,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = WeatherText.describe(day.code),
            style = Waymark.type.bodySmall,
            color = if (WeatherText.isWet(day.code)) colors.accentAmber else colors.textBody,
            modifier = Modifier.weight(1.4f),
        )
        Text(
            text = "${WeatherText.temperature(day.highC)} / ${WeatherText.temperature(day.lowC)}",
            style = Waymark.type.data,
            color = colors.textHeading,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f),
        )
        Text(
            text = day.rainChance?.let { "$it%" }
                ?: day.rainMm?.let { if (it < 0.5) "dry" else "${Math.round(it)} mm" }.orEmpty(),
            style = Waymark.type.dataSmall,
            color = colors.textDim,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.7f),
        )
    }
    if (day.basis == WeatherBasis.LAST_YEAR) {
        Text(text = "last year", style = Waymark.type.hint, color = colors.textFaint)
    }
    Spacer(Modifier.height(WaymarkSpacing.tight))
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FieldLabel(label)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = Waymark.type.bodySmall,
                color = Waymark.colors.textBody,
                textAlign = TextAlign.Start,
            )
        }
    }
    Spacer(Modifier.height(WaymarkSpacing.snug))
}
