package com.waymark.domain.model

import java.time.LocalDate

/** Where a day's weather comes from. */
enum class WeatherBasis {
    /** A forecast for the day itself — only the next two weeks have one. */
    FORECAST,

    /** What the same date was like a year ago: the best guess further out. */
    LAST_YEAR,
}

/** One day's weather at one place, in Celsius and millimetres. */
data class WeatherDay(
    val date: LocalDate,
    /** The WMO weather code. */
    val code: Int,
    val highC: Double,
    val lowC: Double,
    /** Chance of rain, in percent; forecasts only. */
    val rainChance: Int? = null,
    /** Rain that fell, in millimetres; last year's record only. */
    val rainMm: Double? = null,
    val basis: WeatherBasis,
)
