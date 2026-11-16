import { describe, expect, it } from 'vitest';

import { effortWeeks, fitnessScale, predictionLabelDays, spreadDays } from './fitnessCharts';

describe('fitnessScale', () => {
  it('ends at the tightest round value over the line, in four to six steps', () => {
    // 106 × 1.05 = 111.3: 120 in six steps of 20, not 125 in five of 25.
    expect(fitnessScale([80, 106])).toEqual({ max: 120, ticks: [0, 20, 40, 60, 80, 100, 120] });
    expect(fitnessScale([30, 47]).max).toBe(50);
  });
  it('is never under 10', () => {
    expect(fitnessScale([0, 1]).max).toBeGreaterThanOrEqual(10);
    expect(fitnessScale([]).max).toBeGreaterThanOrEqual(10);
  });
});

describe('spreadDays', () => {
  it('spreads about five days, always the first and the last', () => {
    expect(spreadDays(31)).toEqual([0, 8, 15, 23, 30]);
    expect(spreadDays(3)).toEqual([0, 1, 2]);
    expect(spreadDays(0)).toEqual([]);
  });
});

describe('effortWeeks', () => {
  const week = (start: string, load?: number) => ({ start, totals: load == null ? {} : { load } });
  it('judges each week against the three before it', () => {
    const weeks = effortWeeks([
      week('2026-10-12', 250),
      week('2026-10-19', 250),
      week('2026-10-26', 268),
      week('2026-11-02', 400.4),
      week('2026-11-09'),
    ]);
    expect(weeks).toEqual([
      { monday: '2026-11-02', score: 400, range: { lo: 171, hi: 341 }, status: 'above' },
      { monday: '2026-11-09', score: 0, range: { lo: 204, hi: 408 }, status: 'below' },
    ]);
  });
  it('has no weeks without the three before', () => {
    expect(effortWeeks([week('2026-11-09', 10)])).toEqual([]);
  });
});

describe('predictionLabelDays', () => {
  it('labels Mondays over a month and the firsts of the month over longer ranges', () => {
    expect(predictionLabelDays('1m', '2026-10-17', '2026-11-16')).toEqual([
      '2026-10-19',
      '2026-10-26',
      '2026-11-02',
      '2026-11-09',
    ]);
    expect(predictionLabelDays('3m', '2026-08-25', '2026-11-16')).toEqual([
      '2026-09-01',
      '2026-10-01',
      '2026-11-01',
    ]);
  });
});
