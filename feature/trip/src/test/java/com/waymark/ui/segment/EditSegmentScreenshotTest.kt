package com.waymark.ui.segment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.domain.model.ThemeMode
import com.waymark.ui.theme.WaymarkTheme
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A whole screen, from the sample trip, in both themes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi")
class EditSegmentScreenshotTest {

    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val flight = sample.segments.filterIsInstance<Segment.Flight>().first()

    @Test
    fun `editing a flight in Dusk`() = capture(ThemeMode.DUSK)

    @Test
    fun `editing a flight in Dawn`() = capture(ThemeMode.DAWN)

    private fun capture(mode: ThemeMode) =
        captureRoboImage("src/test/screenshots/edit-flight-${mode.name.lowercase()}.png") {
            WaymarkTheme(stored = mode) {
                EditSegmentScreen(original = flight, party = sample.travelers, onCancel = {}, onSave = {})
            }
        }
}
