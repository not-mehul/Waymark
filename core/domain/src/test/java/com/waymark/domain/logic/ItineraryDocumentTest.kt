package com.waymark.domain.logic

import com.waymark.data.catalog.SampleItinerary
import com.waymark.domain.logic.ItineraryDocument.Block
import com.waymark.domain.model.Expense
import com.waymark.domain.model.Money
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.TripParty
import com.waymark.domain.model.WeatherBasis
import com.waymark.domain.model.WeatherDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItineraryDocumentTest {

    private val sample = SampleItinerary.build(LocalDate.of(2026, 5, 14))
    private val dossier = TripDossier(sample.trip, TripParty(sample.trip.id, sample.travelers), sample.segments)

    @Test
    fun `it opens with the trip and lists every booking once, under its day`() {
        val blocks = ItineraryDocument.build(ItineraryDocument.Payload(dossier))
        assertTrue(blocks.first() is Block.Title)
        assertEquals(sample.segments.size, blocks.count { it is Block.Entry })
        val days = blocks.filterIsInstance<Block.Day>().map { it.text }
        assertEquals(days.distinct(), days)
    }

    @Test
    fun `every booking reference is in it`() {
        val text = ItineraryDocument.build(ItineraryDocument.Payload(dossier))
            .filterIsInstance<Block.Entry>().mapNotNull { it.reference }.joinToString()
        sample.segments.mapNotNull { it.confirmationCode }.forEach { assertTrue(it, it in text) }
    }

    @Test
    fun `a day carries its weather when there is some`() {
        val first = sample.segments.minBy { it.startEpochMillis }.start.toLocalDate()
        val weather = mapOf(first to WeatherDay(first, 2, 21.0, 13.0, rainChance = 10, basis = WeatherBasis.FORECAST))
        val day = ItineraryDocument.build(ItineraryDocument.Payload(dossier, weather = weather))
            .filterIsInstance<Block.Day>().first()
        assertTrue(day.weather.orEmpty(), day.weather.orEmpty().contains("Partly cloudy"))
    }

    @Test
    fun `costs are included when anything was spent, with who pays whom`() {
        val payer = sample.travelers.first()
        val expense = Expense(
            id = "e", tripId = sample.trip.id, description = "Dinner", amount = Money(12_000, "EUR"),
            paidBy = payer.id, spentOn = sample.trip.startDate(),
        )
        val summary = CostSplitter.summarize(listOf(expense), sample.travelers.map { it.id }, "EUR", emptyList())
        val blocks = ItineraryDocument.build(ItineraryDocument.Payload(dossier, costs = summary))
        assertTrue(Block.Section("Costs") in blocks)
        assertTrue(blocks.filterIsInstance<Block.Row>().any { it.label.endsWith("pays ${payer.displayName}") })
    }

    @Test
    fun `nothing spent, no costs section`() {
        val summary = CostSplitter.summarize(emptyList(), sample.travelers.map { it.id }, "EUR", emptyList())
        assertTrue(Block.Section("Costs") !in ItineraryDocument.build(ItineraryDocument.Payload(dossier, costs = summary)))
    }

    @Test
    fun `the file is named for its date and trip`() {
        assertTrue(ItineraryDocument.fileName(dossier).matches(Regex("""2026-05-\d\d-[a-z0-9-]+\.pdf""")))
    }
}
