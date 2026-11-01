import { describe, expect, it } from 'vitest';
import {
  boxWidth,
  contains,
  inflated,
  overlaps,
  placeLabels,
  type Box,
  type LabelPlacement,
  type LabelRequest
} from '../../src/domain/labels';

const box = (left: number, top: number, right: number, bottom: number): Box => ({ left, top, right, bottom });
const viewport = box(0, 0, 1000, 1000);

const request = (id: string, x: number, y: number, priority = 0, width = 60, height = 14): LabelRequest => ({
  id,
  anchorX: x,
  anchorY: y,
  width,
  height,
  priority,
  markRadius: 6
});

const boxOf = (p: LabelPlacement, width = 60, height = 14) =>
  box(p.left, p.top, p.left + width, p.top + height);

describe('LabelPlacer', () => {
  it('a label with room lands to the right of its mark', () => {
    const placed = placeLabels([request('a', 500, 500)], viewport);
    expect(placed).toHaveLength(1);
    expect(placed[0]!.left).toBeGreaterThan(500);
    // Vertically centred on the mark.
    expect(placed[0]!.top).toBeCloseTo(493, 2);
  });

  it('two marks on the same spot never print on top of each other', () => {
    const placed = placeLabels([request('a', 500, 500), request('b', 502, 501)], viewport);
    // Whatever survives, no two boxes may overlap.
    placed.forEach((first, i) => {
      for (const second of placed.slice(i + 1)) {
        expect(overlaps(boxOf(first), boxOf(second)), `${first.id} overlaps ${second.id}`).toBe(false);
      }
    });
  });

  it('a lower priority label gives way to a higher one', () => {
    // Same anchor, and only the right-hand position is free: the viewport is
    // cut so a label to the left, above or below falls outside it.
    const tight = box(494, 480, 580, 520);
    const placed = placeLabels([request('stop', 500, 500, 2), request('station', 500, 500, 0)], tight);
    expect(placed).toHaveLength(1);
    expect(placed[0]!.id).toBe('station');
  });

  it('a label that fits nowhere is dropped rather than drawn badly', () => {
    expect(placeLabels([request('a', 20, 20)], box(0, 0, 40, 40))).toEqual([]);
  });

  it('a label never covers another mark', () => {
    // "b" sits exactly where "a" would like to put its text.
    const placed = placeLabels([request('a', 100, 100, 0), request('b', 140, 100, 1)], viewport);
    const aBox = boxOf(placed.find((p) => p.id === 'a')!);
    const bMark = box(140 - 6, 100 - 6, 140 + 6, 100 + 6);
    expect(overlaps(aBox, bMark), "a's label covers b's mark").toBe(false);
  });

  it('every placement stays inside the viewport', () => {
    // A grid of marks pressed against all four edges.
    const requests = [5, 500, 995].flatMap((x) => [5, 500, 995].map((y) => request(`${x}-${y}`, x, y)));
    for (const placement of placeLabels(requests, viewport)) {
      expect(contains(viewport, boxOf(placement)), `${placement.id} escaped the viewport`).toBe(true);
    }
  });

  it('a dense cluster keeps some labels rather than all or none', () => {
    // Forty saved restaurants in one neighbourhood: the case that used to
    // turn the map into overprinted grey.
    const cluster = Array.from({ length: 40 }, (_, index) =>
      request(`p${index}`, 500 + (index % 7) * 9, 500 + Math.floor(index / 7) * 7, 2)
    );
    const placed = placeLabels(cluster, viewport);
    expect(placed.length).toBeGreaterThan(0);
    expect(placed.length).toBeLessThan(cluster.length);
  });

  it('placement is deterministic for the same input', () => {
    const requests = [request('b', 300, 300, 1), request('a', 305, 302, 1), request('c', 310, 298, 1)];
    expect(placeLabels([...requests].reverse(), viewport)).toEqual(placeLabels(requests, viewport));
  });

  it('boxes report overlap and containment the way the placer relies on', () => {
    const a = box(0, 0, 10, 10);
    expect(overlaps(a, box(9, 9, 20, 20))).toBe(true);
    // Edge-touching is not overlapping; two labels may sit flush.
    expect(overlaps(a, box(10, 0, 20, 10))).toBe(false);
    expect(contains(a, box(1, 1, 9, 9))).toBe(true);
    expect(contains(a, box(1, 1, 11, 9))).toBe(false);
    expect(boxWidth(inflated(a, 1))).toBeCloseTo(12, 2);
  });

  it('an empty request list places nothing and does not throw', () => {
    expect(placeLabels([], viewport)).toEqual([]);
  });

  it('the gap is honoured between neighbouring labels', () => {
    const placed = placeLabels([request('a', 100, 100, 0), request('b', 100, 118, 1)], viewport, 6);
    const a = placed.find((p) => p.id === 'a');
    const b = placed.find((p) => p.id === 'b');
    expect(a).toBeDefined();
    if (b) expect(overlaps(inflated(boxOf(a!), 6), boxOf(b))).toBe(false);
  });

  it('a mark whose label is dropped is still reported nowhere', () => {
    const placed = placeLabels([request('a', 15, 15), request('b', 16, 16)], box(0, 0, 30, 30));
    expect(placed.find((p) => p.id === 'b')).toBeUndefined();
  });
});
