/**
 * What an activity's page shows around its own numbers (as Strava's), from /api/activities/{id}/context:
 * how the day moved fitness, the efforts on the same route, its relative effort against the weeks
 * before, the goals it counts for and its results (medals in best times and power, and on segments).
 * Each block hides itself when it has nothing to say; the page puts them in the sport's order.
 */
import {
  activityContextQuery,
  metricValues,
  zoneScale,
  type ActivityContext,
  type Series,
  bestValue,
  type GoalMetric,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../auth';
import { t } from '../i18n';
import { locale } from '../i18n';
import { useUnits } from '../prefs';
import { bestName, Medal } from '../progress/BestsCard';
import { statusColour } from '../progress/EffortCard';
import { FitnessSparkline } from '../progress/FitnessChart';
import { GoalRing, goalWords } from '../progress/GoalsCard';
import { useVolumeFormat } from '../progress/VolumeCard';
import { useTheme } from '../theme';
import { accent, Card, Muted } from '../ui';
import { paceRange, paceZoneSeconds } from './paceZones';

export function useActivityContext(activityId: number) {
  const api = useApi();
  return useQuery(activityContextQuery(api, activityId)).data ?? null;
}

/** A block's heading and, at its foot, the link to its screen (as Strava's orange line). */
function Block({
  title,
  link,
  onLink,
  children,
}: {
  title: string;
  link?: string;
  onLink?: () => void;
  children: React.ReactNode;
}) {
  const c = useTheme();
  return (
    <Card style={{ gap: 10 }}>
      <Text style={{ color: c.fg, fontSize: 17, fontWeight: '800' }}>{title}</Text>
      {children}
      {link && onLink ? (
        <Pressable accessibilityRole="link" onPress={onLink} hitSlop={8} style={{ alignSelf: 'flex-end' }}>
          <Text style={{ color: accent(c), fontSize: 14, fontWeight: '700' }}>{link}</Text>
        </Pressable>
      ) : null}
    </Card>
  );
}

function Figure({ label, value }: { label: string; value: string }) {
  const c = useTheme();
  return (
    <View>
      <Muted style={{ fontSize: 12 }}>{label}</Muted>
      <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
        {value}
      </Text>
    </View>
  );
}

/** "Fitness up": the points the day added and the score, with the four weeks' line. */
export function FitnessBlock({ context }: { context: ActivityContext }) {
  const f = context.fitness;
  if (!f) return null;
  const points = Math.round(f.after - f.before);
  const title = t(points > 0 ? 'detail.fitnessUp' : points < 0 ? 'detail.fitnessDown' : 'detail.fitnessSame');
  return (
    <Block title={title} link={t('detail.fitnessLink')} onLink={() => router.push('/fitness')}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-end' }}>
        <View style={{ flex: 1, flexDirection: 'row', gap: 28 }}>
          <Figure label={t('detail.points')} value={points > 0 ? `+${points}` : String(points)} />
          <Figure label={t('detail.fitnessScore')} value={String(Math.round(f.after))} />
        </View>
        {f.points.length > 1 && (
          <FitnessSparkline values={f.points.map((p) => p.ctl)} width={120} height={64} />
        )}
      </View>
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
  const imperial = useUnits() === 'imperial';
  const mine = context.matches.find((m) => m.this);
  if (!mine || context.matches.length < 2) return null;
  const others = context.matches.length - 1;
  return (
    <Block
      title={t(pace ? 'detail.matchingRuns' : 'detail.matching')}
      link={t('detail.matchingLink', { count: others })}
      onLink={() => router.push(`/matches?id=${activityId}`)}
    >
      <View style={{ flexDirection: 'row', alignItems: 'flex-end' }}>
        <View style={{ flex: 1 }}>
          <Figure
            label={t(pace ? 'detail.thisRun' : 'detail.thisActivity')}
            value={speedText(mine.speed_mps, pace, imperial)}
          />
        </View>
        <FitnessSparkline
          values={context.matches.map((m) => (pace ? -(1 / (m.speed_mps || 1)) : (m.speed_mps ?? 0)))}
          width={120}
          height={64}
          first={false}
        />
      </View>
    </Block>
  );
}

/** 5:41 /km, or 7,5 km/h. */
export function speedText(mps: number | null | undefined, pace: boolean, imperial: boolean): string {
  if (!mps) return '–';
  const unit = imperial ? 1609.344 : 1000;
  if (pace) {
    const s = Math.round(unit / mps);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} /${imperial ? 'mi' : 'km'}`;
  }
  return `${((mps * 3600) / unit).toLocaleString(locale, { maximumFractionDigits: 1 })} ${imperial ? 'mph' : 'km/h'}`;
}

/** The relative effort against the average of the three weeks before, on three bands. */
export function EffortBlock({ context }: { context: ActivityContext }) {
  const c = useTheme();
  const e = context.effort;
  if (!e) return null;
  const status = e.status ?? 'in';
  const bands = [
    { key: 'above', label: t('detail.effortAbove') },
    { key: 'in', label: t('detail.effortIn') },
    { key: 'below', label: t('detail.effortBelow') },
  ] as const;
  return (
    <Block title={t('detail.effort')} link={t('detail.effortLink')} onLink={() => router.push('/effort')}>
      <View style={{ flexDirection: 'row', alignItems: 'baseline' }}>
        <Muted style={{ flex: 1, fontSize: 14 }}>{t(`detail.effortSays.${status}`)}</Muted>
        <Text style={{ color: c.fg, fontSize: 26, fontWeight: '300', fontVariant: ['tabular-nums'] }}>
          {Math.round(e.score)}
        </Text>
      </View>
      <View style={{ borderRadius: 10, overflow: 'hidden' }}>
        {bands.map((b) => {
          const on = b.key === status;
          return (
            <View
              key={b.key}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingHorizontal: 12,
                paddingVertical: 9,
                backgroundColor: on ? statusColour(c, b.key) : c.surface2,
              }}
            >
              <Text
                style={{ flex: 1, color: on ? '#fff' : c.fg, fontSize: 13, fontWeight: on ? '800' : '600' }}
              >
                {b.label}
              </Text>
              {on && (
                <Text style={{ color: '#fff', fontSize: 13, fontWeight: '800' }}>{Math.round(e.score)}</Text>
              )}
            </View>
          );
        })}
      </View>
      {e.average ? (
        <Muted style={{ fontSize: 12 }}>{t('detail.effortAverage', { value: Math.round(e.average) })}</Muted>
      ) : null}
    </Block>
  );
}

/** The goals the activity counts for, each with its ring and what is left. */
export function GoalsBlock({ context }: { context: ActivityContext }) {
  const c = useTheme();
  const format = useVolumeFormat();
  if (!context.goals.length) return null;
  return (
    <Block title={t('detail.goals')} link={t('detail.goalsLink')} onLink={() => router.push('/objectives')}>
      {context.goals.map((g, i) => {
        const left = g.target - g.current;
        const metric = g.metric as GoalMetric;
        return (
          <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
            <GoalRing goal={g as never} size={48} />
            <View style={{ flex: 1 }}>
              <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700' }}>
                {t(`detail.goalPeriod.${g.period}` as never)} ·{' '}
                {left > 0
                  ? t('detail.goalLeft', { value: format.value(metric, left, 1) })
                  : t('detail.goalDone')}
              </Text>
              <Muted style={{ fontSize: 12 }}>
                {format.value(metric, g.current, 1)} / {format.value(metric, g.target, 0)} ·{' '}
                {goalWords(g as never).note}
              </Muted>
            </View>
          </View>
        );
      })}
    </Block>
  );
}

/** The activity's results: its medals in best times or power, and its places on segments. */
export function ResultsBlock({ context, ride }: { context: ActivityContext; ride: boolean }) {
  const c = useTheme();
  const imperial = useUnits() === 'imperial';
  const medals = context.medals;
  const segments = context.segments;
  if (!medals.length && !segments.length) return null;
  const counts: [string, number][] = [
    [t(ride ? 'detail.skills' : 'detail.bestTimes'), medals.length],
    [t('detail.segments'), segments.length],
  ];
  return (
    <Block title={t('detail.results')} link={t('detail.resultsLink')} onLink={() => router.push('/bests')}>
      <View style={{ flexDirection: 'row' }}>
        {counts.map(([label, n]) => (
          <View key={label} style={{ flex: 1, alignItems: 'center' }}>
            <Muted style={{ fontSize: 12 }}>{label}</Muted>
            <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>{n}</Text>
          </View>
        ))}
      </View>
      {medals.slice(0, 3).map((m) => (
        <Pressable
          key={m.key}
          accessibilityRole="button"
          onPress={() => router.push(`/bests?key=${m.key}`)}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}
        >
          <Medal rank={m.rank} size={36} />
          <View style={{ flex: 1 }}>
            <Text style={{ color: c.fg, fontSize: 15 }}>{bestName(m.key)}</Text>
            <Muted style={{ fontSize: 13 }}>
              {bestValue(m.key, m.value, locale, imperial)} ·{' '}
              {t(m.scope === 'all' ? 'progress.allTimeBest' : 'progress.yearBest')}
            </Muted>
          </View>
        </Pressable>
      ))}
      {segments.slice(0, 3).map((s) => (
        <View key={s.segment_id} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          {s.rank <= 3 ? (
            <Medal rank={s.rank} size={36} />
          ) : (
            <View style={{ width: 36, alignItems: 'center' }}>
              <Text style={{ color: c.muted, fontSize: 15, fontWeight: '800' }}>{s.rank}</Text>
            </View>
          )}
          <View style={{ flex: 1 }}>
            <Text numberOfLines={1} style={{ color: c.fg, fontSize: 15 }}>
              {s.name}
            </Text>
            <Muted style={{ fontSize: 13 }}>
              {(s.distance_m / (imperial ? 1609.344 : 1000)).toLocaleString(locale, {
                maximumFractionDigits: 2,
              })}{' '}
              {imperial ? 'mi' : 'km'} · {clock(s.elapsed_s)}
              {s.avg_power ? ` · ${s.avg_power} W` : ''}
            </Muted>
          </View>
        </View>
      ))}
    </Block>
  );
}

/** 57:05 or 1:26:59. */
export function clock(seconds: number): string {
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const pad = (n: number) => String(n).padStart(2, '0');
  return h ? `${h}:${pad(m)}:${pad(s % 60)}` : `${m}:${pad(s % 60)}`;
}

/** A run's pace zones: the share of moving time in each, its range as paces, the fastest on top. */
export function PaceZonesBlock({ context, series }: { context: ActivityContext; series: Series | null }) {
  const c = useTheme();
  const imperial = useUnits() === 'imperial';
  const zones = context.pace_zones;
  if (!zones || !series) return null;
  const seconds = paceZoneSeconds(metricValues(series, 'speed') ?? [], series.t, zones.bounds);
  const total = seconds.reduce((a, b) => a + b, 0);
  if (!total) return null;
  const unit = imperial ? 1609.344 : 1000;
  const shades = zoneScale('pace', seconds.length);
  return (
    <Card style={{ gap: 10 }}>
      <Text style={{ color: c.fg, fontSize: 17, fontWeight: '800' }}>{t('detail.paceZones')}</Text>
      <Muted style={{ fontSize: 13 }}>
        {t('detail.paceZonesNote', { pace: speedText(zones.threshold_mps, true, imperial) })}
      </Muted>
      {seconds
        .map((s, i) => ({ s, i }))
        .reverse()
        .map(({ s, i }) => {
          const share = Math.round((s / total) * 100);
          return (
            <View key={i} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Text style={{ width: 24, color: c.fg, fontSize: 14, fontWeight: '800' }}>Z{i + 1}</Text>
              <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <View
                  style={{
                    width: `${Math.max(2, share * 0.62)}%`,
                    minWidth: 8,
                    height: 20,
                    borderRadius: 6,
                    backgroundColor: shades[i],
                  }}
                />
                <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700' }}>{share}%</Text>
              </View>
              <Muted style={{ fontSize: 13, fontVariant: ['tabular-nums'] }}>
                {paceRange(zones.bounds, i, unit)}
              </Muted>
            </View>
          );
        })}
    </Card>
  );
}
