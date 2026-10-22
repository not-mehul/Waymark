package com.waymark.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.waymark.data.catalog.Places
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Place
import com.waymark.ui.components.OptionChip
import com.waymark.ui.components.ZonePickerModal
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * How the app has understood a typed place, said back before it is saved.
 *
 * "Paris" becomes Paris, France and Paris's clock; "Bloomsbury, London" finds
 * London; "the bakery by the hotel" stays as typed, on the clock of wherever
 * the trip is that day. When a name could be more than one city the others
 * are offered beside it, and a place nobody could look up can have its clock
 * set by hand — the one fact about it that moves a booking in time.
 */
@Composable
fun PlaceReading(
    text: String,
    context: PlanContext,
    chosen: Place?,
    onChoose: (Place?) -> Unit,
) {
    var readings by remember { mutableStateOf<List<Places.Resolved>>(emptyList()) }
    var pickingZone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(text, context) {
        if (text.isBlank()) {
            readings = emptyList()
            return@LaunchedEffect
        }
        // A beat after typing stops, and off the main thread: the gazetteer
        // is thirty thousand cities.
        delay(250)
        readings = withContext(Dispatchers.Default) {
            Places.candidates(text, context.near, context.zoneId)
        }
    }
    if (readings.isEmpty()) return

    val current = chosen ?: readings.first().place
    // A typed place whose clock was set by hand no longer equals the typed
    // reading it came from, and is still that reading.
    val currentReading = readings.firstOrNull { it.place == current }
    val currentMatch = currentReading?.match ?: Places.Match.TYPED
    val colors = Waymark.colors

    Column(verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight)) {
        Text(
            text = buildString {
                append("Read as ")
                append(if (currentMatch == Places.Match.TYPED) "\u201c${current.name}\u201d" else currentReading?.label)
                append(" · ")
                append(zoneName(current.timeZoneId))
            },
            style = Waymark.type.hint,
            color = colors.textDim,
        )
        if (readings.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.tight),
            ) {
                readings.forEach { reading ->
                    OptionChip(
                        text = if (reading.match == Places.Match.TYPED) "As typed" else reading.label,
                        selected = if (reading.match == Places.Match.TYPED) {
                            currentMatch == Places.Match.TYPED
                        } else {
                            reading.place == current
                        },
                        onClick = { onChoose(reading.place) },
                    )
                }
            }
        }
        if (currentMatch == Places.Match.TYPED) {
            Text(
                text = "Set its clock",
                style = Waymark.type.control,
                color = colors.accentAmber,
                modifier = Modifier.clickable(role = Role.Button) { pickingZone = true },
            )
        }
    }

    if (pickingZone) {
        ZonePickerModal(
            current = current.timeZoneId,
            suggested = listOf(context.zoneId),
            onDismiss = { pickingZone = false },
            onPick = { zone ->
                onChoose(current.copy(timeZoneId = zone))
                pickingZone = false
            },
        )
    }
}

/** "Europe/Paris" → "Paris time, UTC+1" — a zone named the way a reader says it. */
internal fun zoneName(zoneId: String): String {
    val city = zoneId.substringAfterLast('/').replace('_', ' ')
    return "$city time, ${TimeText.utcOffset(zoneId)}"
}
