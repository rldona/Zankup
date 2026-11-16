import { describe, expect, it } from 'vitest';

import { paceRange } from '../progress';
import { elevationProfile, fastestSplits, paceZoneOf, paceZoneSeconds, splitAt, splitSpeeds } from './splits';

describe('pace zones', () => {
  // Threshold 3.3 m/s (5:03/km): bounds at 0, 78, 88, 95 and 100 % of it.
  const bounds = [0, 2.574, 2.904, 3.135, 3.3];
  it('adds each second to the zone its speed is in', () => {
    const speeds = [2.9, 2.95, 3.2, 3.4, 0.2, null];
    const times = [0, 1, 2, 3, 4, 5];
    expect(paceZoneSeconds(speeds, times, bounds)).toEqual([0, 1, 1, 1, 1]); // 2.9 is just under Z3
    expect(paceZoneSeconds([3.0, 3.0], [0, 60], bounds)).toEqual([0, 0, 0, 0, 0]); // a pause
  });
  it('writes the ranges as paces', () => {
    expect(paceRange(bounds, 0, 1000)).toBe('> 6:29');
    expect(paceRange(bounds, 2, 1000)).toBe('5:19 - 5:44');
    expect(paceRange(bounds, 4, 1000)).toBe('< 5:03');
  });
  it("finds a split's zone", () => {
    expect(paceZoneOf(3.0, bounds)).toBe(2);
    expect(paceZoneOf(4, bounds)).toBe(4);
    expect(paceZoneOf(0, bounds)).toBeNull();
    expect(paceZoneOf(3, [])).toBeNull();
  });
});

describe('training analysis chart', () => {
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

  it('marks the two fastest splits', () => {
    const speeds = splitSpeeds([
      { distance_m: 1000, time_s: 330 },
      { distance_m: 1000, time_s: 300 },
      { distance_m: 1000, time_s: null },
      { distance_m: 1000, time_s: 310 },
    ]);
    expect(speeds[2]).toBe(0);
    expect([...fastestSplits(speeds)].sort()).toEqual([1, 3]);
  });
});
