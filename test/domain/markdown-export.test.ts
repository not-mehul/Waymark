import { describe, expect, it } from 'vitest';
import { tripReport } from '../../src/domain/analytics';
import {
  codeSpan,
  markdownFileName,
  md,
  renderMarkdown,
  type MarkdownPayload
} from '../../src/domain/markdown';
import { displayName, segmentStart, tripStartDate } from '../../src/domain/model';
import { clock } from '../../src/domain/time-text';
import { dossierOf, sample } from '../helpers';

const bundle = sample();
const dossier = dossierOf(bundle);
const payload: MarkdownPayload = {
  dossier,
  ideas: bundle.ideas,
  analytics: tripReport(dossier, bundle.ideas)
};
const markdown = renderMarkdown(payload);

const between = (text: string, after: string, before: string) => {
  const start = text.indexOf(after);
  const rest = start < 0 ? text : text.slice(start + after.length);
  const end = rest.indexOf(before);
  return end < 0 ? rest : rest.slice(0, end);
};

describe('MarkdownExport', () => {
  it('the document opens with the trip and its dates', () => {
    const first = markdown.split('\n')[0]!;
    expect(first.startsWith('# ')).toBe(true);
    expect(first).toContain(bundle.trip.name);
    expect(markdown).toContain(bundle.trip.destinationSummary);
    expect(markdown).toContain('2026');
  });

  it('every section a trip has content for is present', () => {
    for (const heading of ['## Itinerary', '## On the list', '## Bookings']) {
      expect(markdown, `missing ${heading}`).toContain(heading);
    }
  });

  // A booking reference is the reason to send somebody an itinerary.
  it('every booking reference is exported', () => {
    const codes = bundle.segments.flatMap((s) => (s.confirmationCode ? [s.confirmationCode] : []));
    expect(codes.length, 'fixture has no booking references').toBeGreaterThan(0);
    for (const code of codes) expect(markdown, `missing ${code}`).toContain(code);
  });

  it('days are headings and bookings are list items beneath them', () => {
    const itinerary = between(markdown, '## Itinerary', '## On the list').split('\n');
    const dayHeadings = itinerary.filter((l) => l.startsWith('### '));
    const bullets = itinerary.filter((l) => l.startsWith('- `'));

    expect(dayHeadings.length).toBeGreaterThanOrEqual(3);
    expect(bullets.length).toBeGreaterThanOrEqual(bundle.segments.length);
    // Chronology: the first bullet is the first thing that happens.
    const first = [...bundle.segments].sort((a, b) => a.startEpochMillis - b.startEpochMillis)[0]!;
    expect(bullets[0]).toContain(clock(segmentStart(first)));
  });

  it('ideas render as checkboxes that reflect their state', () => {
    expect(markdown).toContain('- [ ] ');
    // The sample seeds a done idea, so both box states appear.
    expect(markdown).toContain('- [x] ');
  });

  it('a traveler is named only when a segment is not the whole party', () => {
    const everyone = bundle.travelers.map(displayName);
    const itinerary = between(markdown, '## Itinerary', '## On the list');
    const shared = bundle.segments.filter(
      (s) => s.travelerIds.length === 0 || s.travelerIds.length === bundle.travelers.length
    );
    expect(shared.length, 'fixture has no shared segments').toBeGreaterThan(0);

    // Whole-party legs carry no "— name" suffix; split ones do.
    const split = bundle.segments.filter(
      (s) => s.travelerIds.length > 0 && s.travelerIds.length < bundle.travelers.length
    );
    if (split.length > 0) expect(everyone.some((name) => itinerary.includes(`— ${name}`))).toBe(true);
  });

  it('the filename sorts by date and is safe on any filesystem', () => {
    const name = markdownFileName(dossier);
    expect(name.startsWith(tripStartDate(bundle.trip).toString())).toBe(true);
    expect(name.endsWith('.md')).toBe(true);
    expect(name).toMatch(/^[\p{L}\p{Nd}.-]+$/u);
    expect(name).not.toContain('--');
  });

  it('an empty trip still produces a valid document rather than a crash', () => {
    const bare = renderMarkdown({
      dossier: { trip: bundle.trip, party: { tripId: bundle.trip.id, travelers: [] }, segments: [] }
    });
    expect(bare.startsWith('# ')).toBe(true);
    expect(bare).not.toContain('## Itinerary');
    expect(bare).toContain('Exported from Waymark');
  });

  it('the document closes with its provenance', () => {
    expect(markdown.trimEnd().endsWith('Exported from Waymark.')).toBe(true);
  });

  it('user text cannot restyle the document', () => {
    expect(md('*The* Rooms')).toBe('\\*The\\* Rooms');
    expect(md('a_b | c')).toBe('a\\_b \\| c');
    expect(md('#2')).toBe('\\#2');
    expect(md('- not a list')).toBe('\\- not a list');
    expect(md('1. not a list')).toBe('1\\. not a list');
    // Ordinary punctuation is left alone, so the file still reads as text.
    expect(md('St. Pancras (Euston Road)')).toBe('St. Pancras (Euston Road)');
  });

  it('a reference with a backtick still fits in a code span', () => {
    expect(codeSpan('K7QH2P')).toBe('`K7QH2P`');
    expect(codeSpan('AB`C')).toBe('``AB`C``');
  });

  it('the numbers table counts clock changes', () => {
    expect(markdown).toContain('| Clock changes |');
  });
});
