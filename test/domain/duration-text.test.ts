// How a span reads, at every scale a trip actually produces — from a tight
// connection to a season away.
import { describe, expect, it } from 'vitest';
import { duration, durationBetween } from '../../src/domain/time-text';

const hour = 60;
const day = 24 * hour;
const week = 7 * day;

describe('TimeText.duration', () => {
  it('under a day stays in hours and minutes', () => {
    expect(duration(45)).toBe('45m');
    expect(duration(60)).toBe('1h');
    expect(duration(160)).toBe('2h 40m');
    expect(duration(day - 1)).toBe('23h 59m');
  });

  it('a day or more drops minutes and reads in days', () => {
    expect(duration(day)).toBe('1d');
    expect(duration(day + 6 * hour)).toBe('1d 6h');
    // The case that prompted this: a 30-hour layover is not "30h".
    expect(duration(30 * hour)).toBe('1d 6h');
    expect(duration(week - hour)).toBe('6d 23h');
  });

  it('a week or more reads in weeks and days', () => {
    expect(duration(week)).toBe('1w');
    expect(duration(week + 3 * day)).toBe('1w 3d');
    expect(duration(2 * week)).toBe('2w');
    // Hours are gone at this scale, not rounded into a day.
    expect(duration(week + 3 * day + 20 * hour)).toBe('1w 3d');
  });

  it('four weeks or more reads in months, weeks and days', () => {
    expect(duration(4 * week)).toBe('1mo');
    expect(duration(5 * week)).toBe('1mo 1w');
    expect(duration(5 * week + 3 * day)).toBe('1mo 1w 3d');
    expect(duration(8 * week)).toBe('2mo');
    expect(duration(12 * week + 2 * week + day)).toBe('3mo 2w 1d');
  });

  it('zero terms are dropped rather than printed', () => {
    expect(duration(2 * hour)).toBe('2h');
    expect(duration(3 * day)).toBe('3d');
    expect(duration(2 * week)).toBe('2w');
    expect(duration(4 * week)).not.toContain('0');
  });

  it('a negative span keeps its sign at every scale', () => {
    expect(duration(-20)).toBe('−20m');
    expect(duration(-30 * hour)).toBe('−1d 6h');
    expect(duration(-(week + 3 * day))).toBe('−1w 3d');
  });

  it('nothing at all still prints something', () => {
    expect(duration(0)).toBe('0m');
  });

  // Checked on the leading term: the trailing one legitimately goes the other
  // way ("1d" is followed by "1d 1h").
  it('the leading unit only ever coarsens as the span grows', () => {
    const rank: Record<string, number> = { m: 0, h: 1, d: 2, w: 3, mo: 4 };
    let previous = 0;
    for (let minutes = 1; minutes <= 20 * week; minutes += 37) {
      const leading = duration(minutes).split(' ')[0]!.replace(/^\d+/, '');
      const current = rank[leading]!;
      expect(
        current,
        `at ${minutes} minutes the unit got finer: ${duration(minutes)}`
      ).toBeGreaterThanOrEqual(previous);
      previous = current;
    }
  });

  it('no span is written with more than three terms', () => {
    for (let minutes = 1; minutes <= 60 * week; minutes += 53) {
      expect(duration(minutes).split(' ').length).toBeLessThanOrEqual(3);
    }
  });

  it('durationBetween agrees with duration', () => {
    const start = 1_700_000_000_000;
    expect(durationBetween(start, start + (3 * day + 4 * hour) * 60_000)).toBe(duration(3 * day + 4 * hour));
  });
});
