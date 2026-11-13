import { describe, expect, it } from 'vitest';
import { fold, searchTrips } from '../../src/domain/search';
import { sample } from '../helpers';

const bundle = sample();

describe('searchTrips', () => {
  it('finds a booking by its confirmation code, case aside', () => {
    const hits = searchTrips([bundle], 'k7qh2p');
    expect(hits).toHaveLength(1);
    expect(hits[0]!.segmentId).not.toBeNull();
  });

  it('finds a hotel by its name with the accents left off', () => {
    const hits = searchTrips([bundle], 'hotel saint jacques');
    expect(hits.map((h) => h.title)).toContain('Hôtel Saint-Jacques');
  });

  it('needs every word, and takes the trip name into account', () => {
    expect(searchTrips([bundle], 'bloomsbury zanzibar')).toHaveLength(0);
    const named = { ...bundle, trip: { ...bundle.trip, name: 'Spring in Europe' } };
    const hits = searchTrips([named], 'spring bloomsbury');
    expect(hits.some((h) => h.title === 'The Bloomsbury Rooms')).toBe(true);
  });

  it('lists a trip found by name once, and nothing for one letter', () => {
    const named = { ...bundle, trip: { ...bundle.trip, name: 'Zebra weekend' } };
    expect(searchTrips([named], 'zebra')).toEqual([expect.objectContaining({ segmentId: null })]);
    expect(searchTrips([named], 'z')).toEqual([]);
  });

  it('folds accents and case', () => {
    expect(fold('Hôtel ÉTÉ')).toBe('hotel ete');
  });
});
