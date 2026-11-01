// Everything the edit screen can change on a booking, as plain values, and
// the one function with rules in it: turning the form back into a booking.
// Ported from SegmentForm.kt, with its tests.
//
// Times are a date and a clock time in the booking's own zones, which is how
// they were entered and how they read on the timeline. Places are the words
// as shown; applyForm keeps the stored place while the words are unchanged,
// so editing a hotel's room never loses the hotel's map pin.

import { airportPlace, findAirport } from '../catalog/airports';
import { resolvePlace } from '../catalog/places';
import { parseFlightDesignator } from '../domain/flight-designator';
import { endAfter, isOrdered, MAX_FLIGHT_MINUTES } from '../domain/leg-times';
import {
  copyCommon,
  destinationOf,
  placeZone,
  positionOf,
  segmentEnd,
  segmentStart,
  type GroundMode,
  type Place,
  type Segment,
  type Traveler
} from '../domain/model';
import { zonedOf, type LocalDate, type LocalTime } from '../domain/time';

export interface SegmentForm {
  startDate: LocalDate;
  startTime: LocalTime;
  endDate: LocalDate;
  endTime: LocalTime;
  travelerIds: string[];
  note: string;
  confirmationCode: string;
  bookedWith: string;
  /** The flight number, the hotel, the experience, or a ground leg's operator. */
  name: string;
  /** Departure airport code, or where a stay, a booking or a ground leg is. */
  where: string;
  /** Arrival airport code, or where a ground leg ends. */
  destination: string;
  address: string;
  departureTerminal: string;
  departureGate: string;
  arrivalTerminal: string;
  arrivalGate: string;
  aircraft: string;
  cabin: string;
  operatedBy: string;
  seats: Record<string, string>;
  tickets: Record<string, string>;
  room: string;
  checkInNote: string;
  mode: GroundMode;
  pickup: string;
  category: string;
  tips: string;
}

export type FormProblem =
  | { kind: 'NO_NAME' }
  | { kind: 'NO_TRAVELER' }
  | { kind: 'UNKNOWN_AIRPORT'; code: string }
  | { kind: 'NOT_A_FLIGHT_NUMBER' }
  | { kind: 'ENDS_BEFORE_START' }
  | { kind: 'TOO_LONG_FOR_A_FLIGHT' };

export type FormOutcome = { saved: Segment } | { refused: FormProblem };

export function describeProblem(problem: FormProblem): string {
  switch (problem.kind) {
    case 'NO_NAME':
      return 'Give it a name.';
    case 'NO_TRAVELER':
      return 'Someone has to be on it.';
    case 'UNKNOWN_AIRPORT':
      return problem.code === ''
        ? 'Both airports are needed.'
        : `Waymark does not know an airport ${problem.code}.`;
    case 'NOT_A_FLIGHT_NUMBER':
      return 'That is not a flight number — it reads like BA286.';
    case 'ENDS_BEFORE_START':
      return 'It ends before it starts.';
    case 'TOO_LONG_FOR_A_FLIGHT':
      return "Longer than any flight — check the times against each airport's clock.";
  }
}

/** An airport is shown, and typed, as its code. */
const placeText = (p: Place) => p.code ?? p.name;

/** The form for a booking, with "everyone" spelled out so the chips show it. */
export function formOf(segment: Segment, party: readonly Traveler[]): SegmentForm {
  const start = segmentStart(segment);
  const end = segmentEnd(segment);
  const base: SegmentForm = {
    startDate: start.toPlainDate(),
    startTime: start.toPlainTime(),
    endDate: end.toPlainDate(),
    endTime: end.toPlainTime(),
    travelerIds: segment.travelerIds.length > 0 ? [...segment.travelerIds] : party.map((t) => t.id),
    note: segment.note ?? '',
    confirmationCode: segment.confirmationCode ?? '',
    bookedWith: segment.bookedWith ?? '',
    name: '',
    where: placeText(segment.origin),
    destination: placeText(destinationOf(segment)),
    address: segment.origin.address ?? '',
    departureTerminal: '',
    departureGate: '',
    arrivalTerminal: '',
    arrivalGate: '',
    aircraft: '',
    cabin: '',
    operatedBy: '',
    seats: {},
    tickets: {},
    room: '',
    checkInNote: '',
    mode: 'TRAIN',
    pickup: '',
    category: '',
    tips: ''
  };
  switch (segment.kind) {
    case 'FLIGHT':
      return {
        ...base,
        name: `${segment.carrierCode}${segment.flightNumber}`,
        departureTerminal: segment.departureTerminal ?? '',
        departureGate: segment.departureGate ?? '',
        arrivalTerminal: segment.arrivalTerminal ?? '',
        arrivalGate: segment.arrivalGate ?? '',
        aircraft: segment.aircraft ?? '',
        cabin: segment.cabin ?? '',
        operatedBy: segment.operatedBy ?? '',
        seats: { ...segment.seats },
        tickets: { ...segment.ticketNumbers }
      };
    case 'LODGING':
      return {
        ...base,
        name: segment.propertyName,
        room: segment.roomDescription ?? '',
        checkInNote: segment.checkInNote ?? ''
      };
    case 'GROUND':
      return {
        ...base,
        name: segment.provider ?? '',
        mode: segment.mode,
        pickup: segment.pickupInstruction ?? ''
      };
    case 'EXPERIENCE':
      return { ...base, name: segment.name, category: segment.category, tips: segment.curatedBy ?? '' };
  }
}

const clean = (text: string): string | null => text.trim() || null;

function cleaned(map: Record<string, string>, keep: ReadonlySet<string>): Record<string, string> {
  return Object.fromEntries(
    Object.entries(map)
      .map(([k, v]) => [k, v.trim()] as const)
      .filter(([k, v]) => v !== '' && keep.has(k))
  );
}

function airportFor(text: string, original: Place): Place | null {
  const code = text.trim().toUpperCase();
  if (code === original.code) return original;
  const found = findAirport(code);
  return found ? airportPlace(found) : null;
}

/** The stored place while the words are unchanged; otherwise what the words now mean. */
function placeFor(text: string, original: Place, near: Place): Place {
  const typed = text.trim();
  if (typed === '' || typed === placeText(original)) return original;
  return resolvePlace(typed, positionOf(near), near.timeZoneId).place;
}

/**
 * The booking as edited, or the first reason it cannot be. An end that is
 * not after the start but would be a day later runs past midnight, the same
 * rule Add plan uses; anything further out of order is refused.
 */
export function applyForm(form: SegmentForm, original: Segment, party: readonly Traveler[]): FormOutcome {
  if (party.length > 0 && form.travelerIds.length === 0) return { refused: { kind: 'NO_TRAVELER' } };
  const named = original.kind === 'LODGING' || original.kind === 'EXPERIENCE';
  if (named && form.name.trim() === '') return { refused: { kind: 'NO_NAME' } };

  let origin: Place;
  let arrival: Place;
  if (original.kind === 'FLIGHT') {
    const from = airportFor(form.where, original.origin);
    if (!from) return { refused: { kind: 'UNKNOWN_AIRPORT', code: form.where.trim() } };
    const to = airportFor(form.destination, original.destination);
    if (!to) return { refused: { kind: 'UNKNOWN_AIRPORT', code: form.destination.trim() } };
    origin = from;
    arrival = to;
  } else if (original.kind === 'GROUND') {
    origin = placeFor(form.where, original.origin, original.origin);
    arrival = placeFor(form.destination, original.destination, origin);
  } else {
    origin = placeFor(form.where, original.origin, original.origin);
    arrival = origin;
  }

  const start = zonedOf(form.startDate, form.startTime, placeZone(origin));
  const end = endAfter(start, form.endDate.toPlainDateTime(form.endTime), placeZone(arrival));
  if (!isOrdered(start, end)) return { refused: { kind: 'ENDS_BEFORE_START' } };

  const common = copyCommon(original, {
    startEpochMillis: start.epochMilliseconds,
    endEpochMillis: end.epochMilliseconds,
    startZoneId: origin.timeZoneId,
    endZoneId: arrival.timeZoneId,
    travelerIds: [...form.travelerIds],
    confirmationCode: clean(form.confirmationCode),
    bookedWith: clean(form.bookedWith),
    note: clean(form.note)
  });

  switch (common.kind) {
    case 'FLIGHT': {
      const designator = parseFlightDesignator(form.name);
      if (!designator) return { refused: { kind: 'NOT_A_FLIGHT_NUMBER' } };
      const minutes = (common.endEpochMillis - common.startEpochMillis) / 60_000;
      if (minutes > MAX_FLIGHT_MINUTES) return { refused: { kind: 'TOO_LONG_FOR_A_FLIGHT' } };
      // Only the people on the flight keep a seat or a ticket.
      const onBoard = new Set(form.travelerIds.length > 0 ? form.travelerIds : party.map((t) => t.id));
      return {
        saved: {
          ...common,
          carrierCode: designator.carrier,
          flightNumber: `${designator.number}${designator.suffix ?? ''}`,
          origin,
          destination: arrival,
          departureTerminal: clean(form.departureTerminal),
          departureGate: clean(form.departureGate)?.toUpperCase() ?? null,
          arrivalTerminal: clean(form.arrivalTerminal),
          arrivalGate: clean(form.arrivalGate)?.toUpperCase() ?? null,
          aircraft: clean(form.aircraft),
          cabin: clean(form.cabin),
          operatedBy: clean(form.operatedBy),
          seats: cleaned(form.seats, onBoard),
          ticketNumbers: cleaned(form.tickets, onBoard)
        }
      };
    }
    case 'LODGING':
      return {
        saved: {
          ...common,
          propertyName: form.name.trim(),
          origin: { ...origin, address: clean(form.address) },
          endZoneId: origin.timeZoneId,
          roomDescription: clean(form.room),
          checkInNote: clean(form.checkInNote)
        }
      };
    case 'GROUND':
      return {
        saved: {
          ...common,
          mode: form.mode,
          origin,
          destination: arrival,
          provider: clean(form.name),
          pickupInstruction: clean(form.pickup)
        }
      };
    case 'EXPERIENCE':
      return {
        saved: {
          ...common,
          name: form.name.trim(),
          category: clean(form.category) ?? common.category,
          origin: { ...origin, address: clean(form.address) },
          endZoneId: origin.timeZoneId,
          curatedBy: clean(form.tips)
        }
      };
  }
}
