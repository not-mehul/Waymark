package com.waymark.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.Countries
import com.waymark.domain.logic.TimeAtPlace
import com.waymark.domain.logic.TimeText
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.Panel
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
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
    val colors = Waymark.colors

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
