import { describe, expect, it } from 'vitest';

import { niceScale, tightScale } from './scale';

describe('round scales', () => {
  it('picks a round step in four intervals', () => {
    expect(niceScale([0, 100])).toEqual({ domain: [0, 100], ticks: [0, 25, 50, 75, 100] });
    expect(niceScale([0, 7.6]).ticks).toEqual([0, 2, 4, 6, 8]);
    expect(niceScale([47.3, 61.2]).ticks).toEqual([45, 50, 55, 60, 65]);
    expect(niceScale([-12, 30]).ticks).toEqual([-20, 0, 20, 40, 60]);
  });

  it('ends at the tightest round value, in four to six steps', () => {
    expect(tightScale([0, 5312]).ticks).toEqual([0, 1000, 2000, 3000, 4000, 5000, 6000]);
    expect(tightScale([0, 5312]).domain).toEqual([0, 6000]);
    expect(niceScale([0, 5312]).ticks.at(-1)).toBe(8000);
    // A tie keeps the fewer steps.
    expect(tightScale([0, 100]).ticks).toEqual([0, 25, 50, 75, 100]);
  });
});
