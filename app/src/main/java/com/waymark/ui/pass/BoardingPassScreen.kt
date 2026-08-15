package com.waymark.ui.pass

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waymark.domain.logic.Bcbp
import com.waymark.domain.logic.TimeText
import com.waymark.domain.model.Segment
import com.waymark.ui.components.EditorialNote
import com.waymark.ui.components.FieldLabel
import com.waymark.ui.components.GhostIconButton
import com.waymark.ui.components.Hairline
import com.waymark.ui.components.Panel
import com.waymark.ui.components.SectionLabel
import com.waymark.ui.components.WaymarkBackdrop
import com.waymark.ui.components.WaymarkIcons
import com.waymark.ui.components.cornerBrackets
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkShapes
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.trip.TripViewModel
import java.time.Instant

/**
 * The pass, offline. The screen holds itself bright and awake because a
 * scanner is about to read it, and everything on it comes from the sealed
 * payload stored on this device.
 */
@Composable
fun BoardingPassScreen(
    passId: String,
    viewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = Waymark.colors
    val pass = state.passes.firstOrNull { it.id == passId }
    val segment = state.dossier?.segments?.firstOrNull { it.id == pass?.segmentId }
    val flight = segment as? Segment.Flight
    val parsed = pass?.let { Bcbp.parse(it.barcodePayload) }

    KeepScreenBright()

    WaymarkBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WaymarkSpacing.screenHorizontal),
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
                SectionLabel("Boarding pass")
            }

            if (pass == null) {
                Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This pass is no longer stored.",
                        style = Waymark.type.hint,
                        color = colors.textDim,
                    )
                }
                return@Column
            }

            Spacer(Modifier.height(WaymarkSpacing.medium))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WaymarkShapes.panel)
                    .background(colors.panel)
                    .cornerBrackets(colors.amber(0.5f))
                    .padding(WaymarkSpacing.large),
                verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        FieldLabel("Passenger")
                        Text(
                            text = pass.passengerName,
                            style = Waymark.type.cardTitle,
                            color = colors.textHeading,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        FieldLabel("Flight")
                        Text(
                            text = pass.designator,
                            style = Waymark.type.cardTitle,
                            color = colors.accentAmber,
                        )
                    }
                }

                Hairline()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = pass.origin,
                            style = Waymark.type.verdict,
                            color = colors.textHeading,
                        )
                        flight?.let {
                            Text(
                                text = TimeText.clock(it.start),
                                style = Waymark.type.data,
                                color = colors.textDim,
                            )
                        }
                    }
                    Text(
                        text = "→",
                        style = Waymark.type.stat,
                        color = colors.textFaint,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = pass.destination,
                            style = Waymark.type.verdict,
                            color = colors.textHeading,
                        )
                        flight?.let {
                            Text(
                                text = TimeText.clock(it.end),
                                style = Waymark.type.data,
                                color = colors.textDim,
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    PassField("Seat", pass.seat ?: "—", Modifier.weight(1f))
                    PassField("Group", pass.boardingGroup ?: "—", Modifier.weight(1f))
                    PassField("Sequence", pass.sequenceNumber ?: "—", Modifier.weight(1f))
                    PassField("Gate", pass.gate ?: "—", Modifier.weight(1f))
                }

                pass.boardingTimeMillis?.let { boarding ->
                    val zone = Segment.zoneOrUtc(flight?.startZoneId ?: "UTC")
                    Text(
                        text = "Boards ${TimeText.clock(Instant.ofEpochMilli(boarding).atZone(zone))}" +
                            " · ${TimeText.relative(boarding, state.nowMillis)}",
                        style = Waymark.type.dataSmall,
                        color = colors.accentBright,
                    )
                }

                Hairline()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug),
                ) {
                    Code39Barcode(
                        value = pass.sequenceNumber?.takeIf { it.isNotBlank() }
                            ?: parsed?.recordLocator
                            ?: pass.designator,
                        barColor = colors.textStrong,
                        backgroundColor = colors.input,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = pass.barcodePayload,
                        style = Waymark.type.dataSmall,
                        color = colors.textFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(WaymarkSpacing.medium))

            Panel(faint = true, modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Stored payload")
                Spacer(Modifier.height(WaymarkSpacing.snug))
                parsed?.let {
                    Text(
                        text = listOf(
                            "Record locator ${it.recordLocator}",
                            "Cabin ${it.cabin}",
                            "Day ${it.julianDate} of the year",
                        ).joinToString("  ·  "),
                        style = Waymark.type.dataSmall,
                        color = colors.textDim,
                    )
                } ?: Text(
                    text = "Imported payload, kept exactly as issued.",
                    style = Waymark.type.hint,
                    color = colors.textDim,
                )
            }

            Spacer(Modifier.height(WaymarkSpacing.small))

            EditorialNote(
                term = "At the gate",
                body = "Waymark prints a Code 39 symbol of the reference for offline use. " +
                    "Where an airline requires its own two-dimensional symbol, import the " +
                    "airline's pass image and it is shown here instead.",
            )
            Spacer(Modifier.height(WaymarkSpacing.section))
        }
    }
}

@Composable
private fun PassField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = Waymark.type.data,
            color = Waymark.colors.textStrong,
        )
    }
}

/** A pass that dims mid-scan is a pass that gets scanned twice. */
@Composable
private fun KeepScreenBright() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
