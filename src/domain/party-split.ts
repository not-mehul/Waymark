// Group travel is where itineraries quietly disagree: one traveler on the
// early flight, two on the late one, someone with no hotel. This finds those
// disagreements before the airport does.

import {
  chronological,
  displayName,
  segmentTitle,
  type Segment,
  type Traveler,
  type TripDossier
} from './model';

export interface SplitGroup {
  segmentId: string;
  label: string;
  travelerIds: string[];
}

/** A stretch of the trip during which the party is not together, and who is where. */
export interface SplitWindow {
  startMillis: number;
  endMillis: number;
  groups: SplitGroup[];
  /** Travelers with nothing booked in the window. */
  unaccounted: string[];
}

export function splitMinutes(w: SplitWindow): number {
  return Math.trunc((w.endMillis - w.startMillis) / 60_000);
}

/** Per-traveler coverage of a trip: what they are on, and where the holes are. */
export interface TravelerCoverage {
  traveler: Traveler;
  segmentCount: number;
  flightCount: number;
  hasLodging: boolean;
  firstMovementMillis: number | null;
  lastMovementMillis: number | null;
  missingFromSegments: Segment[];
}

const byString = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** Windows where at least two travelers are doing different things. */
export function splitWindows(dossier: TripDossier): SplitWindow[] {
  const travelers = dossier.party.travelers.map((t) => t.id);
  if (travelers.length < 2) return [];
  const segments = chronological(dossier.segments.filter((s) => s.kind !== 'LODGING'));
  if (segments.length === 0) return [];

  const marks = [...new Set(segments.flatMap((s) => [s.startEpochMillis, s.endEpochMillis]))].sort(
    (a, b) => a - b
  );
  const windows: SplitWindow[] = [];
  for (let i = 0; i < marks.length - 1; i++) {
    const from = marks[i]!;
    const to = marks[i + 1]!;
    if (to <= from) continue;
    const midpoint = from + Math.trunc((to - from) / 2);
    const active = segments.filter((s) => midpoint >= s.startEpochMillis && midpoint <= s.endEpochMillis);
    if (active.length === 0) continue;

    const assignment = travelers.map(
      (id) =>
        [id, active.find((s) => s.travelerIds.length === 0 || s.travelerIds.includes(id)) ?? null] as const
    );
    if (new Set(assignment.map(([, s]) => s?.id ?? null)).size < 2) continue;

    const grouped = new Map<Segment, string[]>();
    for (const [id, s] of assignment) {
      if (s === null) continue;
      const list = grouped.get(s);
      if (list) list.push(id);
      else grouped.set(s, [id]);
    }
    const groups = [...grouped].map(([s, ids]) => ({
      segmentId: s.id,
      label: segmentTitle(s),
      travelerIds: ids
    }));
    groups.sort((a, b) => byString(a.label, b.label));
    const unaccounted = assignment.filter(([, s]) => s === null).map(([id]) => id);
    windows.push({ startMillis: from, endMillis: to, groups, unaccounted });
  }
  return merge(windows);
}

function shape(w: SplitWindow): string {
  return JSON.stringify([
    w.groups.map((g) => [g.segmentId, [...g.travelerIds].sort(byString)]),
    [...w.unaccounted].sort(byString)
  ]);
}

/** Adjacent windows with identical grouping read as one split, not five. */
function merge(windows: SplitWindow[]): SplitWindow[] {
  if (windows.length === 0) return [];
  const merged = [windows[0]!];
  for (const w of windows.slice(1)) {
    const last = merged[merged.length - 1]!;
    if (shape(last) === shape(w) && w.startMillis <= last.endMillis) {
      merged[merged.length - 1] = { ...last, endMillis: w.endMillis };
    } else {
      merged.push(w);
    }
  }
  return merged;
}

export function coverage(dossier: TripDossier): TravelerCoverage[] {
  const segments = chronological(dossier.segments);
  return dossier.party.travelers.map((traveler) => {
    const mine = segments.filter((s) => s.travelerIds.length === 0 || s.travelerIds.includes(traveler.id));
    const movements = mine.filter((s) => s.kind === 'FLIGHT' || s.kind === 'GROUND');
    return {
      traveler,
      segmentCount: mine.length,
      flightCount: mine.filter((s) => s.kind === 'FLIGHT').length,
      hasLodging: mine.some((s) => s.kind === 'LODGING'),
      firstMovementMillis: movements.length ? Math.min(...movements.map((s) => s.startEpochMillis)) : null,
      lastMovementMillis: movements.length ? Math.max(...movements.map((s) => s.endEpochMillis)) : null,
      missingFromSegments: segments.filter(
        (s) => s.travelerIds.length > 0 && !s.travelerIds.includes(traveler.id)
      )
    };
  });
}

/** The one thing worth saying out loud about a party: someone on it with nothing booked. */
export function partyWarnings(dossier: TripDossier): string[] {
  if (dossier.party.travelers.length === 0 || dossier.segments.length === 0) return [];
  return coverage(dossier)
    .filter((c) => c.segmentCount === 0)
    .map((c) => `${displayName(c.traveler)} is on the trip with nothing booked.`);
}
