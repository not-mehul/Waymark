package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeoTest {

    private val sfo = LatLon(37.6213, -122.3790)
    private val lhr = LatLon(51.4700, -0.4543)
    private val syd = LatLon(-33.9399, 151.1753)
    private val lax = LatLon(33.9416, -118.4085)

    @Test
    fun `great-circle distance matches published figures`() {
        // SFO–LHR is about 8 600 km; allow one percent for the sphere model.
        assertEquals(8620.0, Geo.distanceKm(sfo, lhr), 90.0)
        // SYD–LAX is about 12 050 km.
        assertEquals(12050.0, Geo.distanceKm(syd, lax), 130.0)
    }

    @Test
    fun `distance is symmetric and zero at a point`() {
        assertEquals(Geo.distanceKm(sfo, lhr), Geo.distanceKm(lhr, sfo), 0.001)
        assertEquals(0.0, Geo.distanceKm(sfo, sfo), 0.001)
    }

    @Test
    fun `initial bearing from San Francisco to London runs north of east`() {
        val bearing = Geo.bearingDegrees(sfo, lhr)
        assertTrue("bearing was $bearing", bearing in 20.0..45.0)
    }

    @Test
    fun `interpolation returns the endpoints and stays on the sphere`() {
        val start = Geo.interpolate(sfo, lhr, 0.0)
        val end = Geo.interpolate(sfo, lhr, 1.0)
        assertEquals(sfo.latitude, start.latitude, 0.0001)
        assertEquals(lhr.longitude, end.longitude, 0.0001)

        // The midpoint of a northern-hemisphere transatlantic leg is higher in
        // latitude than either end: that is the whole point of a great circle.
        val middle = Geo.interpolate(sfo, lhr, 0.5)
        assertTrue(middle.latitude > maxOf(sfo.latitude, lhr.latitude))
    }

    @Test
    fun `halves of an arc are equal length`() {
        val middle = Geo.interpolate(sfo, lhr, 0.5)
        val first = Geo.distanceKm(sfo, middle)
        val second = Geo.distanceKm(middle, lhr)
        assertTrue(abs(first - second) < 1.0)
    }

    @Test
    fun `arc sampling has the requested resolution and ordering`() {
        val arc = Geo.arc(sfo, lhr, 24)
        assertEquals(25, arc.size)
        assertEquals(sfo.latitude, arc.first().latitude, 0.0001)
        assertEquals(lhr.latitude, arc.last().latitude, 0.0001)
    }

    @Test
    fun `projection maps the world into the unit square`() {
        val nullIsland = Geo.project(LatLon(0.0, 0.0))
        assertEquals(0.5, nullIsland.x, 0.0001)
        assertEquals(0.5, nullIsland.y, 0.0001)

        val northWest = Geo.project(LatLon(80.0, -180.0))
        assertEquals(0.0, northWest.x, 0.0001)
        assertTrue(northWest.y in 0.0..0.2)
    }

    @Test
    fun `projection round-trips`() {
        val point = LatLon(51.47, -0.4543)
        val back = Geo.unproject(Geo.project(point))
        assertEquals(point.latitude, back.latitude, 0.0001)
        assertEquals(point.longitude, back.longitude, 0.0001)
    }

    @Test
    fun `date line crossing is detected`() {
        assertTrue(Geo.crossesDateLine(LatLon(0.0, 179.0), LatLon(0.0, -179.0)))
        assertTrue(!Geo.crossesDateLine(LatLon(0.0, 10.0), LatLon(0.0, -10.0)))
    }

    @Test
    fun `bounds cover every point`() {
        val bounds = Geo.bounds(listOf(sfo, lhr, syd))
        requireNotNull(bounds)
        assertTrue(bounds.minLat <= syd.latitude)
        assertTrue(bounds.maxLat >= lhr.latitude)
        assertTrue(bounds.maxLon >= syd.longitude)
    }

    @Test
    fun `distance formatting switches unit and precision`() {
        assertEquals("8.6 km", Geo.formatDistance(8.6))
        assertEquals("120 km", Geo.formatDistance(120.4))
        assertTrue(Geo.formatDistance(100.0, metric = false).endsWith("mi"))
    }
}
