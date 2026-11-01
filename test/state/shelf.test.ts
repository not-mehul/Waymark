import { describe, expect, it } from 'vitest';
import { traveler, trip, type Trip } from '../../src/domain/model';
import { arrangeShelf, cardStatus, profileSummary } from '../../src/state/shelf';

const DAY = 86_400_000;
const NOW = Date.UTC(2026, 10, 1, 12);

function at(id: string, startDays: number, lengthDays: number, archived = false): Trip {
  const start = Date.UTC(2026, 10, 1) + startDays * DAY;
  return trip({
    id,
    name: id,
    destinationSummary: '',
    startEpochMillis: start,
    endEpochMillis: start + lengthDays * DAY - 60_000,
    homeZoneId: 'UTC',
    archived
  });
}

describe('arrangeShelf', () => {
  it('puts each trip in its group, in the order the shelf reads them', () => {
    const shelf = arrangeShelf(
      [at('later', 30, 3), at('soon', 5, 3), at('now', -1, 4), at('long-ago', -90, 3), at('lately', -20, 3)],
      NOW
    );
    expect(shelf.active.map((t) => t.id)).toEqual(['now']);
    expect(shelf.upcoming.map((t) => t.id)).toEqual(['soon', 'later']);
    expect(shelf.past.map((t) => t.id)).toEqual(['lately', 'long-ago']);
  });

  it('keeps put-away trips apart, most recent first', () => {
    const shelf = arrangeShelf([at('old', -90, 3, true), at('ahead', 10, 3, true), at('soon', 5, 3)], NOW);
    expect(shelf.upcoming.map((t) => t.id)).toEqual(['soon']);
    expect(shelf.archived.map((t) => t.id)).toEqual(['ahead', 'old']);
  });
});

describe('cardStatus', () => {
  it('counts the days to departure', () => {
    expect(cardStatus(at('a', 0, 3), Date.UTC(2026, 10, 1) - 3_600_000)).toEqual({
      text: 'Departs tomorrow',
      tone: 'upcoming'
    });
    expect(cardStatus(at('a', 6, 3), NOW)).toEqual({ text: 'In 6 days', tone: 'upcoming' });
    expect(cardStatus(at('a', -1, 4), NOW)).toEqual({ text: 'Under way', tone: 'active' });
    expect(cardStatus(at('a', -9, 3), NOW)).toEqual({ text: 'Complete', tone: 'past' });
  });

  it('says today for a trip that starts later today', () => {
    const today = trip({ ...at('a', 0, 1), startEpochMillis: NOW + 3_600_000 });
    expect(cardStatus(today, NOW).text).toBe('Departs today');
  });
});

describe('profileSummary', () => {
  it('lists the standing details, folding extra loyalty numbers into a count', () => {
    const t = traveler({
      id: 't',
      fullName: 'Ada Lovelace',
      seatPreference: 'WINDOW',
      mealPreference: 'Vegetarian',
      loyaltyRefs: ['BA 123', 'AF 456', 'LH 789'],
      contactPhone: '+44 20 7946 0000'
    });
    expect(profileSummary(t)).toBe('Window · Vegetarian · BA 123 +2 · +44 20 7946 0000');
  });

  it('has nothing to say about a bare profile', () => {
    expect(profileSummary(traveler({ id: 't', fullName: 'Ada' }))).toBeNull();
  });
});
