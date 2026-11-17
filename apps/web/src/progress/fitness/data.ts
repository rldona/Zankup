/**
 * What Fitness, Relative effort and race predictions read (OS-1003): the queries behind their cards and
 * pages, the range Fitness remembers in this browser, the effort statuses' colours and a few dates. The
 * working-out is shared with the app in `packages/core` (progress/).
 */
import {
  addDays,
  calendarQuery,
  commonTokens,
  effortWeeks,
  estimatesQuery,
  FITNESS_RANGES,
  isoDay,
  SPORTS,
  themes,
  volumeQuery,
  weekAt,
  weekDays,
  type CalendarDay,
  type EffortStatus,
  type FitnessRange,
  type Point,
  type Sport,
  type ThemeName,
} from '@zankup/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { api } from '../../api';
import { locale } from '../../i18n';

/**
 * Every day's activities from `from` up to (not including) `to`, fetched once for the whole range and
 * read by day: the pointer moving over a chart picks a day or week from it, instead of asking the
 * server for each one it passes. Another range keeps the last one on screen while it loads.
 */
export function useCalendarDays(from: string, to: string) {
  const calendar = useQuery({
    ...calendarQuery(api, { from, to }),
    placeholderData: keepPreviousData,
  });
  const days = useMemo(
    () => new Map(((calendar.data ?? []) as CalendarDay[]).map((d) => [d.day, d.activities])),
    [calendar.data],
  );
  return { days, pending: calendar.isPending };
}

export type CalendarActivities = CalendarDay['activities'];

export const palette = (theme: ThemeName) => ({ ...commonTokens, ...themes[theme] });

export const asSport = (value: string): Sport =>
  (SPORTS as readonly string[]).includes(value) ? (value as Sport) : 'other';

export const at = (iso: string) => new Date(`${iso}T12:00:00`);

/** 16 Nov, or 16 Nov 2026. */
export const shortDate = (iso: string, withYear = false) =>
  at(iso).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'short',
    ...(withYear ? { year: 'numeric' } : {}),
  });

export const monthName = (iso: string) => at(iso).toLocaleDateString(locale, { month: 'short' });

/** A day's initial, upper case: L M X J V S D. */
export const dayLetter = (iso: string) =>
  at(iso).toLocaleDateString(locale, { weekday: 'narrow' }).toUpperCase();

/** Today, fixed for the screen's life (a page left open over midnight keeps its day). */
export function useToday() {
  const [today] = useState(() => isoDay(new Date()));
  return today;
}

// --- Fitness's range, remembered in this browser ---------------------------------------------------

const RANGE_KEY = 'zankup-fitness-range';

export function savedFitnessRange(): FitnessRange {
  try {
    const saved = localStorage.getItem(RANGE_KEY);
    return FITNESS_RANGES.includes(saved as FitnessRange) ? (saved as FitnessRange) : '1m';
  } catch {
    return '1m';
  }
}

export function saveFitnessRange(range: FitnessRange) {
  try {
    localStorage.setItem(RANGE_KEY, range);
  } catch {
    // Storage blocked: the range holds for this visit.
  }
}

// --- Relative effort --------------------------------------------------------------------------------

type Bucket = { start: string; totals: { load?: number | null } };

/** The last `count` weeks (oldest first), each judged against the three before it. */
export function useEffortWeeks(count: number) {
  const today = useToday();
  const volume = useQuery(
    volumeQuery(api, { from: weekAt(count + 2, today), to: addDays(today, 1) }, 'week'),
  );
  const weeks = useMemo(() => effortWeeks((volume.data ?? []) as Bucket[]), [volume.data]);
  return { weeks, isPending: volume.isPending };
}

/** Each day's effort of this week and the last (Monday first). */
export function useEffortDays() {
  const today = useToday();
  const from = weekAt(1, today);
  const volume = useQuery(volumeQuery(api, { from, to: addDays(weekAt(0, today), 7) }, 'day'));
  const weeks = useMemo(() => {
    const byDay = new Map(((volume.data ?? []) as Bucket[]).map((b) => [b.start, b.totals.load ?? 0]));
    const week = (monday: string) => weekDays(monday).map((d) => byDay.get(d) ?? 0);
    return { thisWeek: week(weekAt(0, today)), lastWeek: week(from) };
  }, [volume.data, today, from]);
  return { ...weeks, isPending: volume.isPending };
}

/** A status's colour for a chart: blue under the range, the accent in it, orange and red over. */
export function statusPaint(theme: ThemeName, status: EffortStatus): string {
  const c = palette(theme);
  if (status === 'in') return c.accent;
  return status === 'below' ? c.fitness : status === 'above' ? c.zone4 : c.zone5;
}

// --- Race predictions -------------------------------------------------------------------------------

/** Each race's history (day and predicted seconds), oldest first. */
export function usePredictions() {
  const estimates = useQuery(estimatesQuery(api));
  const byKind = useMemo(
    () =>
      new Map<string, Point[]>(
        (estimates.data ?? []).map((e) => [e.kind, e.history.map((h) => ({ day: h.day, value: h.value }))]),
      ),
    [estimates.data],
  );
  return { byKind, isPending: estimates.isPending };
}

/** The race badge's scalloped edge, as Strava's: 24 points in and out around the middle. */
export function scallop(size: number): string {
  const r = size / 2;
  const points = 24;
  return (
    Array.from({ length: points }, (_, i) => {
      const a = (i / points) * Math.PI * 2 - Math.PI / 2;
      const radius = i % 2 ? r * 0.82 : r * 0.94;
      return `${i ? 'L' : 'M'}${(r + radius * Math.cos(a)).toFixed(2)} ${(r + radius * Math.sin(a)).toFixed(2)}`;
    }).join('') + 'Z'
  );
}
