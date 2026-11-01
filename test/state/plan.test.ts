import { beforeAll, describe, expect, it } from 'vitest';
import { AIRPORTS_ASSET, installAirports, parseAirportDirectory } from '../../src/catalog/airports';
import { CITIES_ASSET, installCities, parseCityDirectory } from '../../src/catalog/cities';
import { buildSampleItinerary } from '../../src/catalog/sample-itinerary';
import { segmentEnd, segmentStart, type TripDossier } from '../../src/domain/model';
import { localDate, localTime } from '../../src/domain/time';
import { newPlanDraft, planSegment, withKind } from '../../src/state/plan';
import { bundledLines } from '../helpers';

const bundle = buildSampleItinerary(localDate(2026, 5, 14));
const dossier: TripDossier = {
  trip: bundle.trip,
  party: { tripId: bundle.trip.id, travelers: bundle.travelers },
  segments: bundle.segments
};

beforeAll(() => {
  installAirports(parseAirportDirectory(bundledLines(AIRPORTS_ASSET)));
  installCities(parseCityDirectory(bundledLines(CITIES_ASSET)));
});

describe('planSegment', () => {
  it('reads a stay in Paris on the Paris clock, a night long', () => {
    const draft = { ...newPlanDraft(localDate(2026, 5, 19), []), title: 'Hôtel du Nord', where: 'Paris' };
    const stay = planSegment(draft, dossier.trip.id, dossier)!;
    expect(stay.kind).toBe('LODGING');
    expect(stay.startZoneId).toBe('Europe/Paris');
    expect(segmentStart(stay).toPlainTime()).toEqual(localTime(15));
    expect(segmentEnd(stay).toPlainDate()).toEqual(localDate(2026, 5, 20));
  });

  it('runs a late booking past midnight, and refuses one with no name', () => {
    const base = withKind(newPlanDraft(localDate(2026, 5, 16), []), 'EXPERIENCE');
    const late = { ...base, title: 'Jazz', where: 'London', startTime: localTime(22), endTime: localTime(1) };
    expect(segmentEnd(planSegment(late, dossier.trip.id, dossier)!).toPlainDate()).toEqual(
      localDate(2026, 5, 17)
    );
    expect(planSegment({ ...late, title: ' ' }, dossier.trip.id, dossier)).toBeNull();
  });

  it('takes a ground leg from one place to another, each on its own clock', () => {
    const draft = {
      ...withKind(newPlanDraft(localDate(2026, 5, 17), []), 'GROUND'),
      title: 'Eurostar',
      where: 'London',
      destination: 'Paris',
      startTime: localTime(9),
      endTime: localTime(12, 30)
    };
    const leg = planSegment(draft, dossier.trip.id, dossier)!;
    expect(leg.kind === 'GROUND' && leg.destination.city).toBe('Paris');
    expect([leg.startZoneId, leg.endZoneId]).toEqual(['Europe/London', 'Europe/Paris']);
  });
});
