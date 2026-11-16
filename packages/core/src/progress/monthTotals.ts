/**
 * Monthly activities (as Strava's): this month's and last month's totals, each as a line that adds up
 * day by day, for all sports or one. Pure, so it is unit-tested.
 */
import { type CalendarDay } from '../api/queries';

import { isoDay } from '../domain/home';

export type MonthFigures = { count: number; time: number; distance: number; ascent: number };

export type Month = {
  /** First day of the month. */
  start: Date;
  /** Days in the month. */
  days: number;
  totals: MonthFigures;
  /** Moving time added up to the end of each day, up to today for the month in course. */
  cumulative: number[];
};

const empty = (): MonthFigures => ({ count: 0, time: 0, distance: 0, ascent: 0 });

/** The calendar's range for this month and the one before: [from, to). */
export function monthPairRange(today: Date): { from: string; to: string } {
  return {
    from: isoDay(new Date(today.getFullYear(), today.getMonth() - 1, 1)),
    to: isoDay(new Date(today.getFullYear(), today.getMonth() + 1, 1)),
  };
}

/** One month from the calendar's days, all sports or only `sport`; `today` cuts the month in course. */
export function monthOf(calendar: CalendarDay[], start: Date, today: Date, sport?: string): Month {
  const days = new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate();
  const byDay = new Map(calendar.map((d) => [d.day, d]));
  const totals = empty();
  const cumulative: number[] = [];
  const last =
    start.getFullYear() === today.getFullYear() && start.getMonth() === today.getMonth()
      ? today.getDate()
      : days;
  for (let i = 1; i <= last; i++) {
    const day = byDay.get(isoDay(new Date(start.getFullYear(), start.getMonth(), i)));
    for (const a of day?.activities ?? []) {
      if (sport && a.sport !== sport) continue;
      totals.count += 1;
      totals.time += a.moving_s;
      totals.distance += a.distance_m ?? 0;
      totals.ascent += a.ascent_m ?? 0;
    }
    cumulative.push(totals.time);
  }
  return { start, days, totals, cumulative };
}

/** This month and the last, from the calendar of both. */
export function months(calendar: CalendarDay[], today: Date, sport?: string) {
  return {
    current: monthOf(calendar, new Date(today.getFullYear(), today.getMonth(), 1), today, sport),
    previous: monthOf(calendar, new Date(today.getFullYear(), today.getMonth() - 1, 1), today, sport),
  };
}

/** The sports done in either month, the most time first. */
export function monthSports(calendar: CalendarDay[]): string[] {
  const time = new Map<string, number>();
  for (const d of calendar)
    for (const a of d.activities) time.set(a.sport, (time.get(a.sport) ?? 0) + a.moving_s);
  return [...time.entries()].sort((x, y) => y[1] - x[1]).map(([s]) => s);
}
