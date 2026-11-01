// The trip as a document to print or keep as a PDF: a sequence of blocks a
// renderer lays out on pages. What goes in it is decided here, as a pure
// function; how it looks is the renderer's.

import type { CostSummary } from './costs';
import { markdownFileName } from './markdown';
import {
  chronological,
  displayName,
  EXPENSE_CATEGORY_LABEL,
  formatMoney,
  nights,
  segmentEnd,
  segmentStart,
  segmentTitle,
  shortLabel,
  tripEndDate,
  tripStartDate,
  type Segment,
  type TripDossier,
  type WeatherDay
} from './model';
import type { LocalDate } from './time';
import { clock, dateRange, day, dayCompact, dayOffsetSuffix, durationBetween, zoneLabel } from './time-text';
import { describeWeather, temperature } from './weather';

export type DocumentBlock =
  | { type: 'title'; name: string; summary: string; dates: string; party: string | null }
  | { type: 'section'; text: string }
  /** A day, with its weather when known: "Thursday 14 May" · "21° / 14° · Partly cloudy". */
  | { type: 'day'; text: string; weather: string | null }
  | {
      type: 'entry';
      /** "16:20 PDT". */
      time: string;
      title: string;
      details: string[];
      /** Who is on it, when it is not everyone. */
      people: string | null;
      reference: string | null;
      note: string | null;
    }
  /** A label and a value on one line: a reference, a balance. */
  | { type: 'row'; label: string; value: string }
  | { type: 'note'; text: string };

export interface DocumentPayload {
  dossier: TripDossier;
  costs?: CostSummary | null;
  /** The weather for each day (keyed by ISO date), where it is known. */
  weather?: ReadonlyMap<string, WeatherDay>;
}

export function documentFileName(dossier: TripDossier): string {
  return markdownFileName(dossier).replace(/\.md$/, '') + '.pdf';
}

const notBlank = (s: string | null | undefined): s is string => s != null && s.trim() !== '';

export function buildItineraryDocument(payload: DocumentPayload): DocumentBlock[] {
  const blocks: DocumentBlock[] = [];
  const { trip, party } = payload.dossier;
  blocks.push({
    type: 'title',
    name: trip.name,
    summary: trip.destinationSummary,
    dates: dateRange(tripStartDate(trip), tripEndDate(trip)),
    party: party.travelers.length > 0 ? party.travelers.map(displayName).join(' · ') : null
  });

  const segments = chronological(payload.dossier.segments);
  if (segments.length === 0) {
    blocks.push({ type: 'note', text: 'Nothing is booked yet.' });
  } else {
    blocks.push({ type: 'section', text: 'Itinerary' });
    let current: LocalDate | null = null;
    for (const s of segments) {
      const date = segmentStart(s).toPlainDate();
      if (current === null || !date.equals(current)) {
        current = date;
        const weather = payload.weather?.get(date.toString());
        blocks.push({ type: 'day', text: day(date), weather: weather ? weatherLine(weather) : null });
      }
      blocks.push(entry(s, payload));
    }
  }

  const costs = payload.costs;
  if (costs && (costs.total > 0 || costs.unconverted.length > 0)) costBlocks(costs, payload, blocks);
  return blocks;
}

function entry(s: Segment, payload: DocumentPayload): DocumentBlock {
  const start = segmentStart(s);
  const end = segmentEnd(s);
  let details: (string | null)[];
  switch (s.kind) {
    case 'FLIGHT': {
      const offset = dayOffsetSuffix(start, end);
      details = [
        `${shortLabel(s.origin)} → ${shortLabel(s.destination)}`,
        `lands ${clock(end)} ${zoneLabel(end)}` + (offset ? ` (${offset})` : ''),
        s.departureTerminal != null ? `terminal ${s.departureTerminal}` : null,
        s.departureGate != null ? `gate ${s.departureGate}` : null,
        s.cabin,
        Object.keys(s.seats).length > 0 ? seats(s.seats, payload) : null
      ];
      break;
    }
    case 'LODGING': {
      const n = nights(s);
      details = [
        `${n} night${n === 1 ? '' : 's'}, until ${dayCompact(end.toPlainDate())}`,
        s.roomDescription,
        s.origin.address ?? (notBlank(s.origin.city) ? s.origin.city : null),
        s.checkInNote
      ];
      break;
    }
    case 'GROUND':
      details = [
        `${shortLabel(s.origin)} → ${shortLabel(s.destination)}`,
        durationBetween(s.startEpochMillis, s.endEpochMillis),
        s.provider
      ];
      break;
    case 'EXPERIENCE':
      details = [s.category, s.origin.address ?? (notBlank(s.origin.city) ? s.origin.city : null)];
      break;
  }
  const party = payload.dossier.party.travelers;
  const names =
    s.travelerIds.length > 0 && s.travelerIds.length < party.length
      ? party
          .filter((t) => s.travelerIds.includes(t.id))
          .map(displayName)
          .join(', ')
      : null;
  const reference = notBlank(s.confirmationCode)
    ? notBlank(s.bookedWith)
      ? `${s.confirmationCode} · ${s.bookedWith}`
      : s.confirmationCode
    : null;
  return {
    type: 'entry',
    time: `${clock(start)} ${zoneLabel(start)}`,
    title: segmentTitle(s),
    details: details.filter((d): d is string => d != null),
    people: notBlank(names) ? names : null,
    reference,
    note: notBlank(s.note) ? s.note : null
  };
}

function seats(seatMap: Record<string, string>, payload: DocumentPayload): string {
  const names = new Map(payload.dossier.party.travelers.map((t) => [t.id, displayName(t)]));
  return (
    'seats ' +
    Object.entries(seatMap)
      .map(([id, seat]) => `${names.get(id) ?? '?'} ${seat}`)
      .join(', ')
  );
}

function costBlocks(summary: CostSummary, payload: DocumentPayload, blocks: DocumentBlock[]): void {
  const names = new Map(payload.dossier.party.travelers.map((t) => [t.id, displayName(t)]));
  const name = (id: string) => names.get(id) ?? 'Somebody no longer on the trip';
  const money = (minor: number) => formatMoney({ minor, currency: summary.currency });

  blocks.push({ type: 'section', text: 'Costs' });
  blocks.push({ type: 'row', label: 'Spent together', value: money(summary.total) });
  for (const [category, minor] of summary.byCategory) {
    blocks.push({ type: 'row', label: EXPENSE_CATEGORY_LABEL[category], value: money(minor) });
  }
  if (summary.transfers.length === 0) {
    blocks.push({ type: 'note', text: 'Everyone has paid their share.' });
  } else {
    for (const t of summary.transfers) {
      blocks.push({ type: 'row', label: `${name(t.from)} pays ${name(t.to)}`, value: money(t.amount) });
    }
  }
  if (summary.unconverted.length > 0) {
    blocks.push({
      type: 'note',
      text:
        'Not counted, for want of a rate: ' +
        summary.unconverted.map((e) => `${e.description} (${formatMoney(e.amount)})`).join(', ') +
        '.'
    });
  }
}

function weatherLine(d: WeatherDay): string {
  const temps = `${temperature(d.highC)} / ${temperature(d.lowC)}`;
  const basis = d.basis === 'LAST_YEAR' ? ' · last year' : '';
  return `${temps} · ${describeWeather(d.code)}${basis}`;
}
