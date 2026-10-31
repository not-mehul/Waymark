package com.waymark.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.waymark.domain.logic.Measure
import com.waymark.domain.model.ThemeMode
import com.waymark.ui.charts.BarSeries
import com.waymark.ui.charts.Meter
import com.waymark.ui.charts.SplitBar
import com.waymark.ui.theme.WaymarkSpacing
import com.waymark.ui.theme.WaymarkTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The component kit, drawn in both themes: a change to a token, a shape or a
 * component shows up here as a picture that no longer matches.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ComponentsScreenshotTest {

    @Test
    fun `the kit in Dusk`() = capture(ThemeMode.DUSK)

    @Test
    fun `the kit in Dawn`() = capture(ThemeMode.DAWN)

    private fun capture(mode: ThemeMode) =
        captureRoboImage("src/test/screenshots/components-${mode.name.lowercase()}.png") {
            WaymarkTheme(stored = mode) {
                WaymarkBackdrop { Gallery() }
            }
        }
}

/** A phone: Pixel-sized, at a common density. */
const val PHONE = "w411dp-h914dp-420dpi"

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(WaymarkSpacing.large),
        verticalArrangement = Arrangement.spacedBy(WaymarkSpacing.medium),
    ) {
        SectionHeader("Buttons")
        PrimaryButton(text = "Add a flight", onClick = {}, icon = WaymarkIcons.Plane)
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            SecondaryButton(text = "Share", onClick = {}, modifier = Modifier.weight(1f))
            MutedButton(text = "Keep", onClick = {}, modifier = Modifier.weight(1f))
            DangerButton(text = "Remove", onClick = {}, modifier = Modifier.weight(1f))
        }
        PrimaryButton(text = "Not yet", onClick = {}, enabled = false)

        SectionHeader("Controls")
        SegmentedToggle(options = listOf("Flat", "Globe", "List"), selectedIndex = 0, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.snug)) {
            OptionChip(text = "3h", selected = true, onClick = {})
            OptionChip(text = "1h", selected = false, onClick = {})
            OptionChip(text = "Off", selected = false, onClick = {})
        }
        NoticeBanner(
            icon = WaymarkIcons.Clock,
            headline = "Check in online for TP 1351",
            detail = "Check-in usually opens 24 hours before departure",
        )

        SectionHeader("Surfaces and charts")
        Panel {
            SectionLabel("Lisbon and Porto")
            Row(horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.large)) {
                Stat(value = "9", label = "nights", emphasised = true)
                Stat(value = "4", label = "travelers")
                Stat(value = "2", label = "cities")
            }
        }
        Meter(fraction = 0.6f, label = "Packed", value = "6 of 10")
        BarSeries(
            measures = listOf(
                Measure("Lisbon", 5.0, "5 nights", emphasis = true),
                Measure("Porto", 3.0, "3 nights"),
                Measure("Sintra", 1.0, "1 night"),
            ),
        )
        SplitBar(parts = listOf("Air" to 3.0, "Rail" to 1.5, "Road" to 0.5))
        EmptyLine("Nothing booked yet.")
    }
}
