package com.waymark.online

import com.waymark.domain.model.WeatherBasis
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two services' replies, as they send them, read into the app's terms. */
class ParsingTest {

    private val rates = """
        {"amount":1.0,"base":"EUR","date":"2026-10-30","rates":{"GBP":0.8338,"JPY":171.2,"USD":1.1172}}
    """.trimIndent()

    @Test
    fun `the day's rates are read against the euro`() {
        val live = LiveRates.parse(rates)
        assertEquals(LocalDate.of(2026, 10, 30), live.date)
        assertEquals(1.0, live.perEuro.getValue("EUR"), 0.0)
        assertEquals(0.8338, live.perEuro.getValue("GBP"), 0.0)
    }

    @Test
    fun `a rate between two currencies goes through the euro`() {
        val perPound = LiveRates.parse(rates).perUnitIn("GBP", setOf("EUR", "USD", "JPY", "XYZ"))
        assertEquals(0.8338, perPound.getValue("EUR"), 1e-9)
        assertEquals(0.8338 / 1.1172, perPound.getValue("USD"), 1e-9)
        assertEquals(0.8338 / 171.2, perPound.getValue("JPY"), 1e-9)
        // A currency the bank does not publish has no rate, rather than a wrong one.
        assertNull(perPound["XYZ"])
    }

    @Test
    fun `settling in a currency the bank does not publish gives no rates at all`() {
        assertTrue(LiveRates.parse(rates).perUnitIn("AED", setOf("EUR")).isEmpty())
    }

    @Test(expected = IOException::class)
    fun `a reply without rates is refused`() {
        LiveRates.parse("""{"message":"not found"}""")
    }

    private val forecast = """
        {"latitude":38.72,"longitude":-9.14,"timezone":"Europe/Lisbon",
         "daily_units":{"time":"iso8601","weather_code":"wmo code"},
         "daily":{"time":["2026-05-14","2026-05-15","2026-05-16"],
                  "weather_code":[2,61,null],
                  "temperature_2m_max":[24.1,19.6,21.0],
                  "temperature_2m_min":[14.2,13.0,12.5],
                  "precipitation_probability_max":[5,80,null]}}
    """.trimIndent()

    @Test
    fun `a forecast is read day by day, and a day with no reading is left out`() {
        val days = OpenMeteo.parse(forecast, WeatherBasis.FORECAST)
        assertEquals(listOf(LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 15)), days.map { it.date })
        assertEquals(2, days[0].code)
        assertEquals(24.1, days[0].highC, 0.0)
        assertEquals(80, days[1].rainChance)
        assertTrue(days.all { it.basis == WeatherBasis.FORECAST })
    }

    @Test
    fun `last year's record carries rain fallen rather than a chance of it`() {
        val record = """
            {"daily":{"time":["2025-05-14"],"weather_code":[3],"temperature_2m_max":[22.0],
             "temperature_2m_min":[15.0],"precipitation_sum":[1.4]}}
        """.trimIndent()
        val day = OpenMeteo.parse(record, WeatherBasis.LAST_YEAR).single()
        assertEquals(1.4, day.rainMm!!, 0.0)
        assertNull(day.rainChance)
    }

    @Test(expected = IOException::class)
    fun `the service saying no is an error, not an empty week`() {
        OpenMeteo.parse("""{"error":true,"reason":"Daily API request limit exceeded."}""", WeatherBasis.FORECAST)
    }

    @Test
    fun `only the place, to a kilometre, and the dates are sent`() {
        val url = OpenMeteo.forecastUrl(38.7223, -9.1393, LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 16))
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=38.72&longitude=-9.14&"))
        assertTrue(url, "start_date=2026-05-14&end_date=2026-05-16" in url)
    }
}
