// The whole trip as one markdown document: days as headings, each booking as
// a line that says the thing a traveler needs at that moment. Booking
// references travel with it — they are the reason to send an itinerary.

import { formatFixed } from './decimal';
import { formatDistance } from './geo';
import type { TripAnalyticsReport } from './analytics';
import {
  chronological,
  designator,
  displayName,
  groundModeLabel,
  IDEA_KIND_TEXT,
  IDEA_KINDS,
  nights,
  PRICE_BAND_LABEL,
  segmentEnd,
  segmentStart,
  segmentTitle,
  shortLabel,
  destinationOf,
  tripEndDate,
  tripStartDate,
  type Idea,
  type Segment,
  type TripDossier
} from './model';
import type { LocalDate } from './time';
import { clock, dateRange, day, dayCompact, dayOffsetSuffix, duration, durationBetween } from './time-text';

/** Everything the exporter needs, so it can stay a pure function. */
export interface MarkdownPayload {
  dossier: TripDossier;
  ideas?: readonly Idea[];
  analytics?: TripAnalyticsReport | null;
}

const LETTER_OR_DIGIT = /^[\p{L}\p{Nd}]$/u;
const byString = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** A filename that sorts by date and survives every filesystem. */
export function markdownFileName(dossier: TripDossier): string {
  const lower = dossier.trip.name.toLowerCase();
  let mapped = '';
  for (let i = 0; i < lower.length; i++) {
    const c = lower.charAt(i);
    mapped += LETTER_OR_DIGIT.test(c) ? c : '-';
  }
  const slug =
    mapped
      .split('-')
      .filter((p) => p !== '')
      .join('-') || 'trip';
  return `${tripStartDate(dossier.trip).toString()}-${slug}.md`;
}

export function renderMarkdown(payload: MarkdownPayload): string {
  const out: string[] = [];
  const line = (text = '') => out.push(text);
  const { trip, party } = payload.dossier;

  line(`# ${md(trip.name)}`);
  line();
  line(`*${md(trip.destinationSummary)}*`);
  line();
  line(`**${dateRange(tripStartDate(trip), tripEndDate(trip))}**`);
  if (party.travelers.length > 0) {
    line();
    line(party.travelers.map((t) => md(displayName(t))).join(' · '));
  }

  itinerary(payload, line);
  ideas(payload, line);
  references(payload, line);
  numbers(payload, line);

  line();
  line('---');
  line();
  line('Exported from Waymark.');
  return out.map((l) => `${l}\n`).join('');
}

type Line = (text?: string) => void;

function itinerary(payload: MarkdownPayload, line: Line): void {
  const segments = chronological(payload.dossier.segments);
  if (segments.length === 0) return;
  line();
  line('## Itinerary');
  let current: LocalDate | null = null;
  for (const s of segments) {
    const date = segmentStart(s).toPlainDate();
    if (current === null || !date.equals(current)) {
      current = date;
      line();
      line(`### ${day(date)}`);
      line();
    }
    line(bookingLine(s, payload));
  }
}

/** One booking, as one line: when, what, and who — in that order. */
function bookingLine(s: Segment, payload: MarkdownPayload): string {
  const time = clock(segmentStart(s));
  const who = travelerNames(s.travelerIds, payload);
  const parts: string[] = [];
  switch (s.kind) {
    case 'FLIGHT': {
      parts.push(`**${md(designator(s))}** ${route(s)}`);
      const offset = dayOffsetSuffix(segmentStart(s), segmentEnd(s));
      parts.push(`lands ${clock(segmentEnd(s))}` + (offset ? ` (${offset})` : ''));
      if (s.departureTerminal != null) parts.push(`terminal ${md(s.departureTerminal)}`);
      if (s.aircraft != null) parts.push(md(s.aircraft));
      if (s.cabin != null) parts.push(md(s.cabin));
      break;
    }
    case 'LODGING': {
      const n = nights(s);
      parts.push(`**${md(s.propertyName)}**`, `${n} night${n === 1 ? '' : 's'}`);
      if (s.roomDescription != null) parts.push(md(s.roomDescription));
      if (s.origin.city.trim() !== '') parts.push(md(s.origin.city));
      break;
    }
    case 'GROUND':
      parts.push(
        `**${groundModeLabel(s.mode)}** ${route(s)}`,
        durationBetween(s.startEpochMillis, s.endEpochMillis)
      );
      if (s.provider != null) parts.push(md(s.provider));
      break;
    case 'EXPERIENCE':
      parts.push(`**${md(s.name)}**`, md(s.category));
      if (s.origin.city.trim() !== '') parts.push(md(s.origin.city));
      break;
  }
  const suffix = who == null || who.trim() === '' ? '' : ` — ${who}`;
  const note = s.note != null ? `  \n  ${md(s.note)}` : '';
  return `- \`${time}\` ${parts.join(', ')}${suffix}${note}`;
}

function ideas(payload: MarkdownPayload, line: Line): void {
  const live = (payload.ideas ?? []).filter((i) => i.status !== 'DISMISSED');
  if (live.length === 0) return;
  line();
  line('## On the list');
  const byCity = new Map<string, Idea[]>();
  for (const i of live) {
    const city = i.city.trim() === '' ? 'Anywhere' : i.city;
    byCity.set(city, [...(byCity.get(city) ?? []), i]);
  }
  for (const city of [...byCity.keys()].sort(byString)) {
    const cityIdeas = byCity.get(city)!;
    line();
    line(`### ${md(city)}`);
    line();
    for (const kind of IDEA_KINDS) {
      const ofKind = cityIdeas.filter((i) => i.kind === kind);
      if (ofKind.length === 0) continue;
      line(`**${IDEA_KIND_TEXT[kind].heading}**`);
      line();
      for (const i of [...ofKind].sort((a, b) => byString(a.title, b.title))) line(ideaLine(i));
      line();
    }
  }
}

function ideaLine(i: Idea): string {
  // Ticked when it is settled: done, or on the timeline above and so will be.
  const settled = i.status === 'DONE' || i.scheduledSegmentId != null;
  let text = (settled ? '- [x] ' : '- [ ] ') + md(i.title);
  const notes: string[] = [];
  if (i.plannedDate) notes.push(dayCompact(i.plannedDate));
  if (i.typicalMinutes != null) notes.push(duration(i.typicalMinutes));
  if (i.priceBand) notes.push(PRICE_BAND_LABEL[i.priceBand]);
  if (i.bestTime != null) notes.push(md(i.bestTime));
  if (notes.length > 0) text += ` — ${notes.join(', ')}`;
  if (i.note != null) text += `  \n  ${md(i.note)}`;
  return text;
}

/** Every booking that carries a reference, with the reference. */
function references(payload: MarkdownPayload, line: Line): void {
  const booked = chronological(
    payload.dossier.segments.filter((s) => s.confirmationCode != null && s.confirmationCode.trim() !== '')
  );
  if (booked.length === 0) return;
  line();
  line('## Bookings');
  line();
  for (const s of booked) {
    let text = `- **${md(segmentTitle(s))}**`;
    if (s.bookedWith != null && s.bookedWith.trim() !== '') text += ` · ${md(s.bookedWith)}`;
    text += ` — ${codeSpan(s.confirmationCode ?? '')}`;
    line(text);
  }
}

function numbers(payload: MarkdownPayload, line: Line): void {
  const report = payload.analytics;
  if (!report) return;
  line();
  line('## The numbers');
  line();
  line('| | |');
  line('|---|---|');
  line(`| Distance | ${formatDistance(report.totalDistanceKm)} |`);
  line(`| Flown | ${formatDistance(report.flownKm)} |`);
  line(`| Hours moving | ${formatFixed(report.hoursMoving, 1)} |`);
  line(`| Clock changes | ${report.clockChanges} (${formatFixed(report.hoursShifted, 0)}h) |`);
  line(`| Countries | ${md(report.countriesVisited.join(', '))} |`);
  line(`| Estimated carbon | ${Math.trunc(report.carbonKg)} kg CO₂e |`);
  if (report.longestLegLabel != null) {
    line(`| Longest leg | ${md(report.longestLegLabel)}, ${formatDistance(report.longestLegKm)} |`);
  }
}

/** Null when the whole party is on the segment: the party is listed once at the top. */
function travelerNames(ids: readonly string[], payload: MarkdownPayload): string | null {
  const party = payload.dossier.party.travelers;
  if (ids.length === 0 || ids.length === party.length) return null;
  const names = party
    .filter((t) => ids.includes(t.id))
    .map((t) => md(displayName(t)))
    .join(', ');
  return names.trim() === '' ? null : names;
}

/** `SFO → LHR`, escaped. */
function route(s: Segment): string {
  return `${md(shortLabel(s.origin))} → ${md(shortLabel(destinationOf(s)))}`;
}

const INLINE_SPECIALS = '\\`*_[]<>|~';
const LINE_START_SPECIALS = '#-+=';
const ORDERED_ITEM = /^\d+\./;

/**
 * User text, made inert: only what changes meaning is escaped, plus a
 * leading list or heading marker.
 */
export function md(text: string): string {
  let escaped = '';
  for (const c of text) escaped += (INLINE_SPECIALS.includes(c) ? '\\' : '') + c;
  if (escaped === '') return escaped;
  if (LINE_START_SPECIALS.includes(escaped[0]!)) return `\\${escaped}`;
  if (ORDERED_ITEM.test(escaped)) return escaped.replace('.', '\\.');
  return escaped;
}

/** A reference in a code span, wide enough to hold any backticks inside it. */
export function codeSpan(text: string): string {
  const longest = Math.max(0, ...(text.match(/`+/g) ?? []).map((run) => run.length));
  const fence = '`'.repeat(longest + 1);
  const pad = text.startsWith('`') || text.endsWith('`') ? ' ' : '';
  return `${fence}${pad}${text}${pad}${fence}`;
}
