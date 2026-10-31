import { describe, expect, it } from 'vitest';
import { airportPlaceFor } from '../../src/catalog/airports';
import {
  designator,
  flight as flightOf,
  segmentEnd,
  shortLabel,
  SEGMENT_KINDS,
  type Flight,
  type Lodging,
  type Segment,
  type SegmentKind
} from '../../src/domain/model';
import {
  LEAD_OFF,
  REMINDER_CATEGORIES,
  REMINDER_CATEGORY,
  ReminderPreferences,
  dueReminders,
  isOn,
  leaveBy,
  momentOf,
  nextDueAt,
  reminderHeadline,
  type Lead
} from '../../src/domain/reminders';
import { localTime, zonedOf } from '../../src/domain/time';
import { estimateBest, totalMinutes } from '../../src/domain/transit';
import { HOUR, MINUTE, sample } from '../helpers';

const bundle = sample();
const segments = bundle.segments;
const firstOf = (kind: SegmentKind): Segment => segments.find((s) => s.kind === kind)!;
const DEFAULT = ReminderPreferences.DEFAULT;
const everyCategory = (lead: Lead) => REMINDER_CATEGORIES.reduce((acc, c) => acc.with(c, lead), DEFAULT);

describe('ReminderPlanner', () => {
  it('defaults cover the things a traveler would miss', () => {
    expect(isOn(DEFAULT.get('FLIGHTS'))).toBe(true);
    expect(isOn(DEFAULT.get('GROUND'))).toBe(true);
    expect(isOn(DEFAULT.get('BOOKINGS'))).toBe(true);
    // A hotel check-in is not something anyone needs waking for.
    expect(isOn(DEFAULT.get('STAYS'))).toBe(false);
    expect(isOn(DEFAULT.get('CHECK_IN'))).toBe(true);
    expect(isOn(DEFAULT.get('LEAVE'))).toBe(true);
    expect(DEFAULT.anyOn).toBe(true);
  });

  // The gap this fills: everything except flights used to pass unremarked.
  it('every category can raise a reminder, not only flights', () => {
    const everythingOn = everyCategory(2 * 60);
    for (const kind of SEGMENT_KINDS) {
      const segment = firstOf(kind);
      const ids = dueReminders(segments, everythingOn, segment.startEpochMillis - HOUR).map(
        (r) => r.segmentId
      );
      expect(ids.length, `${kind} raised nothing`).toBeGreaterThan(0);
    }
  });

  it('a category switched off raises nothing', () => {
    const flight = firstOf('FLIGHT');
    const off = DEFAULT.with('FLIGHTS', LEAD_OFF);
    const due = dueReminders(segments, off, flight.startEpochMillis - HOUR);
    expect(due.some((r) => r.segmentId === flight.id && r.moment === 'START')).toBe(false);
  });

  it('nothing is raised before the lead time or after the start', () => {
    const flight = firstOf('FLIGHT');
    const twoHours = DEFAULT.with('FLIGHTS', 2 * 60);
    const raisedAt = (now: number) =>
      dueReminders(segments, twoHours, now).some((r) => r.segmentId === flight.id && r.moment === 'START');

    expect(raisedAt(flight.startEpochMillis - 3 * HOUR), 'too early').toBe(false);
    expect(raisedAt(flight.startEpochMillis - 90 * MINUTE), 'inside the window').toBe(true);
    expect(raisedAt(flight.startEpochMillis - 2 * HOUR), 'at the boundary').toBe(true);
    expect(raisedAt(flight.startEpochMillis + MINUTE), 'already gone').toBe(false);
  });

  // The signature deliberately omits the countdown, so a check at two hours
  // out does not re-announce what a check at three hours out already said.
  it('the signature is stable as the clock closes in, and changes when the booking moves', () => {
    const flight = firstOf('FLIGHT');
    const preferences = DEFAULT.with('FLIGHTS', 3 * 60);
    const signatureAt = (itinerary: Segment[], now: number) =>
      dueReminders(itinerary, preferences, now).find(
        (r) => r.segmentId === flight.id && r.moment === 'START'
      )!.signature;

    const early = signatureAt(segments, flight.startEpochMillis - 3 * HOUR);
    const late = signatureAt(segments, flight.startEpochMillis - 20 * MINUTE);
    expect(late).toBe(early);

    const moved = segments.map((s) =>
      s.id !== flight.id
        ? s
        : { ...s, startEpochMillis: s.startEpochMillis + HOUR, endEpochMillis: s.endEpochMillis + HOUR }
    );
    const afterMove = signatureAt(moved, flight.startEpochMillis - 20 * MINUTE);
    expect(afterMove, 'a rescheduled booking must announce itself again').not.toBe(early);
  });

  it('the headline names the thing and how long is left', () => {
    const flight = firstOf('FLIGHT') as Flight;
    const preferences = DEFAULT.with('FLIGHTS', 3 * 60);
    const reminder = dueReminders(segments, preferences, flight.startEpochMillis - 2 * HOUR).find(
      (r) => r.segmentId === flight.id && r.moment === 'START'
    )!;

    expect(reminder.headline).toContain(designator(flight));
    expect(reminder.headline).toContain('2h');
    expect(reminder.label).toBe(designator(flight));
    expect(reminder.detail).toContain(shortLabel(flight.origin));
  });

  it('the next reminder is the earliest one still to fire, and none when all are off', () => {
    const flight = firstOf('FLIGHT');
    const now = flight.startEpochMillis - 30 * HOUR;
    // Online check-in, a day before the first departure, comes first.
    expect(nextDueAt(segments, DEFAULT, now)).toBe(flight.startEpochMillis - 24 * HOUR);

    const silent = everyCategory(LEAD_OFF);
    expect(nextDueAt(segments, silent, now)).toBeNull();
    expect(silent.anyOn).toBe(false);
    expect(dueReminders(segments, silent, segments[0]!.startEpochMillis)).toEqual([]);
  });

  it('check-in is a day ahead and stops mattering when check-in closes', () => {
    const flight = firstOf('FLIGHT');
    const checkIns = (now: number) =>
      dueReminders(segments, DEFAULT, now).filter(
        (r) => r.segmentId === flight.id && r.moment === 'CHECK_IN'
      );

    expect(checkIns(flight.startEpochMillis - 25 * HOUR)).toEqual([]);
    const due = checkIns(flight.startEpochMillis - 23 * HOUR);
    expect(due).toHaveLength(1);
    expect(due[0]!.headline).toContain('Check in online');
    expect(due[0]!.signature.startsWith('checkin|')).toBe(true);
    expect(checkIns(flight.startEpochMillis - 30 * MINUTE)).toEqual([]);
  });

  it('time to leave counts the journey from the hotel and the time at the airport', () => {
    const london = 'Europe/London';
    const hotel = segments.find((s) => s.id === 'seg-hotel-london') as Lodging;
    const heathrow = airportPlaceFor('LHR');
    const paris = airportPlaceFor('CDG');
    const day = segmentEnd(hotel).toPlainDate();
    const flight = flightOf({
      id: 'lhr-cdg',
      tripId: hotel.tripId,
      carrierCode: 'BA',
      flightNumber: '304',
      origin: heathrow,
      destination: paris,
      startEpochMillis: zonedOf(day, localTime(16, 0), london).epochMilliseconds,
      endEpochMillis: zonedOf(day, localTime(18, 20), london).epochMilliseconds,
      startZoneId: london,
      endZoneId: 'Europe/Paris'
    });
    const itinerary = [hotel, flight];

    const leave = leaveBy(flight, itinerary)!;
    expect(leave).not.toBeNull();
    const journey = totalMinutes(estimateBest(hotel.origin, heathrow));
    // International: three hours at the airport, plus the journey there.
    expect(leave).toBe(flight.startEpochMillis - (180 + journey) * MINUTE);

    const due = dueReminders(itinerary, DEFAULT, leave - 20 * MINUTE).filter((r) => r.moment === 'LEAVE');
    expect(due).toHaveLength(1);
    expect(due[0]!.headline.startsWith('Leave for LHR in'), due[0]!.headline).toBe(true);
    expect(due[0]!.detail).toContain(hotel.origin.name);
  });

  it('no time to leave is invented for a connection or a flight with nothing before it', () => {
    expect(
      leaveBy(
        segments.find((s) => s.id === 'seg-outbound')!,
        segments
      )
    ).toBeNull();
    // The booked transfer already ends at the airport.
    expect(
      leaveBy(
        segments.find((s) => s.id === 'seg-return')!,
        segments
      )
    ).toBeNull();
  });

  it("the banner's headline is recomputed from the clock", () => {
    const flight = firstOf('FLIGHT');
    expect(reminderHeadline(flight, 'START', flight.startEpochMillis - 3 * HOUR).endsWith('in 3h')).toBe(
      true
    );
    expect(reminderHeadline(flight, 'START', flight.startEpochMillis - 40 * MINUTE).endsWith('in 40m')).toBe(
      true
    );
    expect(momentOf('leave|x|1')).toBe('LEAVE');
    expect(momentOf('reminder|x|1')).toBe('START');
  });

  it('the summary reads as a sentence rather than a dump', () => {
    const onlyFlights = REMINDER_CATEGORIES.reduce(
      (acc, c) => (c === 'FLIGHTS' ? acc : acc.with(c, LEAD_OFF)),
      DEFAULT
    );
    expect(onlyFlights.summary()).toBe('Flights 3h');
    expect(everyCategory(LEAD_OFF).summary()).toBe('Off for everything');
  });

  it('every category offers off first, then ascending choices that include its default', () => {
    for (const category of REMINDER_CATEGORIES) {
      const { choices, default: fallback } = REMINDER_CATEGORY[category];
      expect(choices[0]).toBe(LEAD_OFF);
      expect([...choices].sort((a, b) => a - b)).toEqual(choices);
      expect(choices, category).toContain(fallback);
    }
  });
});
