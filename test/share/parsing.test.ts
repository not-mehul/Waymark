import { describe, expect, it } from 'vitest';
import { durationMillis, segmentEnd } from '../../src/domain/model';
import { parseBoardingPass } from '../../src/share/bcbp';
import { isComplete, readBookingText, toSegment } from '../../src/share/booking-text';
import { date, t } from '../helpers';

const today = date(2026, 10, 22);

describe('Parsing', () => {
  it('a typical airline confirmation is read into complete flights', () => {
    const email = [
      'Your booking is confirmed. Booking reference: K7QH2P',
      '',
      'Outbound — Thursday 14 May 2026',
      'BA 286  San Francisco (SFO) to London Heathrow (LHR)',
      'Departs 16:20   Arrives 10:55 (+1)',
      'Terminal 5, seat 21A',
      '',
      'Return — 21 May 2026',
      'AF0084  Paris Charles de Gaulle (CDG) → San Francisco (SFO)',
      'Depart 13:25, arrive 15:40'
    ].join('\n');

    const reading = readBookingText(email, today);
    expect(reading.reference).toBe('K7QH2P');
    expect(reading.flights).toHaveLength(2);

    const out = reading.flights[0]!;
    expect(out.designator).toBe('BA286');
    expect(out.date).toEqual(date(2026, 5, 14));
    expect(out.origin).toBe('SFO');
    expect(out.destination).toBe('LHR');
    expect(out.departs).toEqual(t('16:20'));
    expect(out.arrives).toEqual(t('10:55'));
    expect(isComplete(out)).toBe(true);

    const back = reading.flights[1]!;
    expect(back.designator).toBe('AF84');
    expect(back.date).toEqual(date(2026, 5, 21));
    expect(back.origin).toBe('CDG');
    expect(back.arrives).toEqual(t('15:40'));
  });

  it("a complete flight becomes a booking in the airports' own zones", () => {
    const found = readBookingText(
      'BA 286 SFO to LHR on 14 May 2026, departs 16:20, arrives 10:55',
      today
    ).flights;
    expect(found).toHaveLength(1);
    const flight = toSegment(found[0]!, 'trip-1', 'seg-1', 'K7QH2P')!;
    expect(flight).not.toBeNull();
    expect(flight.carrierCode).toBe('BA');
    expect(flight.flightNumber).toBe('286');
    expect(flight.startZoneId).toBe('America/Los_Angeles');
    expect(flight.endZoneId).toBe('Europe/London');
    // Overnight: lands the next morning, ten and a half hours later.
    expect(segmentEnd(flight).toPlainDate()).toEqual(date(2026, 5, 15));
    expect(durationMillis(flight) / 60_000).toBe(10 * 60 + 35);
    expect(flight.confirmationCode).toBe('K7QH2P');
  });

  it('an incomplete flight does not become a booking', () => {
    const found = readBookingText('BA 286 SFO to LHR on 14 May 2026', today).flights;
    expect(found).toHaveLength(1);
    expect(toSegment(found[0]!, 'trip-1', 'seg-1', null)).toBeNull();
  });

  it('twelve-hour times and an American date are understood', () => {
    const text = 'Flight UA 930 on May 3 from SFO to LHR, departs 3:25 PM and arrives 9:50 AM';
    const flights = readBookingText(text, today).flights;
    expect(flights).toHaveLength(1);
    expect(flights[0]!.date).toEqual(date(2027, 5, 3));
    expect(flights[0]!.departs).toEqual(t('15:25'));
    expect(flights[0]!.arrives).toEqual(t('09:50'));
  });

  it('things that look like flight numbers but are not are left alone', () => {
    const text = 'Order ZZ 1234 shipped. Room 215, floor 2. Call 0800 123 456 before 18.05.2026.';
    expect(readBookingText(text, today).flights).toEqual([]);
  });

  it('a boarding pass barcode is read leg by leg', () => {
    // The worked example from IATA Resolution 792, with one leg.
    const pass = 'M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100';
    const legs = parseBoardingPass(pass, today);
    expect(legs).toHaveLength(1);
    const leg = legs[0]!;
    expect(leg.designator).toBe('AC834');
    expect(leg.origin).toBe('YUL');
    expect(leg.destination).toBe('FRA');
    expect(leg.seat).toBe('1A');
    expect(leg.cabin).toBe('Business');
    expect(leg.reference).toBe('ABC123');
    expect(leg.passenger).toBe('Luc Desmarais');
    // Day 326 of 2026 is 22 November — a month from today.
    expect(leg.date).toEqual(date(2026, 11, 22));
  });

  it('two legs on one pass are both read, past the variable field', () => {
    const first = 'ABC123 SFOLHRBA 0286 134Y021A0012 100';
    const second = 'ABC123 LHRCDGBA 0304 135Y003C0040 100';
    const legs = parseBoardingPass(`M2ELLISON/MARA        E${first}${second}`, today);
    expect(legs.map((l) => l.designator)).toEqual(['BA286', 'BA304']);
    expect(legs.map((l) => l.destination)).toEqual(['LHR', 'CDG']);
    expect(legs[0]!.seat).toBe('21A');
  });

  it('text that is not a boarding pass is not one', () => {
    expect(parseBoardingPass('https://example.com', today)).toEqual([]);
    expect(parseBoardingPass('', today)).toEqual([]);
    expect(parseBoardingPass('M1X', today)[0]).toBeUndefined();
  });
});
