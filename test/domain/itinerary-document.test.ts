import { describe, expect, it } from 'vitest';
import { summarizeCosts } from '../../src/domain/costs';
import {
  buildItineraryDocument,
  documentFileName,
  type DocumentBlock
} from '../../src/domain/itinerary-document';
import { displayName, expense, segmentStart, tripStartDate, type WeatherDay } from '../../src/domain/model';
import { dossierOf, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);

const ofType = <T extends DocumentBlock['type']>(blocks: DocumentBlock[], type: T) =>
  blocks.filter((b): b is Extract<DocumentBlock, { type: T }> => b.type === type);
const hasCostsSection = (blocks: DocumentBlock[]) =>
  blocks.some((b) => b.type === 'section' && b.text === 'Costs');

describe('ItineraryDocument', () => {
  it('it opens with the trip and lists every booking once, under its day', () => {
    const blocks = buildItineraryDocument({ dossier });
    expect(blocks[0]!.type).toBe('title');
    expect(ofType(blocks, 'entry')).toHaveLength(bundle.segments.length);
    const days = ofType(blocks, 'day').map((d) => d.text);
    expect([...new Set(days)]).toEqual(days);
  });

  it('every booking reference is in it', () => {
    const text = ofType(buildItineraryDocument({ dossier }), 'entry')
      .flatMap((e) => (e.reference ? [e.reference] : []))
      .join(', ');
    for (const s of bundle.segments) if (s.confirmationCode) expect(text).toContain(s.confirmationCode);
  });

  it('a day carries its weather when there is some', () => {
    const first = segmentStart(
      [...bundle.segments].sort((a, b) => a.startEpochMillis - b.startEpochMillis)[0]!
    ).toPlainDate();
    const day: WeatherDay = {
      date: first,
      code: 2,
      highC: 21,
      lowC: 13,
      rainChance: 10,
      rainMm: null,
      basis: 'FORECAST'
    };
    const weather = new Map([[first.toString(), day]]);
    const firstDay = ofType(buildItineraryDocument({ dossier, weather }), 'day')[0]!;
    expect(firstDay.weather ?? '').toContain('Partly cloudy');
  });

  it('costs are included when anything was spent, with who pays whom', () => {
    const payer = bundle.travelers[0]!;
    const dinner = expense({
      id: 'e',
      tripId: bundle.trip.id,
      description: 'Dinner',
      amount: { minor: 12_000, currency: 'EUR' },
      paidBy: payer.id,
      spentOn: tripStartDate(bundle.trip)
    });
    const costs = summarizeCosts(
      [dinner],
      bundle.travelers.map((x) => x.id),
      'EUR',
      []
    );
    const blocks = buildItineraryDocument({ dossier, costs });
    expect(hasCostsSection(blocks)).toBe(true);
    expect(ofType(blocks, 'row').some((r) => r.label.endsWith(`pays ${displayName(payer)}`))).toBe(true);
  });

  it('nothing spent, no costs section', () => {
    const costs = summarizeCosts(
      [],
      bundle.travelers.map((x) => x.id),
      'EUR',
      []
    );
    expect(hasCostsSection(buildItineraryDocument({ dossier, costs }))).toBe(false);
  });

  it('the file is named for its date and trip', () => {
    expect(documentFileName(dossier)).toMatch(/^2026-05-\d\d-[a-z0-9-]+\.pdf$/);
  });
});
