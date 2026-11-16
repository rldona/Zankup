/**
 * What an activity's page shows around its own numbers (OS-1006), as the app's and Strava's, from
 * /api/activities/{id}/context: how the day moved fitness, the efforts on the same route, its relative
 * effort against the weeks before, the goals it counts for, its results (medals in best times and power,
 * places on segments) and a run's pace zones. Each block hides itself when it has nothing to say; the
 * page puts them in the sport's order.
 */
import {
  bestValue,
  formatDuration,
  metricValues,
  paceRange,
  paceZoneSeconds,
  zoneScale,
  type ActivityContext,
  type ActivityDetail,
  type GoalMetric,
  type GoalPeriod,
  type MessageKey,
  type Series,
  type StrengthSet,
  groupSets,
} from '@zankup/core';
import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router';

import { locale, t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { usePrefs } from '../prefs';
import { CardLine } from '../progress/fitness/parts';
import { GoalRing } from '../progress/more/GoalsCard';
import { goalValue, useFormat } from '../progress/more/data';
import { Medal } from '../progress/zones/Bests';
import { bestName } from '../progress/zones/data';
import { CardTitle } from '../ui';
import { clock, speedText } from './activityContext';
import '../progress/fitness.css';
import '../progress/zones.css';
import './activity.css';

/** A block's heading and, at its foot, the link to its page (as Strava's orange line). */
function Block({
  title,
  link,
  to,
  children,
  className,
}: {
  title: string;
  link?: string;
  to?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel ctxblock${className ? ` ${className}` : ''}`}>
      <CardTitle>{title}</CardTitle>
      {children}
      {link && to && (
        <Link to={to} className="ctxlink">
          {link}
        </Link>
      )}
    </section>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="ctxfig">
      <span>{label}</span>
      <b className="num">{value}</b>
    </div>
  );
}

/** "Fitness up": the points the day added and the score, with the four weeks' line. */
export function FitnessBlock({ context }: { context: ActivityContext }) {
  const f = context.fitness;
  if (!f) return null;
  const points = Math.round(f.after - f.before);
  const title = t(points > 0 ? 'detail.fitnessUp' : points < 0 ? 'detail.fitnessDown' : 'detail.fitnessSame');
  const values = f.points.map((p) => p.ctl);
  return (
    <Block title={title} link={t('detail.fitnessLink')} to="/progress/fitness">
      <div className="ctxrow">
        <div className="ctxfigs">
          <Figure label={t('detail.points')} value={points > 0 ? `+${points}` : String(points)} />
          <Figure label={t('detail.fitnessScore')} value={String(Math.round(f.after))} />
        </div>
        {values.length > 1 && (
          <CardLine
            values={values}
            first={String(Math.round(values[0] ?? 0))}
            area
            level
            label={`${title}: ${Math.round(f.before)} → ${Math.round(f.after)}`}
          />
        )}
      </div>
    </Block>
  );
}

/** The efforts on the same route: this one's pace or speed, and the line of all of them. */
export function MatchesBlock({
  context,
  activityId,
  pace,
}: {
  context: ActivityContext;
  activityId: number;
  pace: boolean;
}) {
  const imperial = usePrefs().units === 'imperial';
  const mine = context.matches.find((m) => m.this);
  if (!mine || context.matches.length < 2) return null;
  const others = context.matches.length - 1;
  const title = t(pace ? 'detail.matchingRuns' : 'detail.matching');
  return (
    <Block
      title={title}
      link={t('detail.matchingLink', { count: others })}
      to={`/activities/${activityId}/matches`}
    >
      <div className="ctxrow">
        <div className="ctxfigs">
          <Figure
            label={t(pace ? 'detail.thisRun' : 'detail.thisActivity')}
            value={speedText(mine.speed_mps, pace, imperial)}
          />
        </div>
        {/* Faster is higher, for a pace too. */}
        <CardLine
          values={context.matches.map((m) => m.speed_mps ?? 0)}
          first=""
          label={`${title}: ${context.matches.map((m) => speedText(m.speed_mps, pace, imperial)).join(', ')}`}
        />
      </div>
    </Block>
  );
}

const EFFORT_BANDS = [
  { key: 'above', label: 'detail.effortAbove', colour: 'var(--zone-4)' },
  { key: 'in', label: 'detail.effortIn', colour: 'var(--accent)' },
  { key: 'below', label: 'detail.effortBelow', colour: 'var(--fitness)' },
] as const;

/** The relative effort against the average of the three weeks before, on three bands. */
export function EffortBlock({ context }: { context: ActivityContext }) {
  const e = context.effort;
  if (!e) return null;
  const status = e.status ?? 'in';
  const score = Math.round(e.score);
  return (
    <Block title={t('detail.effort')} link={t('detail.effortLink')} to="/progress/effort">
      <div className="ctxsays">
        <span className="note">{t(`detail.effortSays.${status}`)}</span>
        <b className="num">{score}</b>
      </div>
      <ol className="ctxbands">
        {EFFORT_BANDS.map((b) => {
          const on = b.key === status;
          return (
            <li
              key={b.key}
              className={on ? 'on' : undefined}
              style={on ? ({ '--band': b.colour } as CSSProperties) : undefined}
              aria-current={on || undefined}
            >
              <span>{t(b.label)}</span>
              {on && <b className="num">{score}</b>}
            </li>
          );
        })}
      </ol>
      {e.average ? (
        <p className="note">{t('detail.effortAverage', { value: Math.round(e.average) })}</p>
      ) : null}
    </Block>
  );
}

/** The goals the activity counts for, each with its ring and what is left. */
export function GoalsBlock({ context }: { context: ActivityContext }) {
  const format = useFormat();
  if (!context.goals.length) return null;
  return (
    <Block title={t('detail.goals')} link={t('detail.goalsLink')} to="/goals">
      <ul className="ctxlist">
        {context.goals.map((g, i) => {
          const left = g.target - g.current;
          const metric = g.metric as GoalMetric;
          return (
            <li key={i}>
              <GoalRing goal={g} size={48} />
              <span>
                <b>
                  {t(`detail.goalPeriod.${g.period as GoalPeriod}`)} ·{' '}
                  {left > 0
                    ? t('detail.goalLeft', { value: goalValue(metric, left, format) })
                    : t('detail.goalDone')}
                </b>
                <small className="note num">
                  {goalValue(metric, g.current, format)} / {goalValue(metric, g.target, format)}
                </small>
              </span>
            </li>
          );
        })}
      </ul>
    </Block>
  );
}

/** The activity's results: its medals in best times or power, and its places on segments. */
export function ResultsBlock({ context, ride }: { context: ActivityContext; ride: boolean }) {
  const prefs = usePrefs();
  const imperial = prefs.units === 'imperial';
  const { medals, segments } = context;
  if (!medals.length && !segments.length) return null;
  const counts: [string, number][] = [
    [t(ride ? 'detail.skills' : 'detail.bestTimes'), medals.length],
    [t('detail.segments'), segments.length],
  ];
  return (
    <Block title={t('detail.results')} link={t('detail.resultsLink')} to="/progress/bests">
      <dl className="ctxcounts">
        {counts.map(([label, n]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd className="num">{n}</dd>
          </div>
        ))}
      </dl>
      <ul className="ctxlist">
        {medals.slice(0, 3).map((m) => (
          <li key={m.key}>
            <Link to={`/progress/bests?key=${m.key}`} className="ctxrowlink">
              <Medal rank={m.rank} size={36} />
              <span>
                <b>{bestName(m.key)}</b>
                <small className="note num">
                  {bestValue(m.key, m.value, locale, imperial)} ·{' '}
                  {t(m.scope === 'all' ? 'progress.allTimeBest' : 'progress.yearBest')}
                </small>
              </span>
            </Link>
          </li>
        ))}
        {segments.slice(0, 3).map((s) => (
          <li key={s.segment_id}>
            <Link to={`/segments/${s.segment_id}`} className="ctxrowlink">
              {s.rank <= 3 ? <Medal rank={s.rank} size={36} /> : <b className="ctxrank num">{s.rank}</b>}
              <span>
                <b>{s.name}</b>
                <small className="note num">
                  {(s.distance_m / (imperial ? 1609.344 : 1000)).toLocaleString(locale, {
                    maximumFractionDigits: 2,
                  })}{' '}
                  {imperial ? 'mi' : 'km'} · {clock(s.elapsed_s)}
                  {s.avg_power ? ` · ${s.avg_power} W` : ''}
                </small>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Block>
  );
}

/**
 * Time in each zone (#283): a thick bar per zone, as long as its time against the longest, the time
 * beside it and the range on the right; heart rate in reds, pace in blues, power in purples.
 */
export function ZoneRows({
  kind,
  seconds,
  range,
  share = false,
  reverse = false,
}: {
  kind: 'hr' | 'pace' | 'power';
  seconds: number[];
  range: (index: number) => string;
  /** The share of the time instead of the time itself (pace zones, as Strava's). */
  share?: boolean;
  /** The highest zone on top (pace). */
  reverse?: boolean;
}) {
  const colours = zoneScale(kind, seconds.length);
  const most = Math.max(1, ...seconds);
  const total = seconds.reduce((a, b) => a + b, 0) || 1;
  const rows = seconds.map((s, i) => ({ s, i }));
  return (
    <ol className="pz-rows zonerows">
      {(reverse ? rows.reverse() : rows).map(({ s, i }) => {
        const value = share ? `${Math.round((s / total) * 100)}%` : formatDuration(s);
        return (
          <li key={i}>
            <b>Z{i + 1}</b>
            <span className="pz-track">
              <i
                style={{ '--w': `${Math.max(2, (s / most) * 62)}%`, background: colours[i] } as CSSProperties}
              />
              <b className="num">{value}</b>
            </span>
            <span className="num pz-range">{range(i)}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** A run's pace zones: the share of moving time in each, its range as paces, the fastest on top. */
export function PaceZonesBlock({ context, series }: { context: ActivityContext; series: Series | null }) {
  const imperial = usePrefs().units === 'imperial';
  const zones = context.pace_zones;
  if (!zones || !series) return null;
  const seconds = paceZoneSeconds(metricValues(series, 'speed') ?? [], series.t, zones.bounds);
  if (!seconds.some((s) => s > 0)) return null;
  const unit = imperial ? 1609.344 : 1000;
  return (
    <section className="panel zones">
      <CardTitle>{t('detail.paceZones')}</CardTitle>
      <p className="note">
        {t('detail.paceZonesNote', { pace: speedText(zones.threshold_mps, true, imperial) })}
      </p>
      <ZoneRows kind="pace" seconds={seconds} share reverse range={(i) => paceRange(zones.bounds, i, unit)} />
    </section>
  );
}

/**
 * The workout's exercises at a glance, near the top (as Strava's "Exercises"): each one once, with its
 * sets and reps (and weight lifted, if any). The full sets, with their weights and rests, come lower.
 */
export function ExercisesBlock({ activity }: { activity: Pick<ActivityDetail, 'sets' | 'device'> }) {
  const sets: StrengthSet[] = activity.sets ?? [];
  const byExercise = new Map<string, { sets: number; reps: number; volume: number }>();
  for (const group of groupSets(sets)) {
    const key = group.category ?? 'unknown';
    const row = byExercise.get(key) ?? { sets: 0, reps: 0, volume: 0 };
    row.sets += group.sets.length;
    row.reps += group.reps;
    row.volume += group.volume_kg;
    byExercise.set(key, row);
  }
  if (!byExercise.size) return null;
  return (
    <section className="panel ctxblock">
      <CardTitle right={activity.device ? <span className="note">{activity.device}</span> : undefined}>
        {t('detail.exercises')}
      </CardTitle>
      <ul className="ctxlist">
        {[...byExercise].map(([category, row]) => (
          <li key={category}>
            <span className="exicon" aria-hidden="true">
              <MaterialIcon name="fitness_center" />
            </span>
            <span>
              <b>{t(`exercise.${category}` as MessageKey)}</b>
              <small className="note num">
                {t('detail.exerciseLine', { sets: row.sets, reps: row.reps })}
                {row.volume ? ` · ${Math.round(row.volume).toLocaleString(locale)} kg` : ''}
              </small>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
