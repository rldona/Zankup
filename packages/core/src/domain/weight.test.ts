import { describe, expect, it } from 'vitest';

import { dailyWeights, parseWeight, weightChange, weightHistory, weightRangeStart } from './weight';

const weighings = [
  { day: '2026-11-01', weight_kg: 74 },
  { day: '2026-11-05', weight_kg: 72 },
  { day: '2026-11-06', weight_kg: 72.5 },
];

describe('weight', () => {
  it('joins the weighings day by day, with nothing outside them', () => {
    expect(dailyWeights(weighings, '2026-10-31', '2026-11-07')).toEqual([
      null,
      74,
      73.5,
      73,
      72.5,
      72,
      72.5,
      null,
    ]);
    expect(dailyWeights([], '2026-11-01', '2026-11-02')).toEqual([null, null]);
  });

  it('measures the change over a span', () => {
    expect(weightChange(weighings, '2026-11-01')).toBe(-1.5);
    expect(weightChange(weighings, '2026-11-06')).toBeNull(); // one weighing
  });

  it('starts all time at the first weighing', () => {
    expect(weightRangeStart('all', '2026-11-15', weighings)).toBe('2026-11-01');
    expect(weightRangeStart('all', '2026-11-15', [])).toBe('2026-11-15');
    expect(weightRangeStart('1m', '2026-11-15', weighings)).toBe('2026-10-16');
  });

  it('lists the history newest first with the step from the one before', () => {
    expect(weightHistory(weighings)).toEqual([
      { day: '2026-11-06', weight_kg: 72.5, step: 0.5 },
      { day: '2026-11-05', weight_kg: 72, step: -2 },
      { day: '2026-11-01', weight_kg: 74, step: null },
    ]);
  });

  it('reads a typed weight', () => {
    expect(parseWeight(' 72,5 ')).toBe(72.5);
    expect(parseWeight('72.456')).toBe(72.46);
    expect(parseWeight('5')).toBeNull();
    expect(parseWeight('abc')).toBeNull();
    expect(parseWeight('')).toBeNull();
  });
});
