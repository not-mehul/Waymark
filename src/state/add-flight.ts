// A flight, typed in. Nothing is fetched or filled in from a schedule: the
// only thing the app contributes is the coordinates and zone behind an
// airport code. Ported from AddFlightUiState and AddFlightViewModel.

import { airlineName } from '../catalog/airlines';
import { airportPlace, findAirport, type Airport } from '../catalog/airports';
import { parseFlightDesignator } from '../domain/flight-designator';
import { flightLeg, type Leg } from '../domain/leg-times';
import { flight, tripEndDate, tripStartDate, type Flight, type Trip } from '../domain/model';
import { compareDates, zoneOrUtc, type LocalDate, type LocalTime } from '../domain/time';
import { newId } from '../db/trips';
import { parseBoardingPass } from '../share/bcbp';

export interface FlightEntry {
  designator: string;
  date: LocalDate;
  origin: string;
  destination: string;
  departTime: LocalTime | null;
  arriveTime: LocalTime | null;
  /** Null while the arrival day is inferred from the clock times. */
  arrivalDayOffset: number | null;
  departureTerminal: string;
  arrivalTerminal: string;
  aircraft: string;
  cabin: string;
  operatedBy: string;
  selectedTravelers: string[];
  confirmationCode: string;
  eTickets: Record<string, string>;
  seats: Record<string, string>;
}

/** A flight half-known from somewhere else, waiting to be checked and saved. */
export interface FlightDraft {
  designator?: string | null;
  date?: LocalDate | null;
  origin?: string | null;
  destination?: string | null;
  departs?: LocalTime | null;
  arrives?: LocalTime | null;
  confirmationCode?: string | null;
  cabin?: string | null;
  seat?: string | null;
}

/**
 * The trip's first day, or today once the trip is under way: a flight being
 * added is almost never one that has already gone.
 */
export function defaultFlightDate(trip: Trip, today: LocalDate): LocalDate {
  const start = tripStartDate(trip);
  if (compareDates(today, start) < 0 || compareDates(today, tripEndDate(trip)) > 0) return start;
  return today;
}

export function newFlightEntry(date: LocalDate, travelerIds: string[]): FlightEntry {
  return {
    designator: '',
    date,
    origin: '',
    destination: '',
    departTime: null,
    arriveTime: null,
    arrivalDayOffset: null,
    departureTerminal: '',
    arrivalTerminal: '',
    aircraft: '',
    cabin: '',
    operatedBy: '',
    selectedTravelers: travelerIds,
    confirmationCode: '',
    eTickets: {},
    seats: {}
  };
}

/** Fill in what something else read: a pasted email, a boarding pass. */
export function prefill(entry: FlightEntry, draft: FlightDraft): FlightEntry {
  const only = entry.selectedTravelers.length === 1 ? entry.selectedTravelers[0]! : null;
  return {
    ...entry,
    designator: draft.designator ?? entry.designator,
    date: draft.date ?? entry.date,
    origin: draft.origin ?? entry.origin,
    destination: draft.destination ?? entry.destination,
    departTime: draft.departs ?? entry.departTime,
    arriveTime: draft.arrives ?? entry.arriveTime,
    confirmationCode: draft.confirmationCode ?? entry.confirmationCode,
    cabin: draft.cabin ?? entry.cabin,
    seats: draft.seat && only ? { [only]: draft.seat } : entry.seats
  };
}

/** The flight as the times describe it, or null while they cannot. */
export function entryLeg(entry: FlightEntry): Leg | null {
  const from = findAirport(entry.origin);
  const to = findAirport(entry.destination);
  if (!from || !to || !entry.departTime || !entry.arriveTime) return null;
  return flightLeg(
    entry.date,
    entry.departTime,
    zoneOrUtc(from.timeZoneId),
    entry.arriveTime,
    zoneOrUtc(to.timeZoneId),
    entry.arrivalDayOffset
  );
}

/** Why the times do not make a flight, once there are enough of them to judge. */
export function timeProblem(entry: FlightEntry): string | null {
  if (!findAirport(entry.origin) || !findAirport(entry.destination)) return null;
  if (!entry.departTime || !entry.arriveTime || entryLeg(entry)) return null;
  return entry.arrivalDayOffset !== null
    ? 'That arrival day lands before it leaves, or after a day in the air.'
    : 'These times do not make a flight between these airports. Check the zones.';
}

/** Only the four facts a flight cannot exist without are required. */
export function canSave(entry: FlightEntry): boolean {
  const from = findAirport(entry.origin);
  const to = findAirport(entry.destination);
  return (
    parseFlightDesignator(entry.designator) !== null &&
    from !== null &&
    to !== null &&
    from.code !== to.code &&
    entryLeg(entry) !== null
  );
}

const blank = (text: string) => text.trim() || null;
const filled = (map: Record<string, string>) =>
  Object.fromEntries(Object.entries(map).filter(([, v]) => v.trim() !== ''));

export function flightOf(entry: FlightEntry, tripId: string, party: readonly string[]): Flight | null {
  const designator = parseFlightDesignator(entry.designator);
  const from = findAirport(entry.origin);
  const to = findAirport(entry.destination);
  const leg = entryLeg(entry);
  if (!designator || !from || !to || !leg) return null;
  const origin = airportPlace(from);
  const destination = airportPlace(to);
  const everyone =
    entry.selectedTravelers.length === party.length &&
    party.every((id) => entry.selectedTravelers.includes(id));
  return flight({
    id: newId('seg'),
    tripId,
    carrierCode: designator.carrier,
    // The suffix is part of the number: LH1234A is not LH1234.
    flightNumber: `${designator.number}${designator.suffix ?? ''}`,
    origin,
    destination,
    startEpochMillis: leg.departure.epochMilliseconds,
    endEpochMillis: leg.arrival.epochMilliseconds,
    startZoneId: origin.timeZoneId,
    endZoneId: destination.timeZoneId,
    // Everyone ticked is stored as everyone, so a traveler added later is on it too.
    travelerIds: everyone ? [] : [...entry.selectedTravelers],
    departureTerminal: blank(entry.departureTerminal),
    arrivalTerminal: blank(entry.arrivalTerminal),
    aircraft: blank(entry.aircraft),
    cabin: blank(entry.cabin),
    seats: filled(entry.seats),
    ticketNumbers: filled(entry.eTickets),
    confirmationCode: blank(entry.confirmationCode),
    bookedWith: airlineName(designator.carrier),
    // Only when the traveler says so: the marketing carrier is not evidence
    // of who flies the aircraft.
    operatedBy: blank(entry.operatedBy)
  });
}

/** "San Francisco · San Francisco International", for the hint under a station field. */
export const stationHint = (a: Airport) => `${a.city} · ${a.name}`;

/**
 * A flight from the text in a boarding pass's barcode, with the line to show
 * above the form; null when the text is not a pass.
 */
export function fromBoardingPass(
  entry: FlightEntry,
  text: string,
  today: LocalDate
): { entry: FlightEntry; notice: string } | null {
  const legs = parseBoardingPass(text, today);
  const leg = legs[0];
  if (!leg) return null;
  return {
    entry: prefill(entry, {
      designator: leg.designator,
      date: leg.date,
      origin: leg.origin,
      destination: leg.destination,
      confirmationCode: leg.reference,
      cabin: leg.cabin,
      seat: leg.seat
    }),
    notice:
      'Read from the pass. Add the departure and arrival times; a pass does not carry them.' +
      (legs.length > 1 ? ` It has ${legs.length} flights; this is the first.` : '')
  };
}
