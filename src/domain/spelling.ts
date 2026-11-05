// A booking reference read aloud: to an agent on the phone, or across a desk
// where O and 0, I and 1 look the same in somebody else's font.

const ALPHABET: Record<string, string> = {
  A: 'Alfa',
  B: 'Bravo',
  C: 'Charlie',
  D: 'Delta',
  E: 'Echo',
  F: 'Foxtrot',
  G: 'Golf',
  H: 'Hotel',
  I: 'India',
  J: 'Juliett',
  K: 'Kilo',
  L: 'Lima',
  M: 'Mike',
  N: 'November',
  O: 'Oscar',
  P: 'Papa',
  Q: 'Quebec',
  R: 'Romeo',
  S: 'Sierra',
  T: 'Tango',
  U: 'Uniform',
  V: 'Victor',
  W: 'Whiskey',
  X: 'X-ray',
  Y: 'Yankee',
  Z: 'Zulu'
};

const DIGITS = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'];

export interface SpelledCharacter {
  character: string;
  /** "Kilo", "Seven", or null for punctuation and anything without a word. */
  word: string | null;
}

/** Each character of a reference, with the word that says it unmistakably. */
export function spellOut(reference: string): SpelledCharacter[] {
  return [...reference.trim()]
    .filter((c) => c !== ' ')
    .map((c) => {
      const upper = c.toUpperCase();
      const word = ALPHABET[upper] ?? (/[0-9]/.test(c) ? DIGITS[Number(c)]! : null);
      return { character: upper, word };
    });
}

/** "Kilo Seven Papa": the whole reference as one line, to read out. */
export function spokenReference(reference: string): string {
  return spellOut(reference)
    .map((s) => s.word ?? s.character)
    .join(' ');
}
