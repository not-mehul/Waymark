// A QR code as one SVG path, dark modules on light. The lowest error
// correction holds the most: a trip code is read off a screen, not a
// crumpled sticker.

import qrcode from 'qrcode-generator';

export interface QrDrawing {
  /** Modules per side, the quiet zone included. */
  size: number;
  /** One path of unit squares. */
  path: string;
}

const MARGIN = 2;

export function qrDrawing(text: string): QrDrawing {
  const code = qrcode(0, 'L');
  code.addData(text, 'Byte');
  code.make();
  const count = code.getModuleCount();
  let path = '';
  for (let row = 0; row < count; row++) {
    for (let col = 0; col < count; col++) {
      if (code.isDark(row, col)) path += `M${col + MARGIN} ${row + MARGIN}h1v1h-1z`;
    }
  }
  return { size: count + MARGIN * 2, path };
}
