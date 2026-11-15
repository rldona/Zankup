import { describe, expect, it } from 'vitest';

import { elevationProfile, splitAt } from './splitsElevation';

describe('workout analysis chart', () => {
  it('averages the altitude along the distance, with no holes', () => {
    const dist = [0, 100, 200, 300, 400];
    const alt = [10, 20, 30, null, 50];
    expect(elevationProfile(dist, alt, 500, 5)).toEqual([10, 20, 30, 30, 50]);
    expect(elevationProfile(null, alt, 500)).toEqual([]);
  });

  it('finds the split under a point, the splits as wide as their distance', () => {
    const d = [1000, 1000, 500];
    expect(splitAt(0, d)).toBe(0);
    expect(splitAt(0.5, d)).toBe(1);
    expect(splitAt(0.9, d)).toBe(2);
    expect(splitAt(1, d)).toBe(2);
  });
});
