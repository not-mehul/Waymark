// Nights are calendar nights in the hotel's own zone. A 15:00 check-in and an
// 11:00 check-out four days later is four nights, though it is only 3 days
// and 20 hours by the clock.
import { describe, expect, it } from 'vitest';
import { lodging, nights, place, type Lodging } from '../../src/domain/model';
import type { LocalDate } from '../../src/domain/time';
import { at, date, sample } from '../helpers';

const LONDON = 'Europe/London';

function stay(inDate: LocalDate, inTime: string, outDate: LocalDate, outTime: string) {
  return lodging({
    id: 'stay',
    tripId: 'trip',
    propertyName: 'Hotel',
    origin: place({ name: 'Hotel', timeZoneId: LONDON }),
    startEpochMillis: at(inDate, inTime, LONDON),
    endEpochMillis: at(outDate, outTime, LONDON),
    startZoneId: LONDON,
    endZoneId: LONDON
  });
}

describe('Lodging nights', () => {
  it('the worked example has four nights in London and three in Paris', () => {
    const stays = new Map(
      sample()
        .segments.filter((s): s is Lodging => s.kind === 'LODGING')
        .map((s) => [s.id, s])
    );
    expect(nights(stays.get('seg-hotel-london')!)).toBe(4);
    expect(nights(stays.get('seg-hotel-paris')!)).toBe(3);
  });

  it('a late check-in and an early check-out is still one night', () => {
    const day = date(2026, 3, 2);
    expect(nights(stay(day, '20:00', day.add({ days: 1 }), '10:00'))).toBe(1);
  });

  it('a day room counts as one rather than zero', () => {
    const day = date(2026, 3, 2);
    expect(nights(stay(day, '09:00', day, '17:00'))).toBe(1);
  });

  it('a stay across the clocks going forward is not a night short', () => {
    // The UK moves to summer time on 29 March 2026.
    const day = date(2026, 3, 28);
    expect(nights(stay(day, '15:00', day.add({ days: 2 }), '11:00'))).toBe(2);
  });
});
