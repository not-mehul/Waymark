// The list of things a trip might do, and the one operation that matters:
// promoting an idea onto the timeline, where it stops being an idea.

import {
  experience,
  hasCoordinates,
  IDEA_KIND_TEXT,
  IDEA_KINDS,
  place,
  type Experience,
  type Idea,
  type IdeaKind,
  type IdeaStatus,
  type Place
} from './model';
import { compareDates, zonedOf, zoneOrUtc, type LocalDate, type LocalTime } from './time';
import { estimateBest, totalMinutes } from './transit';

/** The board grouped by place. */
export interface CityGroup {
  city: string;
  ideas: Idea[];
}

/** The board grouped by the day it is pencilled in for. */
export interface DayGroup {
  date: LocalDate;
  ideas: Idea[];
}

export interface DayPlan {
  days: DayGroup[];
  undated: Idea[];
}

/** One section of the board: a kind of idea, and the ideas in it. */
export interface IdeaSection {
  kind: IdeaKind;
  ideas: Idea[];
}

export interface IdeaTally {
  saved: number;
  scheduled: number;
  done: number;
  dismissed: number;
}

export const outstanding = (group: { ideas: Idea[] }) =>
  group.ideas.filter((i) => i.status === 'SAVED').length;
export const estimatedMinutes = (day: DayGroup) =>
  day.ideas.reduce((n, i) => n + (i.typicalMinutes ?? 60), 0);
export const isEmptyPlan = (plan: DayPlan) => plan.days.length === 0 && plan.undated.length === 0;
export const tallyTotal = (t: IdeaTally) => t.saved + t.scheduled + t.done;

const DEFAULT_MINUTES = 60;
const STATUS_ORDER: Record<IdeaStatus, number> = { SAVED: 0, SCHEDULED: 1, DONE: 2, DISMISSED: 3 };
const byString = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** Open items first, then scheduled, then done; alphabetical within each. */
function displayOrder(a: Idea, b: Idea): number {
  return (
    STATUS_ORDER[a.status] - STATUS_ORDER[b.status] || byString(a.title.toLowerCase(), b.title.toLowerCase())
  );
}

/** Sections in a fixed order, so the board does not reshuffle as it fills. */
export function ideaSections(
  ideas: readonly Idea[],
  options: { city?: string | null; travelerFilter?: string | null; includeDismissed?: boolean } = {}
): IdeaSection[] {
  const { city = null, travelerFilter = null, includeDismissed = false } = options;
  const visible = shown(ideas, travelerFilter, includeDismissed).filter(
    (i) => city === null || i.city.toLowerCase() === city.toLowerCase()
  );
  return IDEA_KINDS.flatMap((kind) => {
    const group = visible.filter((i) => i.kind === kind).sort(displayOrder);
    return group.length === 0 ? [] : [{ kind, ideas: group }];
  });
}

export function ideaTally(ideas: readonly Idea[]): IdeaTally {
  const count = (status: IdeaStatus) => ideas.filter((i) => i.status === status).length;
  return {
    saved: count('SAVED'),
    scheduled: count('SCHEDULED'),
    done: count('DONE'),
    dismissed: count('DISMISSED')
  };
}

/**
 * Promote an idea onto the timeline. Duration comes from the idea's own
 * estimate where it has one, because a museum is not a coffee; the fallback
 * is an hour. The idea's note comes along so the reason it was saved survives.
 */
export function scheduleIdea(
  idea: Idea,
  segmentId: string,
  date: LocalDate,
  startTime: LocalTime,
  zoneId: string,
  travelerIds: readonly string[],
  minutesOverride: number | null = null
): Experience {
  const zone = zoneOrUtc(zoneId);
  const minutes = Math.max(15, minutesOverride ?? idea.typicalMinutes ?? DEFAULT_MINUTES);
  const start = zonedOf(date, startTime, zone);
  const end = start.add({ minutes });
  return experience({
    id: segmentId,
    tripId: idea.tripId,
    name: idea.title,
    category: IDEA_KIND_TEXT[idea.kind].label,
    origin: idea.place ?? place({ name: idea.title, city: idea.city, timeZoneId: zone }),
    startEpochMillis: start.epochMilliseconds,
    endEpochMillis: end.epochMilliseconds,
    startZoneId: zone,
    endZoneId: zone,
    travelerIds: travelerIds.length > 0 ? [...travelerIds] : [...idea.interestedTravelerIds],
    note: idea.note,
    curatedBy: idea.bestTime
  });
}

/** The list grouped by place, in visiting order where the trip supplies one. */
export function ideasByCity(
  ideas: readonly Idea[],
  options: { cityOrder?: readonly string[]; travelerFilter?: string | null; includeDismissed?: boolean } = {}
): CityGroup[] {
  const { cityOrder = [], travelerFilter = null, includeDismissed = false } = options;
  const order = cityOrder.map((c) => c.toLowerCase());
  const groups = new Map<string, Idea[]>();
  for (const i of shown(ideas, travelerFilter, includeDismissed)) {
    const city = i.city.trim() === '' ? 'Anywhere' : i.city;
    const list = groups.get(city);
    if (list) list.push(i);
    else groups.set(city, [i]);
  }
  const rank = (city: string) => {
    const at = order.indexOf(city.toLowerCase());
    return at < 0 ? Number.MAX_SAFE_INTEGER : at;
  };
  return [...groups]
    .map(([city, group]) => ({ city, ideas: group.sort(displayOrder) }))
    .sort((a, b) => rank(a.city) - rank(b.city) || byString(a.city.toLowerCase(), b.city.toLowerCase()));
}

/**
 * The list grouped by the day it is pencilled in for, with everything that
 * has no day yet gathered at the end. Days outside the trip are kept.
 */
export function ideasByDay(
  ideas: readonly Idea[],
  options: { travelerFilter?: string | null; includeDismissed?: boolean } = {}
): DayPlan {
  const { travelerFilter = null, includeDismissed = false } = options;
  const visible = shown(ideas, travelerFilter, includeDismissed);
  const groups = new Map<string, DayGroup>();
  for (const i of visible) {
    if (i.plannedDate === null) continue;
    const key = i.plannedDate.toString();
    const group = groups.get(key);
    if (group) group.ideas.push(i);
    else groups.set(key, { date: i.plannedDate, ideas: [i] });
  }
  const days = [...groups.values()]
    .map((g) => ({ date: g.date, ideas: g.ideas.sort(displayOrder) }))
    .sort((a, b) => compareDates(a.date, b.date));
  return { days, undated: visible.filter((i) => i.plannedDate === null).sort(displayOrder) };
}

/** What one traveler cares about — unclaimed ideas belong to everyone — minus what was set aside. */
function shown(ideas: readonly Idea[], travelerFilter: string | null, includeDismissed: boolean): Idea[] {
  return ideas
    .filter((i) => includeDismissed || i.status !== 'DISMISSED')
    .filter(
      (i) =>
        travelerFilter === null ||
        i.interestedTravelerIds.length === 0 ||
        i.interestedTravelerIds.includes(travelerFilter)
    );
}

/** Ideas that fit a gap in the day, by their own duration plus the journey there and back. */
export function ideasThatFit(
  ideas: readonly Idea[],
  from: Place,
  availableMinutes: number,
  limit = 4
): Idea[] {
  return ideas
    .filter((i) => i.status === 'SAVED')
    .filter((i) => {
      const visit = i.typicalMinutes ?? DEFAULT_MINUTES;
      const travel =
        i.place && hasCoordinates(from) && hasCoordinates(i.place)
          ? totalMinutes(estimateBest(from, i.place)) * 2
          : 0;
      return visit + travel <= availableMinutes;
    })
    .sort((a, b) => (b.typicalMinutes ?? DEFAULT_MINUTES) - (a.typicalMinutes ?? DEFAULT_MINUTES))
    .slice(0, limit);
}
