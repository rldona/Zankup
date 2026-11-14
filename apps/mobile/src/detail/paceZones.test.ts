import { describe, expect, it } from 'vitest';

import { paceRange, paceZoneSeconds } from './paceZones';

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
});
