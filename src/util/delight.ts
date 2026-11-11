// Waymark's moments of delight and its easter eggs (style.md §11.3). The
// animations come from dusk-dawn and all of them respect reduced motion; the
// toast always says what happened, so nothing is only visual.

import { celebrate, createEggs, prefersReducedMotion, type CelebrateOptions } from 'dusk-dawn/expressive';
import { ui } from '../state/ui.svelte';
import { icons } from './icons';

export const EGGS = ['konami', 'title', 'fogg'] as const;
export type Egg = (typeof EGGS)[number];

const eggs = createEggs('waymark', EGGS);

/** Departure-board confetti: markers, diamonds and the odd compass point. */
export const DEPARTURES: CelebrateOptions = {
  shapes: ['diamond', 'triangle', 'circle', 'cookie-4', 'gem'],
  colours: ['--accent-bright', '--accent-amber', '--accent-sage', '--map-route'],
  glyphs: ['✦', 'N', 'E', 'S', 'W']
};

/** An easter egg hatches: its toast, with the count the first time it's found. */
export function hatch(id: Egg, message: string): void {
  const { isNew, found, total } = eggs.find(id);
  ui.notify(isNew ? `${message} Easter egg found: ${found} of ${total}.` : message, 'celebrate');
}

export function eggsFound(): number {
  return eggs.found().length;
}

/** A real accomplishment: confetti, from where it happened. */
export function cheer(origin?: Element | null, options: CelebrateOptions = {}): void {
  void celebrate({ ...DEPARTURES, origin: origin ?? undefined, ...options });
}

/**
 * A paper plane loops across the screen and away. Purely decorative: it sits
 * in an aria-hidden layer that takes no pointer events, and it doesn't fly
 * under reduced motion.
 */
export function flyBy(): Promise<void> {
  if (prefersReducedMotion() || typeof document === 'undefined') return Promise.resolve();
  const plane = document.createElement('div');
  plane.className = 'fly-by';
  plane.setAttribute('aria-hidden', 'true');
  plane.innerHTML = icons.plane();
  document.body.append(plane);
  const w = innerWidth;
  const h = innerHeight;
  const frames: Keyframe[] = [];
  const steps = 24;
  for (let i = 0; i <= steps; i++) {
    const p = i / steps;
    const x = -60 + (w + 120) * p;
    // One loop-the-loop in the middle of the flight.
    const loop = Math.max(0, Math.min(1, (p - 0.4) / 0.25));
    const y = h * 0.62 - h * 0.3 * p - Math.sin(loop * Math.PI * 2) * 70;
    const angle = -20 - loop * 360;
    frames.push({ transform: `translate(${x}px, ${y}px) rotate(${angle + 45}deg)` });
  }
  return plane
    .animate(frames, { duration: 2400, easing: 'ease-in-out', fill: 'forwards' })
    .finished.then(() => plane.remove())
    .catch(() => plane.remove());
}
