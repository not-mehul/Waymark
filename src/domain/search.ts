// Finding something across every trip: the hotel in Lisbon two years ago,
// a confirmation code read out on the phone, the flight with BA in it.
// Every word typed has to appear somewhere on the booking (accents and case
// aside); trips match on their name and where they went.

import {
  designator,
  destinationOf,
  segmentStart,
  segmentTitle,
  type Idea,
  type Place,
  type Segment,
  type Trip
} from './model';

export interface Searchable {
  trip: Trip;
  segments: readonly Segment[];
  ideas?: readonly Idea[];
}

export interface SearchHit {
  tripId: string;
  tripName: string;
  /** Null for the trip itself. */
  segmentId: string | null;
  title: string;
  /** Epoch millis of the booking (or the trip's start), for its date line. */
  atMillis: number;
  zoneId: string;
}

export const MAX_HITS = 60;

export function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

const placeWords = (p: Place) => [p.name, p.code ?? '', p.city, p.country, p.address ?? ''];

function segmentText(s: Segment): string {
  const words: string[] = [segmentTitle(s), s.confirmationCode ?? '', s.bookedWith ?? '', s.note ?? ''];
  words.push(...placeWords(s.origin));
  if (destinationOf(s) !== s.origin) words.push(...placeWords(destinationOf(s)));
  switch (s.kind) {
    case 'FLIGHT':
      words.push(
        designator(s),
        `${s.carrierCode} ${s.flightNumber}`,
        s.operatedBy ?? '',
        ...Object.values(s.ticketNumbers),
        ...Object.values(s.seats)
      );
      break;
    case 'LODGING':
      words.push(s.propertyName, s.roomDescription ?? '', s.checkInNote ?? '');
      break;
    case 'GROUND':
      words.push(s.mode.replace(/_/g, ' '), s.provider ?? '', s.pickupInstruction ?? '');
      break;
    case 'EXPERIENCE':
      words.push(s.name, s.category, s.curatedBy ?? '');
      break;
  }
  return fold(words.join(' \u0000 '));
}

/** Newest trips first; inside a trip, bookings in time order. */
export function searchTrips(trips: readonly Searchable[], query: string): SearchHit[] {
  const terms = fold(query)
    .split(/\s+/)
    .filter((t) => t.length > 0);
  if (terms.length === 0 || terms.join('').length < 2) return [];
  const matches = (text: string) => terms.every((t) => text.includes(t));
  const hits: SearchHit[] = [];
  const ordered = [...trips].sort((a, b) => b.trip.startEpochMillis - a.trip.startEpochMillis);
  for (const { trip, segments } of ordered) {
    const tripText = fold(`${trip.name} ${trip.destinationSummary}`);
    const tripMatched = matches(tripText);
    if (tripMatched) {
      hits.push({
        tripId: trip.id,
        tripName: trip.name,
        segmentId: null,
        title: trip.name,
        atMillis: trip.startEpochMillis,
        zoneId: trip.homeZoneId
      });
    }
    for (const s of [...segments].sort((a, b) => a.startEpochMillis - b.startEpochMillis)) {
      const own = segmentText(s);
      // A trip's name counts for its bookings too: "lisbon hotel" finds the
      // hotel on the Lisbon trip. A trip found by name alone lists itself once.
      if (!matches(own) && (tripMatched || !matches(`${tripText} ${own}`))) continue;
      hits.push({
        tripId: trip.id,
        tripName: trip.name,
        segmentId: s.id,
        title: segmentTitle(s),
        atMillis: s.startEpochMillis,
        zoneId: segmentStart(s).timeZoneId
      });
    }
    if (hits.length >= MAX_HITS) break;
  }
  return hits.slice(0, MAX_HITS);
}
