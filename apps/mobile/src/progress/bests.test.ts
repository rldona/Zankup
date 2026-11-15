import { describe, expect, it } from 'vitest';

import {
  bestsBetween,
  bestsIn,
  bestValue,
  monthStarts,
  placeOf,
  ranked,
  trend,
  windowOf,
  windowShare,
  yearBefore,
  yearMedal,
  yearsOf,
} from './bests';

const b = (day: string, value: number, id = Math.round(value)) => ({ activity_id: id, day, value });

describe('best times', () => {
  const runs = [b('2019-08-09', 67), b('2025-11-10', 118), b('2026-08-21', 114), b('2026-10-17', 117)];
  it('ranks faster first for runs, more for power', () => {
    expect(ranked('400m', runs).map((x) => x.value)).toEqual([67, 114, 117, 118]);
    expect(ranked('power_30', [b('2026-01-01', 500), b('2026-02-01', 551)])[0].value).toBe(551);
    expect(placeOf('400m', runs, runs[2])).toBe(2);
  });
  it('gives the medal of the year', () => {
    expect(yearMedal('400m', runs, runs[2])).toBe(1);
    expect(yearMedal('400m', runs, runs[3])).toBe(2);
    expect(yearMedal('400m', runs, runs[0])).toBe(1);
  });
  it('lists the years and a year or the last twelve months, newest first', () => {
    expect(yearsOf(runs)).toEqual(['2026', '2025', '2019']);
    expect(bestsIn(runs, null, '2026-11-14').map((x) => x.day)).toEqual(['2026-10-17', '2026-08-21']);
    expect(bestsIn(runs, null, '2026-11-09')).toHaveLength(3); // 10 Nov 2025 is within twelve months
    expect(bestsIn(runs, '2019', '2026-11-14')).toHaveLength(1);
  });
  it('writes a measure as Strava does', () => {
    expect(bestValue('5k', 26 * 60 + 45, 'es')).toBe('26:45');
    expect(bestValue('marathon', 4 * 3600 + 6 * 60 + 10, 'es')).toBe('4:06:10');
    expect(bestValue('power_30', 551.4, 'es')).toBe('551 W');
    expect(bestValue('longest', 102200, 'es')).toBe('102,2 km');
  });
  it('shows twelve months at a time, a year back per swipe', () => {
    expect(yearBefore('2024-02-29')).toBe('2023-02-28');
    expect(windowOf('2026-11-15')).toEqual({ from: '2025-11-15', to: '2026-11-15' });
    const b = (day: string, value: number) => ({ activity_id: value, day, value });
    const all = [b('2025-11-15', 1), b('2025-11-16', 2), b('2026-11-15', 3), b('2026-11-16', 4)];
    expect(bestsBetween(all, '2025-11-15', '2026-11-15').map((x) => x.value)).toEqual([2, 3]);
    expect(windowShare('2026-05-15', '2025-11-15', '2026-11-15')).toBeCloseTo(0.5, 1);
    expect(monthStarts('2025-11-15', '2026-02-10')).toEqual(['2025-12-01', '2026-01-01', '2026-02-01']);
  });
  it('draws the trend as the average of the bests near each one', () => {
    const b = (day: string, value: number) => ({ activity_id: value, day, value });
    const points = [b('2026-01-01', 100), b('2026-01-10', 200), b('2026-06-01', 300)];
    expect(trend(points, 30)).toEqual([150, 150, 300]);
  });
});
