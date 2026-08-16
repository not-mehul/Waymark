package com.waymark.ui.map

import com.waymark.domain.logic.LatLon

/** A place worth drawing: an airport, a hotel, a restaurant on a hill. */
data class ChartPlace(
    val id: String,
    val label: String,
    val position: LatLon,
    val kind: Kind,
) {
    /**
     * Ranking, not just styling. When labels compete for room the station wins,
     * because a traveler looking at a trip map is orienting by the airports.
     */
    enum class Kind(val rank: Int) { STATION(0), STAY(1), STOP(2) }
}

/** A leg between two places. Flights arc; ground legs run straight and dashed. */
data class ChartRoute(
    val id: String,
    val from: LatLon,
    val to: LatLon,
    val flying: Boolean,
    val aircraft: LatLon? = null,
    val emphasis: Boolean = false,
)

/** Flat chart or globe. The same data, projected two ways. */
enum class MapProjection(val label: String) { FLAT("Chart"), GLOBE("Globe") }
