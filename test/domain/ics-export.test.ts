import { describe, expect, it } from 'vitest';
import { fold, icsText, renderIcs } from '../../src/domain/ics';
import { dossierOf, sample } from '../helpers';

const bundle = sample();
const ics = renderIcs(dossierOf(bundle), 0);
const octets = (line: string) => new TextEncoder().encode(line).length;
const count = (text: string, needle: string) => text.split(needle).length - 1;

describe('IcsExport', () => {
  it('one event per booking, wrapped in a calendar', () => {
    expect(ics.startsWith('BEGIN:VCALENDAR\r\n')).toBe(true);
    expect(ics.endsWith('END:VCALENDAR\r\n')).toBe(true);
    expect(count(ics, 'BEGIN:VEVENT')).toBe(bundle.segments.length);
    expect(count(ics, 'END:VEVENT')).toBe(bundle.segments.length);
  });

  it('times are in UTC and match the booking to the second', () => {
    const outbound = bundle.segments.find((s) => s.id === 'seg-outbound')!;
    // 16:20 PDT on 14 May is 23:20 UTC.
    expect(ics).toContain('DTSTART:20260514T232000Z');
    expect(ics).toContain(`UID:${outbound.id}@waymark`);
  });

  it('references travel with the events', () => {
    expect(ics.replaceAll('\r\n ', '')).toContain('Reference: K7QH2P (British Airways)');
  });

  it('text is escaped and long lines are folded at 75 octets', () => {
    expect(icsText('a;b,c\\d\ne')).toBe('a\\;b\\,c\\\\d\\ne');
    const long = 'DESCRIPTION:' + 'é'.repeat(60);
    const folded = fold(long);
    for (const line of folded.split('\r\n')) expect(octets(line)).toBeLessThanOrEqual(75);
    expect(folded.replaceAll('\r\n ', '')).toBe(long);
    for (const line of ics.split('\r\n')) expect(octets(line), line).toBeLessThanOrEqual(75);
  });
});
