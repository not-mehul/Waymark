import { describe, expect, it } from 'vitest';
import { buildSampleItinerary } from '../../src/catalog/sample-itinerary';
import { localDate } from '../../src/domain/time';
import {
  backupFileName,
  MAX_FILE_BYTES,
  readTripCode,
  readTripFile,
  readTripText,
  TRIP_CODE_PREFIX
} from '../../src/share/files';
import { archiveOf, encodeArchive, toCode } from '../../src/share/trip-archive';

const blob = (text: string, size = text.length) => ({ size, text: () => Promise.resolve(text) });

describe('reading a trip file', () => {
  it('finds the trips in a backup', async () => {
    const sample = buildSampleItinerary(localDate(2026, 11, 7));
    const incoming = await readTripFile(blob(encodeArchive(archiveOf([{ ...sample, costs: null }]))));
    expect(incoming.kind).toBe('trips');
    if (incoming.kind === 'trips') expect(incoming.bundles.map((b) => b.trip.id)).toEqual([sample.trip.id]);
  });

  it('turns away a file too large to be a trip, without reading it', async () => {
    const incoming = await readTripFile({
      size: MAX_FILE_BYTES + 1,
      text: () => Promise.reject(new Error())
    });
    expect(incoming).toMatchObject({ kind: 'problem', title: 'That file could not be opened' });
  });

  it('says when a file has no trips in it', () => {
    const empty = encodeArchive(archiveOf([]));
    expect(readTripText(empty)).toMatchObject({ kind: 'problem', title: 'Nothing to open' });
  });

  it('says why something else is not a trip', () => {
    expect(readTripText('{"hello": 1}')).toMatchObject({
      kind: 'problem',
      title: 'That is not a trip Waymark can open'
    });
    expect(readTripText('not json')).toMatchObject({ detail: 'This is not a Waymark file.' });
  });
});

it('names a backup by the day it was made', () => {
  expect(backupFileName(localDate(2026, 11, 1))).toBe('waymark-backup-2026-11-01.json');
});

describe('reading a scanned trip code', () => {
  const sample = buildSampleItinerary(localDate(2026, 11, 7));
  const code = toCode(archiveOf([{ ...sample, costs: null }]))!;

  it('opens the trip from the link a code holds', () => {
    const incoming = readTripCode(TRIP_CODE_PREFIX + code.payload);
    expect(incoming.kind).toBe('trips');
    if (incoming.kind === 'trips') expect(incoming.bundles[0]!.trip.id).toBe(sample.trip.id);
  });

  it('opens the bare payload too', () => {
    expect(readTripCode(code.payload).kind).toBe('trips');
  });

  it('explains a code that is not a trip', () => {
    expect(readTripCode('M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100')).toMatchObject({
      kind: 'problem'
    });
  });
});
