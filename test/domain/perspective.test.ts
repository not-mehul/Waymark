import { describe, expect, it } from 'vitest';
import { distanceInPerspective } from '../../src/domain/analytics';

describe('distance in perspective', () => {
  it('measures a long trip against the Earth', () => {
    expect(distanceInPerspective(40_000)).toBe('As far as going round the Earth once.');
    expect(distanceInPerspective(60_000)).toBe('As far as going round the Earth 1.5 times.');
    expect(distanceInPerspective(18_000)).toBe('45% of the way round the Earth.');
  });

  it('measures a short one in marathons', () => {
    expect(distanceInPerspective(422)).toBe('About 10 marathons, end to end.');
  });

  it('says nothing about a trip that barely moved', () => {
    expect(distanceInPerspective(0)).toBeNull();
    expect(distanceInPerspective(50)).toBeNull();
  });
});
