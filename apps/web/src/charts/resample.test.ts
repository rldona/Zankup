import { describe, expect, it } from 'vitest';

import { resample } from './resample';

describe('resample', () => {
  it('draws every range through the same number of places, from the first value to the last', () => {
    for (const values of [[1, 2, 3], Array.from({ length: 365 }, (_, i) => i)]) {
      const line = resample(values, { samples: 50 });
      expect(line).toHaveLength(50);
      expect(line[0]).toEqual([0, values[0]]);
      expect(line.at(-1)).toEqual([values.length - 1, values.at(-1)]);
    }
  });

  it('passes through the values and goes straight between them', () => {
    const line = resample([0, 10, 4], { samples: 5 });
    expect(line).toEqual([
      [0, 0],
      [0.5, 5],
      [1, 10],
      [1.5, 7],
      [2, 4],
    ]);
  });

  it('bridges gaps, and leaves no line before the first value or after the last', () => {
    const line = resample([null, 2, null, 6, null], { samples: 9 });
    expect(line.map(([, v]) => v)).toEqual([null, null, 2, 3, 4, 5, 6, null, null]);
  });

  it('a smooth line never overshoots the values it passes through', () => {
    const line = resample([0, 10, 10, 0], { samples: 61, smooth: true });
    for (const [, v] of line) {
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThanOrEqual(10);
    }
  });
});
