/**
 * What the activity's page reads around its own numbers (OS-1006), as the app's: the context query
 * (fetched beside the activity, so it is in the cache by the time the page draws), and how its paces,
 * times and goals are written.
 */
import {
  activityContextQuery,
  type ActivityContext,
  type GoalMetric,
  type GoalPeriod,
  type ReachedGoal,
  type Sport,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';

import { api } from '../api';
import { locale, t } from '../i18n';
import { goalSport, goalValue, type Format } from '../progress/more/data';

/** The activity's context, or null while it loads (or when it has none): each block then hides. */
export function useActivityContext(activityId: number, enabled = true): ActivityContext | null {
  return (
    useQuery({ ...activityContextQuery(api, activityId), enabled: enabled && activityId > 0 }).data ?? null
  );
}

/** 5:41 /km, or 27,5 km/h; – without a speed. */
export function speedText(mps: number | null | undefined, pace: boolean, imperial: boolean): string {
  if (!mps) return '–';
  const unit = imperial ? 1609.344 : 1000;
  if (pace) {
    const s = Math.round(unit / mps);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} /${imperial ? 'mi' : 'km'}`;
  }
  return `${((mps * 3600) / unit).toLocaleString(locale, { maximumFractionDigits: 1 })} ${imperial ? 'mph' : 'km/h'}`;
}

/** 57:05 or 1:26:59. */
export function clock(seconds: number): string {
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const pad = (n: number) => String(n).padStart(2, '0');
  return h ? `${h}:${pad(m)}:${pad(s % 60)}` : `${m}:${pad(s % 60)}`;
}

type GoalLike = Pick<ReachedGoal, 'period' | 'metric' | 'sport' | 'target'>;

/** "Yearly: Distance goal" and "Run" (or "Run - 12 activities"), as the app's goals. */
export function goalWords(g: GoalLike): { title: string; note: string } {
  const title = t('progress.goalTitle', {
    period: t(`progress.goalPeriod.${g.period as GoalPeriod}`),
    kind: t(`progress.goalKind.${g.metric as GoalMetric}`),
  });
  const note =
    g.metric === 'count'
      ? t('progress.goalCountNote', { sport: goalSport(g.sport), count: Math.round(g.target) })
      : goalSport(g.sport);
  return { title, note };
}

/** A reached goal on one line: "Yearly: Distance goal · 1,000 km · Run". */
export function goalLine(g: GoalLike, format: Format): string {
  const words = goalWords(g);
  // A target reads round: 1,000 km, not 1,000.00 km.
  const value =
    g.metric === 'distance'
      ? `${Math.round(g.target / (format.unit === 'mi' ? 1609.344 : 1000)).toLocaleString(locale)} ${format.unit}`
      : goalValue(g.metric, g.target, format);
  const target = g.metric === 'count' ? '' : ` · ${value}`;
  return `${words.title}${target} · ${words.note}`;
}

/** A ride's work, intensity and training load (the server's, from the FTP), for the power chart. */
export function powerFigures(context: ActivityContext | null): [string, string][] {
  const p = context?.power;
  if (!p) return [];
  return [
    [t('detail.work'), `${p.work_kj.toLocaleString(locale)} kJ`],
    ...(p.intensity != null ? ([[t('detail.intensity'), String(p.intensity)]] as [string, string][]) : []),
    ...(p.load != null ? ([[t('detail.load'), String(p.load)]] as [string, string][]) : []),
  ];
}

/** Whether any of the sport's context blocks has something to say (they sit beside the charts). */
export function contextHasBlocks(c: ActivityContext | null, sport: Sport): boolean {
  if (!c) return false;
  const effortOrGoals = !!c.effort || c.goals.length > 0;
  if (sport === 'strength') return effortOrGoals;
  if (sport !== 'ride' && sport !== 'run') return false;
  const matches = c.matches.length > 1 && c.matches.some((m) => m.this);
  return !!c.fitness || matches || effortOrGoals || c.medals.length > 0 || c.segments.length > 0;
}
