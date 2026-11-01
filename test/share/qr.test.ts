import { describe, expect, it } from 'vitest';
import { qrDrawing } from '../../src/share/qr';
import { MAX_CODE_CHARS } from '../../src/share/trip-archive';

describe('qrDrawing', () => {
  it('draws a small code with a quiet zone', () => {
    const drawing = qrDrawing('waymark://import?d=abc');
    // 22 bytes is more than version 1 holds at the lowest correction: version 2, 25 modules.
    expect(drawing.size).toBe(25 + 4);
    expect(drawing.path.startsWith('M')).toBe(true);
  });

  it('holds the largest trip code there is', () => {
    const payload = 'waymark://import?d=' + 'A'.repeat(MAX_CODE_CHARS);
    expect(qrDrawing(payload).size).toBeLessThanOrEqual(177 + 4);
  });
});
