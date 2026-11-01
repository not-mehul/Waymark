// Decides which map labels get drawn, and where. An unreadable label is worse
// than no label: requests are placed in priority order, each tries four
// positions around its mark, and one that fits nowhere is dropped.

export interface Box {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export const boxWidth = (b: Box) => b.right - b.left;
export const boxHeight = (b: Box) => b.bottom - b.top;

export function overlaps(a: Box, b: Box): boolean {
  return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
}

export function contains(outer: Box, inner: Box): boolean {
  return (
    inner.left >= outer.left &&
    inner.right <= outer.right &&
    inner.top >= outer.top &&
    inner.bottom <= outer.bottom
  );
}

export function inflated(b: Box, by: number): Box {
  return { left: b.left - by, top: b.top - by, right: b.right + by, bottom: b.bottom + by };
}

/**
 * One label wanting a home. [priority] is ascending — 0 is placed first and
 * wins every contest — and [markRadius] keeps text off its own dot as well
 * as everyone else's.
 */
export interface LabelRequest {
  id: string;
  anchorX: number;
  anchorY: number;
  width: number;
  height: number;
  priority: number;
  markRadius: number;
}

export interface LabelPlacement {
  id: string;
  left: number;
  top: number;
}

/**
 * Placements for the labels that fit, in the order they were placed.
 * [gap] is the clear space required between two labels, in pixels.
 */
export function placeLabels(requests: readonly LabelRequest[], viewport: Box, gap = 3): LabelPlacement[] {
  const placed: Box[] = [];
  const placements: LabelPlacement[] = [];
  // Every mark reserves its own dot up front, so a label never lands on a
  // mark belonging to a request that has not been considered yet.
  const marks = new Map(
    requests.map((r) => [
      r.id,
      {
        left: r.anchorX - r.markRadius,
        top: r.anchorY - r.markRadius,
        right: r.anchorX + r.markRadius,
        bottom: r.anchorY + r.markRadius
      }
    ])
  );

  const ordered = [...requests].sort(
    (a, b) => a.priority - b.priority || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)
  );
  for (const request of ordered) {
    const fitted = candidates(request).find(
      (box) =>
        contains(viewport, box) &&
        !placed.some((p) => overlaps(p, inflated(box, gap))) &&
        ![...marks].some(([id, mark]) => id !== request.id && overlaps(mark, box))
    );
    if (fitted) {
      placed.push(fitted);
      placements.push({ id: request.id, left: fitted.left, top: fitted.top });
    }
  }
  return placements;
}

/** Right of the mark, then left, then above, then below. */
function candidates(r: LabelRequest): Box[] {
  const pad = r.markRadius + 4;
  const halfHeight = r.height / 2;
  const halfWidth = r.width / 2;
  const at = (left: number, top: number): Box => ({
    left,
    top,
    right: left + r.width,
    bottom: top + r.height
  });
  return [
    at(r.anchorX + pad, r.anchorY - halfHeight),
    at(r.anchorX - pad - r.width, r.anchorY - halfHeight),
    at(r.anchorX - halfWidth, r.anchorY - pad - r.height),
    at(r.anchorX - halfWidth, r.anchorY + pad)
  ];
}
