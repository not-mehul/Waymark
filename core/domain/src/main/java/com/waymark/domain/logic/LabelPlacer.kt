package com.waymark.domain.logic

/**
 * Decides which map labels get drawn, and where.
 *
 * A trip map holds far more names than a phone screen has room for. Drawn
 * naively — every label pinned to the right of its mark — a cluster of hotels
 * in one city becomes a stack of overlapping text, and at world zoom the whole
 * chart turns into overprinted grey. Every earlier version of this map did
 * exactly that.
 *
 * The rule here is that an unreadable label is worse than no label. Requests
 * are considered in priority order; each one tries four positions around its
 * mark and takes the first that fits the viewport and collides with nothing
 * already placed. A label that fits nowhere is dropped, and its mark stays on
 * the map unlabelled — the geography survives, the typography gives way.
 *
 * Pure geometry, so the behaviour under a hundred marks is testable without
 * rendering anything.
 */
object LabelPlacer {

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        fun overlaps(other: Box): Boolean =
            left < other.right && other.left < right &&
                top < other.bottom && other.top < bottom

        fun contains(other: Box): Boolean =
            other.left >= left && other.right <= right &&
                other.top >= top && other.bottom <= bottom

        fun inflated(by: Float): Box = Box(left - by, top - by, right + by, bottom + by)
    }

    /**
     * One label wanting a home. [priority] is ascending — 0 is placed first and
     * therefore wins every contest — and [markRadius] keeps text off its own
     * dot as well as everyone else's.
     */
    data class Request(
        val id: String,
        val anchorX: Float,
        val anchorY: Float,
        val width: Float,
        val height: Float,
        val priority: Int,
        val markRadius: Float,
    )

    data class Placement(val id: String, val left: Float, val top: Float)

    /**
     * @param gap clear space required between two labels, in pixels.
     * @return placements for the labels that fit, in the order they were placed.
     */
    fun place(
        requests: List<Request>,
        viewport: Box,
        gap: Float = 3f,
    ): List<Placement> {
        val placed = mutableListOf<Box>()
        val placements = mutableListOf<Placement>()

        // Every mark reserves its own dot up front, so a label never lands on
        // a mark belonging to a request that has not been considered yet.
        val marks = requests.associate { request ->
            request.id to Box(
                left = request.anchorX - request.markRadius,
                top = request.anchorY - request.markRadius,
                right = request.anchorX + request.markRadius,
                bottom = request.anchorY + request.markRadius,
            )
        }

        requests
            .sortedWith(compareBy({ it.priority }, { it.id }))
            .forEach { request ->
                val fitted = candidates(request).firstOrNull { box ->
                    // A label is kept clear of other labels by [gap], and clear
                    // of foreign marks by its own edge. Its own mark is skipped:
                    // the candidate positions are already offset past it, and
                    // testing against it would make a generous gap reject every
                    // position a label has.
                    viewport.contains(box) &&
                        placed.none { it.overlaps(box.inflated(gap)) } &&
                        marks.none { (id, mark) -> id != request.id && mark.overlaps(box) }
                }
                if (fitted != null) {
                    placed += fitted
                    placements += Placement(request.id, fitted.left, fitted.top)
                }
            }

        return placements
    }

    /**
     * Four positions, in the order a cartographer would try them: to the right
     * of the mark, then left, then above, then below. Right first because a
     * left-to-right reader's eye is already there.
     */
    private fun candidates(request: Request): List<Box> {
        val pad = request.markRadius + 4f
        val halfHeight = request.height / 2f
        val halfWidth = request.width / 2f
        return listOf(
            // Right, vertically centred on the mark.
            boxAt(request.anchorX + pad, request.anchorY - halfHeight, request),
            // Left.
            boxAt(request.anchorX - pad - request.width, request.anchorY - halfHeight, request),
            // Above, horizontally centred.
            boxAt(request.anchorX - halfWidth, request.anchorY - pad - request.height, request),
            // Below.
            boxAt(request.anchorX - halfWidth, request.anchorY + pad, request),
        )
    }

    private fun boxAt(left: Float, top: Float, request: Request): Box =
        Box(left, top, left + request.width, top + request.height)
}
