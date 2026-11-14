import { describe, expect, it } from 'vitest';

import { months, monthsRange, monthSports } from './monthTotals';

const act = (sport: string, moving_s: number, distance_m = 0) => ({
  id: 1,
  sport,
  name: null,
  load: null,
  distance_m,
  moving_s,
  ascent_m: 10,
  start: '2026-11-01T08:00:00Z',
});

describe('monthly activities', () => {
  const today = new Date(2026, 10, 14); // 14 November
  const calendar = [
    { day: '2026-10-03', tsb: null, activities: [act('run', 3600, 10000)] },
    { day: '2026-10-30', tsb: null, activities: [act('ride', 7200, 50000)] },
    { day: '2026-11-02', tsb: null, activities: [act('ride', 1800, 20000), act('strength', 600)] },
    { day: '2026-11-20', tsb: null, activities: [act('ride', 999)] }, // after today: not counted yet
  ];

  it('asks the calendar for last month and this one', () => {
    expect(monthsRange(today)).toEqual({ from: '2026-10-01', to: '2026-12-01' });
  });

  it('adds up this month to today and the whole of last month, day by day', () => {
    const { current, previous } = months(calendar, today);
    expect(current.cumulative).toHaveLength(14);
    expect(current.cumulative[0]).toBe(0);
    expect(current.cumulative[13]).toBe(2400);
    expect(current.totals).toEqual({ count: 2, time: 2400, distance: 20000, ascent: 20 });
    expect(previous.cumulative).toHaveLength(31);
    expect(previous.totals.time).toBe(10800);
  });

  it('keeps to one sport and lists the sports by time', () => {
    expect(months(calendar, today, 'ride').current.totals.count).toBe(1);
    expect(months(calendar, today, 'run').current.totals.count).toBe(0);
    expect(monthSports(calendar)).toEqual(['ride', 'run', 'strength']);
  });
});
