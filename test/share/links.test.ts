// Tests for the shared link module (style.md §14.1). Identical in all five apps
// apart from the import path.
import { describe, expect, it } from 'vitest';
import { buildLink, dateParam, fractionParam, isKnownAction, parseLink } from '../../src/platform/links';

describe('links between the apps', () => {
  it('builds a link with the sender and without empty values', () => {
    expect(
      buildLink(
        'bookshelf',
        'log',
        { title: 'Dune', author: 'Frank Herbert', isbn: '', finished: null },
        'rosetta'
      )
    ).toBe('bookshelf://log?title=Dune&author=Frank+Herbert&source=rosetta');
  });

  it('reads back what it builds', () => {
    const url = buildLink(
      'ledger',
      'add',
      { city: 'São Paulo', country: 'BR', date: '2026-10-02' },
      'waymark'
    );
    expect(parseLink(url)).toEqual({
      app: 'ledger',
      action: 'add',
      params: { city: 'São Paulo', country: 'BR', date: '2026-10-02' },
      source: 'waymark'
    });
  });

  it('treats a bare scheme as open', () => {
    expect(parseLink('colophon://')).toMatchObject({
      app: 'colophon',
      action: 'open',
      params: {},
      source: null
    });
    expect(parseLink('ROSETTA://Open/?id=abc')).toMatchObject({
      app: 'rosetta',
      action: 'open',
      params: { id: 'abc' }
    });
  });

  it('accepts only links to the named app when asked', () => {
    expect(parseLink('bookshelf://open', 'ledger')).toBeNull();
    expect(parseLink('ledger://open', 'ledger')).not.toBeNull();
  });

  it('ignores other schemes and malformed links', () => {
    expect(parseLink('https://example.com/')).toBeNull();
    expect(parseLink('content://media/1')).toBeNull();
    expect(parseLink('bookshelf:log')).toBeNull();
    expect(parseLink(`bookshelf://log?title=${'x'.repeat(9000)}`)).toBeNull();
  });

  it('keeps unknown actions so the receiver can fall back', () => {
    const link = parseLink('waymark://teleport?to=moon')!;
    expect(link.action).toBe('teleport');
    expect(isKnownAction(link)).toBe(false);
    expect(isKnownAction(parseLink('waymark://import?d=abc')!)).toBe(true);
    expect(isKnownAction(parseLink('waymark://constructor')!)).toBe(false);
  });

  it('checks dates and fractions', () => {
    expect(dateParam('2026-02-28')).toBe('2026-02-28');
    expect(dateParam('2026-02-30')).toBeNull();
    expect(dateParam('yesterday')).toBeNull();
    expect(fractionParam('0.42')).toBe(0.42);
    expect(fractionParam('1.5')).toBeNull();
    expect(fractionParam(undefined)).toBeNull();
  });
});
