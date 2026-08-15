package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PartySplitAnalyzerTest {

    private val bundle = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(
        trip = bundle.trip,
        party = TripParty(bundle.trip.id, bundle.travelers),
        segments = bundle.segments,
    )

    @Test
    fun `finds the day the party travels separately`() {
        val windows = PartySplitAnalyzer.splitWindows(dossier)
        assertTrue("expected at least one split", windows.isNotEmpty())

        val trainSplit = windows.firstOrNull { window ->
            window.groups.any { it.segmentId == "seg-eurostar-julian" }
        }
        assertTrue("the morning Eurostar should open a split", trainSplit != null)
    }

    @Test
    fun `a solo traveler never splits`() {
        val solo = dossier.copy(
            party = TripParty(dossier.trip.id, listOf(bundle.travelers.first()))
        )
        assertTrue(PartySplitAnalyzer.splitWindows(solo).isEmpty())
    }

    @Test
    fun `windows are merged rather than repeated per boundary`() {
        val windows = PartySplitAnalyzer.splitWindows(dossier)
        windows.zipWithNext().forEach { (first, second) ->
            assertTrue(first.endMillis <= second.startMillis)
        }
    }

    @Test
    fun `coverage counts what each traveler is actually on`() {
        val coverage = PartySplitAnalyzer.coverage(dossier).associateBy { it.traveler.id }
        val mara = coverage.getValue("trav-mara")
        val julian = coverage.getValue("trav-julian")

        assertTrue(mara.hasLodging && julian.hasLodging)
        assertEquals(2, mara.flightCount)
        assertEquals(2, julian.flightCount)
        assertTrue(mara.missingFromSegments.any { it.id == "seg-eurostar-julian" })
        assertTrue(julian.missingFromSegments.any { it.id == "seg-eurostar-mara" })
    }

    @Test
    fun `a traveler with nothing booked is called out`() {
        val stranger = bundle.travelers.first()
            .copy(id = "trav-stranger", fullName = "Nadia Roth", nickname = null)
        val withStranger = dossier.copy(
            party = TripParty(dossier.trip.id, bundle.travelers + stranger)
        )
        val notes = PartySplitAnalyzer.warnings(withStranger)
        assertTrue(notes.any { it.contains("Nadia") })
    }
}
