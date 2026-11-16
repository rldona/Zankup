/** What Volume, Series and the Training log share on the web: units, dates, sports and their colours. */
import {
  SPORT_ICONS,
  SPORTS,
  valueOf,
  volumeFormat,
  type CalendarDay,
  type LogMetric,
  type MaterialIcon,
  type Sport,
} from '@zankup/core';
import { useMemo } from 'react';

import { locale } from '../../i18n';
import { usePrefs } from '../../prefs';

export type Activities = CalendarDay['activities'];

export const asSport = (value: string): Sport =>
  (SPORTS as readonly string[]).includes(value) ? (value as Sport) : 'other';

/** Totals, axis labels and ticks in the person's units and language. */
export function useVolumeFormat() {
  const imperial = usePrefs().units === 'imperial';
  return useMemo(() => volumeFormat(imperial, locale), [imperial]);
}

/** A sport's chip icon, and the all-sports one. */
export const sportIcon = (sport: string | null): MaterialIcon =>
  sport ? SPORT_ICONS[asSport(sport)] : 'monitoring';

export const sportColour = (sport: string) => `var(--${asSport(sport)})`;

/** A day's colour: its sport's, or a neutral grey when it has more than one activity. */
export const dayColour = (activities: Activities) =>
  activities.length === 1 && activities[0] ? sportColour(activities[0].sport) : 'var(--muted)';

export const total = (activities: Activities, metric: LogMetric) =>
  activities.reduce((sum, a) => sum + valueOf(a, metric), 0);

export const at = (iso: string) => new Date(`${iso}T12:00:00`);

export const longDate = (iso: string) =>
  at(iso).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });

export const shortDate = (iso: string) =>
  at(iso).toLocaleDateString(locale, { day: '2-digit', month: '2-digit', year: '2-digit' });

/** The week's day initials, Monday first. */
export const dayLetters = (days: string[]) =>
  days.map((d) => at(d).toLocaleDateString(locale, { weekday: 'narrow' }).toUpperCase());
