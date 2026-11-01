// The text inside a barcode in a picture: a screenshot of a mobile boarding
// pass, a photo of a printed one, or a trip code. Read on the phone, with no
// camera permission and nothing sent anywhere.
//
// In the Android app the shell reads it with ZXing, as the Kotlin app did. In
// a browser that has one, the Shape Detection API's BarcodeDetector does.

import { callNative, isNativeShell } from '../platform/native';

export type CodeReading =
  | { kind: 'read'; text: string }
  /** Nothing in the picture could be read as a code. */
  | { kind: 'none' }
  /** Neither the shell nor this browser can read codes. */
  | { kind: 'unsupported' };

/** The formats a boarding pass or a trip code is printed in. */
export const CODE_FORMATS = ['pdf417', 'aztec', 'qr_code', 'data_matrix'] as const;

interface DetectedCode {
  rawValue: string;
}
interface Detector {
  detect: (image: ImageBitmapSource) => Promise<DetectedCode[]>;
}
interface DetectorClass {
  new (options: { formats: string[] }): Detector;
  getSupportedFormats?: () => Promise<string[]>;
}

/** The browser's detector, limited to the formats it can read; null without one. */
async function browserDetector(): Promise<Detector | null> {
  const Detector = (globalThis as { BarcodeDetector?: DetectorClass }).BarcodeDetector;
  if (!Detector) return null;
  const supported = (await Detector.getSupportedFormats?.()) ?? [...CODE_FORMATS];
  const formats = CODE_FORMATS.filter((f) => supported.includes(f));
  return formats.length > 0 ? new Detector({ formats }) : null;
}

/** A photo is shrunk before it crosses to the shell: the code reads as well, and a 48 MP picture would not fit. */
const MAX_SIDE = 2048;

async function shrunk(file: Blob): Promise<Blob> {
  if (typeof createImageBitmap !== 'function' || typeof OffscreenCanvas !== 'function') return file;
  const bitmap = await createImageBitmap(file);
  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size < 4_000_000) return file;
    const canvas = new OffscreenCanvas(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale));
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await canvas.convertToBlob({ type: 'image/png' });
  } finally {
    bitmap.close();
  }
}

function base64Of(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ''));
    reader.onerror = () => reject(reader.error ?? new Error('Could not read the picture'));
    reader.readAsDataURL(file);
  });
}

/** Read the first code in a picture. */
export async function readCode(file: Blob): Promise<CodeReading> {
  if (isNativeShell()) {
    try {
      const text = await callNative<string | null>('readCode', { image: await base64Of(await shrunk(file)) });
      return text ? { kind: 'read', text } : { kind: 'none' };
    } catch (err) {
      // The shell could not read the picture at all: try the WebView's own detector.
      console.warn('The app could not read the code', err);
    }
  }
  const detector = await browserDetector();
  if (!detector) return { kind: 'unsupported' };
  let bitmap: ImageBitmap | null = null;
  try {
    bitmap = await createImageBitmap(file);
    const found = await detector.detect(bitmap);
    const text = found.find((c) => c.rawValue.length > 0)?.rawValue;
    return text ? { kind: 'read', text } : { kind: 'none' };
  } catch {
    return { kind: 'none' };
  } finally {
    bitmap?.close();
  }
}
