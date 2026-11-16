/**
 * What Training zones, Skills and Best times share on the web (OS-1004): the measures' names, the
 * dates as they read, and the rider's best power by duration with the weight it is read for.
 */
import {
  addDays,
  curvesQuery,
  durationLabel,
  isoDay,
  meQuery,
  RUN_KEYS,
  SPORTS,
  type MessageKey,
  type Sport,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { api } from '../../api';
import { locale, t } from '../../i18n';

/** Without a weight in the profile, the scale is read for a rider of this much (as the app). */
export const REFERENCE_KG = 75;

export const asSport = (value: string | null): Sport =>
  (SPORTS as readonly string[]).includes(value ?? '') ? (value as Sport) : 'other';

/** A measure's name: 5 km, ½ mile… Power: 30 s, Longest ride. */
export function bestName(key: string): string {
  if (key.startsWith('power_'))
    return t('progress.bestPower', { duration: durationLabel(Number(key.slice(6))) });
  if (key === 'longest') return t('progress.bestLongest');
  return t(`effort.${key}` as MessageKey);
}

export const sportOf = (key: string): 'run' | 'ride' =>
  (RUN_KEYS as readonly string[]).includes(key) ? 'run' : 'ride';

/** 4 November 2026 (or 4 November, without the year). */
export const longDate = (iso: string, withYear = true) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'long',
    ...(withYear ? { year: 'numeric' } : {}),
  });

export function useToday() {
  const [today] = useState(() => isoDay(new Date()));
  return today;
}

const asMap = (points?: { duration_s: number; value: number }[]) =>
  new Map((points ?? []).map((p) => [p.duration_s, p.value]));

/** The best power at each duration, for the last eight weeks and for all time, and the weight. */
export function useSkills() {
  const today = useToday();
  const recent = useQuery(curvesQuery(api, 'power', { from: addDays(today, -55), to: addDays(today, 1) }));
  const all = useQuery(curvesQuery(api, 'power', { from: '2000-01-01', to: addDays(today, 1) }));
  const me = useQuery(meQuery(api));
  const recentMap = useMemo(() => asMap(recent.data), [recent.data]);
  const allMap = useMemo(() => asMap(all.data), [all.data]);
  const weight = me.data?.weight_kg ?? null;
  return {
    recent: recentMap,
    all: allMap,
    weight: weight ?? REFERENCE_KG,
    hasWeight: weight != null,
    isPending: recent.isPending || all.isPending,
  };
}
