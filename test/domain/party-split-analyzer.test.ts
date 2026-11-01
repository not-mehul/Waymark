import { describe, expect, it } from 'vitest';
import { coverage, partyWarnings, splitWindows } from '../../src/domain/party-split';
import { dossierOf, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);

describe('PartySplitAnalyzer', () => {
  it('finds the day the party travels separately', () => {
    const windows = splitWindows(dossier);
    expect(windows.length, 'expected at least one split').toBeGreaterThan(0);
    const trainSplit = windows.find((w) => w.groups.some((g) => g.segmentId === 'seg-eurostar-julian'));
    expect(trainSplit, 'the morning Eurostar should open a split').toBeDefined();
  });

  it('a solo traveler never splits', () => {
    const solo = { ...dossier, party: { tripId: dossier.trip.id, travelers: [bundle.travelers[0]!] } };
    expect(splitWindows(solo)).toEqual([]);
  });

  it('windows are merged rather than repeated per boundary', () => {
    const windows = splitWindows(dossier);
    for (let i = 1; i < windows.length; i++) {
      expect(windows[i - 1]!.endMillis).toBeLessThanOrEqual(windows[i]!.startMillis);
    }
  });

  it('coverage counts what each traveler is actually on', () => {
    const byId = new Map(coverage(dossier).map((c) => [c.traveler.id, c]));
    const mara = byId.get('trav-mara')!;
    const julian = byId.get('trav-julian')!;

    expect(mara.hasLodging && julian.hasLodging).toBe(true);
    expect(mara.flightCount).toBe(2);
    expect(julian.flightCount).toBe(2);
    expect(mara.missingFromSegments.some((s) => s.id === 'seg-eurostar-julian')).toBe(true);
    expect(julian.missingFromSegments.some((s) => s.id === 'seg-eurostar-mara')).toBe(true);
  });

  it('a traveler with nothing booked is called out', () => {
    const stranger = { ...bundle.travelers[0]!, id: 'trav-stranger', fullName: 'Nadia Roth', nickname: null };
    const withStranger = {
      ...dossier,
      party: { tripId: dossier.trip.id, travelers: [...bundle.travelers, stranger] }
    };
    expect(partyWarnings(withStranger).some((n) => n.includes('Nadia'))).toBe(true);
  });
});
