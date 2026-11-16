/**
 * How Volume and the Training log write what they show (as Strava's Progress), shared by the app and
 * the web: totals and axis labels in the person's units, week titles, the words under a chart, the
 * sentence on how a range went and the months back to the first activity. Pure, so it is unit-tested.
 */
import type { MessageKey } from '../i18n';
import { axisTicks, hoursMinutes, percentChange, type ProgressRange, type VolumeMetric } from './volume';
import { addDays } from './weeks';

const MILE = 1609.344;
const FOOT = 3.28084;

const at = (iso: string) => new Date(`${iso}T12:00:00`);

/** Totals, axis labels and ticks written for the person's units and language. */
export function volumeFormat(imperial: boolean, locale: string) {
  const distanceUnit = imperial ? MILE : 1000;
  const n = (v: number, digits = 0) =>
    v.toLocaleString(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits });
  return {
    imperial,
    /** A total: 4h 17min · 28,97 km · 1.421 m · 22. */
    value(metric: VolumeMetric, v: number, digits = 2) {
      if (metric === 'time') return hoursMinutes(v);
      if (metric === 'distance') return `${n(v / distanceUnit, v ? digits : 0)} ${imperial ? 'mi' : 'km'}`;
      if (metric === 'ascent') return `${n(imperial ? v * FOOT : v)} ${imperial ? 'ft' : 'm'}`;
      return n(v);
    },
    /** An axis label: 4h 0min · 30 km · 2.500 m · 5. */
    tick(metric: VolumeMetric, v: number) {
      if (metric === 'time') return hoursMinutes(v);
      if (metric === 'distance')
        return `${n(v / distanceUnit, Number.isInteger(v / distanceUnit) ? 0 : 1)} ${imperial ? 'mi' : 'km'}`;
      if (metric === 'ascent') return `${n(v)} ${imperial ? 'ft' : 'm'}`;
      return n(v);
    },
    /** Ticks in the person's units (miles and feet round as miles and feet). */
    ticks(max: number, metric: VolumeMetric, intervals: number) {
      if (metric === 'ascent' && imperial)
        return axisTicks(max * FOOT, 'ascent', intervals).map((v) => v / FOOT);
      return axisTicks(max, metric, intervals, metric === 'distance' && imperial ? MILE / 1000 : 1);
    },
  };
}

export type VolumeFormat = ReturnType<typeof volumeFormat>;

/** "7 sept - 13 sept 2026": a week as the headers write it. */
export function weekTitle(monday: string, locale: string): string {
  const day = (iso: string, year = false) =>
    at(iso).toLocaleDateString(locale, {
      day: 'numeric',
      month: 'short',
      ...(year ? { year: 'numeric' } : {}),
    });
  return `${day(monday)} - ${day(addDays(monday, 6), true)}`;
}

export type ChartLabel = { index: number; text: string };

const shortMonth = (iso: string, locale: string) =>
  at(iso).toLocaleDateString(locale, { month: 'short' }).replace('.', '').toUpperCase();

/** Months under a weekly chart: one where a month starts, as SEP · OCT · NOV. */
export function monthLabels(starts: string[], locale: string): ChartLabel[] {
  return starts.flatMap((s, i) =>
    i > 0 && s.slice(5, 7) !== starts[i - 1]?.slice(5, 7) ? [{ index: i, text: shortMonth(s, locale) }] : [],
  );
}

/**
 * The words under a range's chart: each day's initial over 7 days, each Monday (as 9 NOV) over a month,
 * and the months over longer ranges.
 */
export function rangeLabels(range: ProgressRange, starts: string[], locale: string): ChartLabel[] {
  if (range === '7d')
    return starts.map((s, index) => ({
      index,
      text: at(s).toLocaleDateString(locale, { weekday: 'narrow' }).toUpperCase(),
    }));
  if (range === '1m')
    return starts.flatMap((s, index) =>
      at(s).getDay() === 1
        ? [
            {
              index,
              text: at(s)
                .toLocaleDateString(locale, { day: 'numeric', month: 'short' })
                .replace('.', '')
                .toUpperCase(),
            },
          ]
        : [],
    );
  return monthLabels(starts, locale);
}

/**
 * "Your progress at a glance": the sentence on a range's total against the one it is compared with (the
 * span before, or `compare` years back). `format` writes a total; `t` is the interface's translator.
 */
export function glanceText({
  range,
  compare,
  total,
  previousTotal,
  format,
  t,
}: {
  range: ProgressRange;
  compare: number | null;
  total: number;
  previousTotal: number;
  format: (v: number) => string;
  t: (key: MessageKey, values?: Record<string, string | number>) => string;
}): string {
  const pct = percentChange(total, previousTotal);
  const value = format(total);
  const previous = format(previousTotal);
  const period = t(`progress.period.${range}`);
  if (compare) {
    if (pct == null) return t('progress.glance.new', { value, period });
    const label = t('progress.glance.compareLabel', { count: compare });
    return t(pct >= 0 ? 'progress.glance.compareUp' : 'progress.glance.compareDown', {
      pct: Math.abs(pct),
      label,
      previous,
    });
  }
  if (!total) return t('progress.glance.none', { period });
  if (pct == null) return t('progress.glance.new', { value, period });
  if (pct === 0) return t('progress.glance.same', { value, period });
  return t(pct > 0 ? 'progress.glance.up' : 'progress.glance.down', {
    value,
    period,
    pct: Math.abs(pct),
    previous,
  });
}

/** Every month from this one back to the first activity's, newest first. */
export function monthsBack(first: string, today: string): { year: number; month: number }[] {
  const months: { year: number; month: number }[] = [];
  const end = new Date(`${first.slice(0, 7)}-15T12:00:00`);
  for (let d = new Date(`${today.slice(0, 7)}-15T12:00:00`); d >= end; d.setMonth(d.getMonth() - 1))
    months.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  return months;
}
