package com.waymark.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPlacerTest {

    private val viewport = LabelPlacer.Box(0f, 0f, 1000f, 1000f)

    private fun request(
        id: String,
        x: Float,
        y: Float,
        priority: Int = 0,
        width: Float = 60f,
        height: Float = 14f,
    ) = LabelPlacer.Request(
        id = id,
        anchorX = x,
        anchorY = y,
        width = width,
        height = height,
        priority = priority,
        markRadius = 6f,
    )

    private fun boxOf(
        placement: LabelPlacer.Placement,
        width: Float = 60f,
        height: Float = 14f,
    ) = LabelPlacer.Box(
        placement.left,
        placement.top,
        placement.left + width,
        placement.top + height,
    )

    @Test
    fun `a label with room lands to the right of its mark`() {
        val placed = LabelPlacer.place(listOf(request("a", 500f, 500f)), viewport)
        assertEquals(1, placed.size)
        assertTrue("expected it right of the mark", placed.first().left > 500f)
        // Vertically centred on the mark.
        assertEquals(500f - 7f, placed.first().top, 0.01f)
    }

    @Test
    fun `two marks on the same spot never print on top of each other`() {
        val placed = LabelPlacer.place(
            listOf(request("a", 500f, 500f), request("b", 502f, 501f)),
            viewport,
        )
        // Whatever survives, no two boxes may overlap.
        placed.forEachIndexed { i, first ->
            placed.drop(i + 1).forEach { second ->
                assertTrue(
                    "${first.id} overlaps ${second.id}",
                    !boxOf(first).overlaps(boxOf(second)),
                )
            }
        }
    }

    @Test
    fun `a lower priority label gives way to a higher one`() {
        // Same anchor, and only the right-hand position is free: the viewport
        // is cut so a label to the left, above or below falls outside it.
        val tight = LabelPlacer.Box(494f, 480f, 580f, 520f)
        val placed = LabelPlacer.place(
            listOf(
                request("stop", 500f, 500f, priority = 2),
                request("station", 500f, 500f, priority = 0),
            ),
            tight,
        )
        assertEquals(1, placed.size)
        assertEquals("station", placed.first().id)
    }

    @Test
    fun `a label that fits nowhere is dropped rather than drawn badly`() {
        val cramped = LabelPlacer.Box(0f, 0f, 40f, 40f)
        val placed = LabelPlacer.place(listOf(request("a", 20f, 20f)), cramped)
        assertTrue(placed.isEmpty())
    }

    @Test
    fun `a label never covers another mark`() {
        // "b" sits exactly where "a" would like to put its text.
        val requests = listOf(
            request("a", 100f, 100f, priority = 0),
            request("b", 140f, 100f, priority = 1),
        )
        val placed = LabelPlacer.place(requests, viewport)
        val aBox = placed.first { it.id == "a" }.let(::boxOf)

        val bMark = LabelPlacer.Box(140f - 6f, 100f - 6f, 140f + 6f, 100f + 6f)
        assertTrue("a's label covers b's mark", !aBox.overlaps(bMark))
    }

    @Test
    fun `every placement stays inside the viewport`() {
        // A grid of marks pressed against all four edges.
        val requests = buildList {
            listOf(5f, 500f, 995f).forEach { x ->
                listOf(5f, 500f, 995f).forEach { y ->
                    add(request("$x-$y", x, y))
                }
            }
        }
        LabelPlacer.place(requests, viewport).forEach { placement ->
            assertTrue(
                "${placement.id} escaped the viewport",
                viewport.contains(boxOf(placement)),
            )
        }
    }

    @Test
    fun `a dense cluster keeps some labels rather than all or none`() {
        // Forty saved restaurants in one neighbourhood: the case that used to
        // turn the map into overprinted grey.
        val cluster = (0 until 40).map { index ->
            request("p$index", 500f + (index % 7) * 9f, 500f + (index / 7) * 7f, priority = 2)
        }
        val placed = LabelPlacer.place(cluster, viewport)
        assertTrue("placed ${placed.size}", placed.isNotEmpty())
        assertTrue("placed ${placed.size} of 40", placed.size < cluster.size)
    }

    @Test
    fun `placement is deterministic for the same input`() {
        val requests = listOf(
            request("b", 300f, 300f, priority = 1),
            request("a", 305f, 302f, priority = 1),
            request("c", 310f, 298f, priority = 1),
        )
        val first = LabelPlacer.place(requests, viewport)
        val second = LabelPlacer.place(requests.reversed(), viewport)
        assertEquals(first, second)
    }

    @Test
    fun `boxes report overlap and containment the way the placer relies on`() {
        val a = LabelPlacer.Box(0f, 0f, 10f, 10f)
        assertTrue(a.overlaps(LabelPlacer.Box(9f, 9f, 20f, 20f)))
        // Edge-touching is not overlapping; two labels may sit flush.
        assertTrue(!a.overlaps(LabelPlacer.Box(10f, 0f, 20f, 10f)))
        assertTrue(a.contains(LabelPlacer.Box(1f, 1f, 9f, 9f)))
        assertTrue(!a.contains(LabelPlacer.Box(1f, 1f, 11f, 9f)))
        assertEquals(12f, a.inflated(1f).width, 0.01f)
    }

    @Test
    fun `an empty request list places nothing and does not throw`() {
        assertTrue(LabelPlacer.place(emptyList(), viewport).isEmpty())
    }

    @Test
    fun `the gap is honoured between neighbouring labels`() {
        val requests = listOf(
            request("a", 100f, 100f, priority = 0),
            request("b", 100f, 118f, priority = 1),
        )
        val placed = LabelPlacer.place(requests, viewport, gap = 6f)
        val a = placed.firstOrNull { it.id == "a" }
        val b = placed.firstOrNull { it.id == "b" }
        assertNotNull(a)
        if (b != null) {
            assertTrue(!boxOf(a!!).inflated(6f).overlaps(boxOf(b)))
        }
    }

    @Test
    fun `a mark whose label is dropped is still reported nowhere`() {
        val cramped = LabelPlacer.Box(0f, 0f, 30f, 30f)
        val placed = LabelPlacer.place(
            listOf(request("a", 15f, 15f), request("b", 16f, 16f)),
            cramped,
        )
        assertNull(placed.firstOrNull { it.id == "b" })
    }
}
