package com.waymark.domain.logic

import com.waymark.domain.model.Place
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** Latitude/longitude in degrees. */
data class LatLon(val latitude: Double, val longitude: Double)

/** Projected map coordinate, both axes normalised to 0..1. */
data class MapPoint(val x: Double, val y: Double)

data class BoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    val centre: LatLon get() = LatLon((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    val latSpan: Double get() = maxLat - minLat
    val lonSpan: Double get() = maxLon - minLon

    fun padded(fraction: Double): BoundingBox {
        val padLat = max(latSpan * fraction, 0.35)
        val padLon = max(lonSpan * fraction, 0.35)
        return BoundingBox(
            minLat = max(-85.0, minLat - padLat),
            minLon = max(-180.0, minLon - padLon),
            maxLat = min(85.0, maxLat + padLat),
            maxLon = min(180.0, maxLon + padLon),
        )
    }
}

object Geo {

    const val EARTH_RADIUS_KM = 6371.0088

    fun toRadians(degrees: Double): Double = degrees * Math.PI / 180.0
    fun toDegrees(radians: Double): Double = radians * 180.0 / Math.PI

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = toRadians(b.latitude - a.latitude)
        val dLon = toRadians(b.longitude - a.longitude)
        val lat1 = toRadians(a.latitude)
        val lat2 = toRadians(b.latitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    fun distanceKm(a: Place, b: Place): Double =
        distanceKm(LatLon(a.latitude, a.longitude), LatLon(b.latitude, b.longitude))

    /** Initial bearing from [a] to [b], degrees clockwise from north. */
    fun bearingDegrees(a: LatLon, b: LatLon): Double {
        val lat1 = toRadians(a.latitude)
        val lat2 = toRadians(b.latitude)
        val dLon = toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Point at [fraction] along the great circle from [a] to [b].
     * Used both for drawing route arcs and for placing an aircraft by progress.
     */
    fun interpolate(a: LatLon, b: LatLon, fraction: Double): LatLon {
        val lat1 = toRadians(a.latitude)
        val lon1 = toRadians(a.longitude)
        val lat2 = toRadians(b.latitude)
        val lon2 = toRadians(b.longitude)
        val d = 2 * asin(
            min(
                1.0,
                sqrt(
                    sin((lat2 - lat1) / 2).let { it * it } +
                        cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).let { it * it }
                )
            )
        )
        if (d < 1e-9) return a
        val f = fraction.coerceIn(0.0, 1.0)
        val aCoef = sin((1 - f) * d) / sin(d)
        val bCoef = sin(f * d) / sin(d)
        val x = aCoef * cos(lat1) * cos(lon1) + bCoef * cos(lat2) * cos(lon2)
        val y = aCoef * cos(lat1) * sin(lon1) + bCoef * cos(lat2) * sin(lon2)
        val z = aCoef * sin(lat1) + bCoef * sin(lat2)
        return LatLon(
            latitude = toDegrees(atan2(z, sqrt(x * x + y * y))),
            longitude = toDegrees(atan2(y, x)),
        )
    }

    /** Sampled great-circle path, dense enough to look like a curve on screen. */
    fun arc(a: LatLon, b: LatLon, samples: Int = 48): List<LatLon> {
        val steps = samples.coerceAtLeast(2)
        return (0..steps).map { interpolate(a, b, it.toDouble() / steps) }
    }

    /**
     * Web-Mercator-ish projection into 0..1 space. Latitude is clamped at ±85°
     * because the projection runs to infinity at the poles.
     */
    fun project(point: LatLon): MapPoint {
        val lat = point.latitude.coerceIn(-85.05112878, 85.05112878)
        val x = (point.longitude + 180.0) / 360.0
        val sinLat = sin(toRadians(lat))
        val y = 0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)
        return MapPoint(x, y)
    }

    fun unproject(point: MapPoint): LatLon {
        val lon = point.x * 360.0 - 180.0
        val n = Math.PI * (1 - 2 * point.y)
        return LatLon(toDegrees(atan(sinh(n))), lon)
    }

    fun bounds(points: List<LatLon>): BoundingBox? {
        if (points.isEmpty()) return null
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        points.forEach {
            minLat = min(minLat, it.latitude)
            maxLat = max(maxLat, it.latitude)
            minLon = min(minLon, it.longitude)
            maxLon = max(maxLon, it.longitude)
        }
        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    /** True when a route crosses the antimeridian and must be drawn in two pieces. */
    fun crossesDateLine(a: LatLon, b: LatLon): Boolean = abs(a.longitude - b.longitude) > 180.0

    fun formatDistance(km: Double, metric: Boolean = true): String = if (metric) {
        if (km < 10) String.format("%.1f km", km) else "${km.toInt()} km"
    } else {
        val miles = km * 0.621371
        if (miles < 10) String.format("%.1f mi", miles) else "${miles.toInt()} mi"
    }

    /** Mercator y at a given latitude, unclamped — handy for graticule spacing. */
    internal fun mercatorY(latitude: Double): Double =
        ln(tan(Math.PI / 4 + toRadians(latitude.coerceIn(-85.0, 85.0)) / 2))
}
