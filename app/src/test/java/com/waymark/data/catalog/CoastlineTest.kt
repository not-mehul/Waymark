package com.waymark.data.catalog

import com.waymark.domain.logic.Geo
import com.waymark.domain.logic.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoastlineTest {

    private val rings = Coastline.rings

    @Test
    fun `the world is present and parses`() {
        assertTrue("only ${rings.size} rings", rings.size >= 40)
        assertTrue(rings.sumOf { it.size } in 1_000..3_000)
    }

    @Test
    fun `every coordinate is a real coordinate`() {
        rings.forEach { ring ->
            ring.forEach { point ->
                assertTrue("lat ${point.latitude}", point.latitude in -90.0..90.0)
                assertTrue("lon ${point.longitude}", point.longitude in -180.0..180.0)
            }
        }
    }

    @Test
    fun `every ring is closed, so it can be filled`() {
        rings.forEach { ring ->
            assertTrue("ring of ${ring.size}", ring.size >= 4)
            assertEquals(ring.first(), ring.last())
        }
    }

    /**
     * The bug this data was regenerated to avoid: a ring that hops the seam
     * from one interior longitude to another fills as a stripe across the
     * whole chart.
     *
     * A step between the two edges themselves is a different thing and is
     * allowed — that is how Antarctica closes along the bottom of the world —
     * because both endpoints sit on the boundary and the segment traces it
     * rather than cutting across the map.
     */
    @Test
    fun `no ring hops the antimeridian from interior longitudes`() {
        rings.forEach { ring ->
            ring.zipWithNext().forEach { (a, b) ->
                if (abs(a.longitude) == 180.0 && abs(b.longitude) == 180.0) return@forEach
                assertFalse(
                    "seam crossing at ${a.longitude} → ${b.longitude}",
                    abs(a.longitude - b.longitude) > 180.0,
                )
            }
        }
    }

    @Test
    fun `the coastline is coarse enough to draw every frame`() {
        // One point per fifty kilometres or so; a denser table would be a
        // per-frame cost with no visible return at this size on screen.
        val steps = rings.flatMap { ring ->
            ring.zipWithNext().map { (a, b) -> Geo.distanceKm(a, b) }
        }
        assertTrue(steps.isNotEmpty())
        assertTrue("median step ${steps.sorted()[steps.size / 2]}", steps.average() > 20.0)
    }

    @Test
    fun `known land is inside a ring and known ocean is not`() {
        // Two points that any recognisable world map has to get right.
        assertTrue("Paris is not on land", covers(LatLon(48.85, 2.35)))
        assertTrue("Kansas is not on land", covers(LatLon(38.5, -98.0)))
        assertFalse("mid-Pacific is land", covers(LatLon(0.0, -140.0)))
        assertFalse("mid-Atlantic is land", covers(LatLon(30.0, -40.0)))
    }

    @Test
    fun `the largest ring is the Africa-Eurasia landmass`() {
        val biggest = rings.maxBy { it.size }
        val lons = biggest.map { it.longitude }
        // It reaches from the Atlantic coast well into Asia.
        assertTrue(lons.min() < -10.0)
        assertTrue(lons.max() > 100.0)
    }

    /** Even-odd ray cast, the standard point-in-polygon test. */
    private fun covers(point: LatLon): Boolean = rings.any { ring ->
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val straddles = (a.latitude > point.latitude) != (b.latitude > point.latitude)
            if (!straddles) continue
            val crossing = (b.longitude - a.longitude) *
                (point.latitude - a.latitude) /
                (b.latitude - a.latitude) + a.longitude
            if (point.longitude < crossing) inside = !inside
        }
        inside
    }
}
