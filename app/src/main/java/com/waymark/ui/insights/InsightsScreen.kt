package com.waymark.ui.insights

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.data.catalog.DestinationGuide
import com.waymark.data.catalog.DestinationInsights
import com.waymark.domain.model.IdeaKind
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.Panel
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.components.SectionHeader
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel

/**
 * Local knowledge for where this trip actually goes: the practical facts, in
 * the order a traveler needs them on arrival.
 */
@Composable
fun InsightsScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors

    val cities = state.dossier?.segments
        ?.mapNotNull { segment ->
            DestinationInsights.forCity(segment.destination.city)
                ?: DestinationInsights.forAirport(segment.destination.code)
        }
        ?.distinctBy { it.city }
        .orEmpty()

    ScreenScaffold(
        title = "On arrival",
        onBack = onBack,
        spacing = WaymarkSpacing.small,
    ) {

        if (cities.isEmpty()) {
            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No notes bundled for this destination yet.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }
        }

        cities.forEach { insight ->
            SectionHeader(insight.city)
            Panel(modifier = Modifier.fillMaxWidth()) {
                Fact("Money", insight.currency)
                Fact("Power", "${insight.plugTypes} · ${insight.voltage}")
                Fact("Emergency", insight.emergencyNumber)
                Fact("Water", insight.tapWater)
                Fact("Tipping", insight.tipping)
                Spacer(Modifier.height(WaymarkSpacing.small))
                Hairline()
                Spacer(Modifier.height(WaymarkSpacing.small))
                Fact("From the airport", insight.airportTransfer)
                Fact("Getting around", insight.transitNote)
                Fact("Season", insight.seasonNote)
                Fact("A word", insight.greeting)
            }

            val guide = DestinationGuide.forCity(insight.city)
            val dishes = guide.filter { it.kind == IdeaKind.EAT }
            val seeing = guide.filter { it.kind != IdeaKind.EAT }

            if (dishes.isNotEmpty()) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    FieldLabel("Food to try")
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    dishes.forEach { entry ->
                        Text(
                            text = entry.title,
                            style = Waymark.type.bodySmall,
                            color = colors.textHeading,
                        )
                        Text(
                            text = entry.note,
                            style = Waymark.type.hint,
                            color = colors.textDim,
                        )
                        Spacer(Modifier.height(WaymarkSpacing.snug))
                    }
                    Text(
                        text = "Add any of these to the trip's list from the Ideas tab.",
                        style = Waymark.type.hint,
                        color = colors.textFaint,
                    )
                }
            }

            if (seeing.isNotEmpty()) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    FieldLabel("Worth the walk")
                    Spacer(Modifier.height(WaymarkSpacing.snug))
                    seeing.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = entry.title,
                                style = Waymark.type.bodySmall,
                                color = colors.textHeading,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = listOfNotNull(
                                    entry.priceBand?.label,
                                    entry.typicalMinutes?.let { "${it}m" },
                                ).joinToString(" · "),
                                style = Waymark.type.fieldLabel,
                                color = colors.textFaint,
                            )
                        }
                        Text(
                            text = entry.note,
                            style = Waymark.type.hint,
                            color = colors.textDim,
                        )
                        Spacer(Modifier.height(WaymarkSpacing.snug))
                    }
                }
            }

            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                FieldLabel("Where to stay near")
                Spacer(Modifier.height(WaymarkSpacing.snug))
                insight.neighbourhoods.forEach { neighbourhood ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = neighbourhood.name,
                            style = Waymark.type.bodySmall,
                            color = colors.textHeading,
                        )
                        Text(
                            text = neighbourhood.character,
                            style = Waymark.type.hint,
                            color = colors.textDim,
                            modifier = Modifier.padding(start = WaymarkSpacing.small),
                        )
                    }
                    Spacer(Modifier.height(WaymarkSpacing.tight))
                }
            }
        }


    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = WaymarkSpacing.small)) {
        FieldLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = Waymark.type.bodySmall,
            color = Waymark.colors.textBody,
        )
    }
}
