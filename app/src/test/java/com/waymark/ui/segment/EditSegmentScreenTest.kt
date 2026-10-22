package com.waymark.ui.segment

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.Segment
import com.waymark.ui.theme.WaymarkTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The edit screen says why it will not save, and saves what was typed. */
@RunWith(AndroidJUnit4::class)
class EditSegmentScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val flight = sample.segments.filterIsInstance<Segment.Flight>().first()

    @Test
    fun `a flight number that is not one stops the save and says why`() {
        var saved: Segment? = null
        compose.setContent {
            WaymarkTheme {
                EditSegmentScreen(original = flight, party = sample.travelers, onCancel = {}, onSave = { saved = it })
            }
        }

        compose.onNodeWithText(flight.designator).performTextReplacement("HELLO")
        compose.onNodeWithText("That is not a flight number", substring = true).performScrollTo()
        compose.onNodeWithText("SAVE CHANGES").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithText("HELLO").performTextReplacement("BA287")
        compose.onNodeWithText("SAVE CHANGES").performScrollTo().assertIsEnabled().performClick()
        assertEquals("287", (saved as Segment.Flight).flightNumber)
    }
}
