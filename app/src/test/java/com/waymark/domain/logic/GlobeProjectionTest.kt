package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GlobeProjectionTest {

    private val london = LatLon(51.47, -0.45)
    private val sydney = LatLon(-33.94, 151.18)
    private val sanFrancisco = LatLon(37.62, -122.38)

    @Test
    fun `the point facing the viewer lands in the middle of the disc`() {
        val point = Geo.orthographic(london, london.latitude, london.longitude)
        assertEquals(0.0, point.x, 1e-9)
        assertEquals(0.0, point.y, 1e-9)
        assertTrue(point.visible)
    }

    @Test
    fun `everything projects inside the unit disc`() {
        val samples = listOf(london, sydney, sanFrancisco, LatLon(0.0, 0.0), LatLon(89.0, 12.0))
        samples.forEach { place ->
            val point = Geo.orthographic(place, 20.0, 0.0)
            val radius = sqrt(point.x * point.x + point.y * point.y)
            assertTrue("$place projected to $radius", radius <= 1.0 + 1e-9)
        }
    }

    @Test
    fun `the far side of the world is culled`() {
        // Looking at London, Sydney is very nearly antipodal.
        val hidden = Geo.orthographic(sydney, london.latitude, london.longitude)
        assertTrue(!hidden.visible)

        val shown = Geo.orthographic(sydney, sydney.latitude, sydney.longitude)
        assertTrue(shown.visible)
    }

    @Test
    fun `the horizon is the boundary, not an arbitrary cut`() {
        // A point 90° away in longitude on the equator sits exactly on the rim.
        val rim = Geo.orthographic(LatLon(0.0, 90.0), 0.0, 0.0)
        assertEquals(1.0, abs(rim.x), 1e-9)
        assertTrue(rim.visible)

        val justBeyond = Geo.orthographic(LatLon(0.0, 90.5), 0.0, 0.0)
        assertTrue(!justBeyond.visible)
    }

    @Test
    fun `north is up and east is right when looking at the equator`() {
        val north = Geo.orthographic(LatLon(30.0, 0.0), 0.0, 0.0)
        val east = Geo.orthographic(LatLon(0.0, 30.0), 0.0, 0.0)
        assertTrue("north should be positive y", north.y > 0)
        assertTrue("east should be positive x", east.x > 0)
    }

    @Test
    fun `a globe arc keeps its ends and reports crossing the horizon`() {
        val arc = Geo.globeArc(sanFrancisco, sydney, sanFrancisco.latitude, sanFrancisco.longitude)
        assertTrue(arc.first().visible)
        // A Pacific crossing from a San Francisco viewpoint disappears round
        // the back before it arrives.
        assertTrue(arc.any { !it.visible })
        assertEquals(65, arc.size)
    }

    @Test
    fun `the centroid sits between the places, and survives the date line`() {
        val pacific = Geo.centroid(listOf(LatLon(0.0, 179.0), LatLon(0.0, -179.0)))!!
        // Averaging the longitudes would give 0° — the wrong side of the planet.
        assertTrue("longitude was ${pacific.longitude}", abs(pacific.longitude) > 179.0)
        assertEquals(0.0, pacific.latitude, 1e-6)

        val atlantic = Geo.centroid(listOf(sanFrancisco, london))!!
        assertTrue(atlantic.longitude in -122.4..-0.4)
        assertTrue(atlantic.latitude > 37.0)
    }

    @Test
    fun `an empty set has no centroid`() {
        assertTrue(Geo.centroid(emptyList()) == null)
    }
}
