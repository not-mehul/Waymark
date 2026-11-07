import { describe, expect, it } from 'vitest';
import { spellOut, spokenReference } from '../../src/domain/spelling';

describe('spelling a reference', () => {
  it('gives every letter and digit its word', () => {
    expect(spellOut('k7p')).toEqual([
      { character: 'K', word: 'Kilo' },
      { character: '7', word: 'Seven' },
      { character: 'P', word: 'Papa' }
    ]);
  });

  it('tells O from 0 and I from 1', () => {
    expect(spokenReference('O0I1')).toBe('Oscar Zero India One');
  });

  it('keeps punctuation as itself and drops spaces', () => {
    expect(spellOut('AB-1 2').map((s) => s.word)).toEqual(['Alfa', 'Bravo', null, 'One', 'Two']);
    expect(spokenReference('AB-1')).toBe('Alfa Bravo - One');
  });
});
