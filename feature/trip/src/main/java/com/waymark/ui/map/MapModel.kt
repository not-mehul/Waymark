package com.waymark.ui.map

import com.waymark.domain.logic.LatLon

/**
 * A place worth drawing: an airport, a hotel, a restaurant on a hill.
 *
 * [id] is the mark's own and unique on the map — a flight has a mark at each
 * end, and both belong to the same booking — while [segmentId] is the booking
 * a tap opens, or null for a saved idea that is not on the timeline yet.
 */
data class ChartPlace(
    val id: String,
    val label: String,
    val position: LatLon,
    val kind: Kind,
    val segmentId: String? = null,
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
)

/** Flat chart or globe. The same data, projected two ways. */
enum class MapProjection(val label: String) { FLAT("Chart"), GLOBE("Globe") }
