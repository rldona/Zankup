/**
 * What Monthly activities, Data sources and Goals share on the web (OS-1005): the person's units, the
 * calendar of this month and the last, and how a goal's figures and a device's last use are written.
 * The working-out itself is in packages/core (progress/monthTotals.ts, progress/sources.ts, goals).
 */
import {
  calendarQuery,
  commonTokens,
  daysAgo,
  formatters,
  hoursMinutes,
  monthPairRange,
  SOURCE_NAMES,
  SPORT_ICONS,
  SPORTS,
  themes,
  type DeviceUse,
  type MaterialIcon,
  type Month,
  type Sport,
  type ThemeName,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { api } from '../../api';
import { locale, t } from '../../i18n';
import { usePrefs } from '../../prefs';

export type Format = ReturnType<typeof formatters>;

/** The person's units, as the rest of the web writes them. */
export function useFormat(): Format {
  const prefs = usePrefs();
  return useMemo(() => formatters(prefs), [prefs]);
}

/** The theme's colours, for what CSS variables cannot reach (the charts). */
export const palette = (theme: ThemeName) => ({ ...commonTokens, ...themes[theme] });

export const asSport = (value: string): Sport =>
  (SPORTS as readonly string[]).includes(value) ? (value as Sport) : 'other';

/** A goal's icon: its sport's, or a chart for all sports. */
export const goalIcon = (sport: string | null): MaterialIcon =>
  sport ? SPORT_ICONS[asSport(sport)] : 'monitoring';

/** A goal's sport, or "All sports". */
export const goalSport = (sport: string | null) =>
  sport ? t(`sport.${asSport(sport)}`) : t('progress.allSports');

/** A goal's figure in its units: 42,5 km · 4h 17min · 1.421 m · 3. */
export function goalValue(metric: string, value: number, format: Format): string {
  if (metric === 'distance') return format.distance(value);
  if (metric === 'time') return hoursMinutes(value);
  if (metric === 'ascent') return format.elevation(value);
  return value.toLocaleString(locale, { maximumFractionDigits: 1 });
}

/** The calendar of this month and the last, fetched once for the card and the page. */
export function useMonths() {
  const [today] = useState(() => new Date());
  const query = useQuery(calendarQuery(api, monthPairRange(today)));
  return { calendar: query.data, today, pending: query.isPending };
}

/** "October" in the person's language. */
export const monthName = (m: Month) => m.start.toLocaleDateString(locale, { month: 'long' });

/** The device's name, or its source's when the source names none. */
export const deviceName = (d: DeviceUse) => d.name ?? SOURCE_NAMES[d.source] ?? t('sources.file');

/** "today", "2 days ago", "3 weeks ago". */
export function lastUsed(iso: string, now = new Date()): string {
  const days = daysAgo(iso, now);
  if (days === 0) return t('sources.today');
  if (days < 7) return t('sources.daysAgo', { count: days });
  return t('sources.weeksAgo', { count: Math.floor(days / 7) });
}
