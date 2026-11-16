/**
 * The sections of Stats (OS-519), as in the PWA (OS-311, OS-315 to OS-323): the goal card, the
 * estimates (their own screen, from You), the events' wording (Notifications), then for the period the summary
 * against last year, fitness and form, volume by sport, best-effort curves, records with their
 * progression, the year-over-year line, aerobic efficiency and the comparison with Strava.
 */
import {
  curvesQuery,
  efficiencyQuery,
  estimatesQuery,
  fitnessQuery,
  formatDate,
  formatDuration,
  formatHoursMinutes,
  progressQuery,
  recordsQuery,
  SPORTS,
  stravaComparisonQuery,
  summaryQuery,
  volumeQuery,
  type EstimateOut,
  type EventOut,
  type GoalOut,
  type MessageKey,
  type Sport,
  yearColours,
  effortShown,
  alpha,
  compare,
  comparisonLabels,
  mix,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../auth';
import { Chart, type Layer } from '../charts';
import { GoalChart } from './GoalChart';
import {
  effortName,
  ESTIMATE_ORDER,
  estimateName,
  estimateValue,
  methodText,
  RACES,
  shortDay,
} from '../estimates';
import { Icon } from '../Icon';
import { locale, t } from '../i18n';
import { asSport, useFormat } from '../prefs';
import { openGoals, rememberGoal } from '../storage';
import { useTheme } from '../theme';
import { Card, CardTitle, Choice, Heading, Muted, ProgressBar, Stat, styles as ui } from '../ui';

type Format = ReturnType<typeof useFormat>;
export type Range = { from: string; to: string };

const open = (id: number) => router.push(`/activity/${id}`);

/** A pressable line of text that opens an activity. */
function ActivityLink({ id, children }: { id: number; children: string }) {
  const c = useTheme();
  return (
    <Text accessibilityRole="link" onPress={() => open(id)} style={{ color: c.accent, fontWeight: '600' }}>
      {children}
    </Text>
  );
}

// --- now ------------------------------------------------------------------------------------------

function goalValue(goal: { metric: string }, value: number, format: Format): string {
  if (goal.metric === 'distance') return format.distance(value);
  if (goal.metric === 'time') return formatHoursMinutes(value);
  return value.toLocaleString(locale, { maximumFractionDigits: 1 });
}

export function GoalCard({
  goal,
  format,
  children,
}: {
  goal: GoalOut;
  format: Format;
  children?: React.ReactNode;
}) {
  const c = useTheme();
  const share = Math.min(1, goal.current / goal.target);
  const done = goal.current >= goal.target;
  // Open or folded as it was left, even after closing the app.
  const [chart, setChart] = useState(() => openGoals().has(goal.id));
  const toggle = () => {
    setChart(!chart);
    rememberGoal(goal.id, !chart);
  };
  return (
    <Card>
      {/* The whole card opens and closes the day-by-day chart. The chart itself sits outside the touch
          area, so reading it with a finger never folds it. */}
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded: chart }}
        accessibilityHint={t(chart ? 'goals.hideChart' : 'goals.showChart')}
        onPress={toggle}
        style={({ pressed }) => ({ gap: 8, opacity: pressed ? 0.7 : 1 })}
      >
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <Text style={{ color: c.fg, fontWeight: '700' }}>
            {t(`goals.period.${goal.period}` as MessageKey)} ·{' '}
            {goal.sport ? t(`sport.${asSport(goal.sport)}`) : t('goals.allSports')}
          </Text>
          <Text style={{ color: c.fg, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
            {Math.round(share * 100)} %
          </Text>
        </View>
        <ProgressBar value={share} max={1} label={t('goals.target')} />
        <Text style={{ color: c.fg, fontVariant: ['tabular-nums'] }}>
          <Text style={{ fontWeight: '700' }}>{goalValue(goal, goal.current, format)}</Text> /{' '}
          {goalValue(goal, goal.target, format)}
        </Text>
        {/* Nothing done yet: no projection to speak of. */}
        {(done || goal.current > 0) && (
          <Muted>
            {done
              ? t('goals.done')
              : t(goal.projection >= goal.target ? 'goals.onTrack' : 'goals.behind', {
                  projection: goalValue(goal, goal.projection, format),
                })}
          </Muted>
        )}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
          <Text style={{ color: c.accent, fontWeight: '600' }}>
            {t(chart ? 'goals.hideChart' : 'goals.showChart')}
          </Text>
          <Icon
            name={chart ? 'keyboard_arrow_up' : 'keyboard_arrow_down'}
            color={String(c.accent)}
            size={20}
          />
        </View>
      </Pressable>
      {chart && (
        // Some room between the figures above and the chart.
        <View style={{ marginTop: 12 }}>
          <GoalChart
            goal={goal}
            value={(v) => goalValue(goal, v, format)}
            tick={(v) =>
              goal.metric === 'distance'
                ? `${Math.round(v / (format.unit === 'km' ? 1000 : 1609.344))} ${format.unit}`
                : goal.metric === 'time'
                  ? `${Math.round(v / 3600)} h`
                  : String(Math.round(v))
            }
          />
        </View>
      )}
      {/* The edit and delete buttons, with the same room above. */}
      {children ? <View style={{ marginTop: 12 }}>{children}</View> : null}
    </Card>
  );
}

export function eventText(e: EventOut, format: Format): string {
  const p = e.payload as Record<string, unknown>;
  if (e.kind === 'record')
    return t('event.record', {
      distance: effortName(String(p.distance_key)),
      time: formatDuration(Number(p.time_s)),
    });
  if (e.kind === 'estimate_up')
    return t('event.estimate_up', {
      kind: estimateName(String(p.kind)),
      value: estimateValue(String(p.kind), Number(p.value), format),
    });
  if (e.kind === 'ramp') return t('event.ramp', { ramp: String(p.ramp) });
  return e.kind;
}

export function Estimates() {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const estimates = useQuery(estimatesQuery(api));
  if (!estimates.data) return null;
  if (!estimates.data.length)
    return (
      <Card>
        <CardTitle>{t('stats.estimates')}</CardTitle>
        <Muted>{t('stats.estimatesEmpty')}</Muted>
      </Card>
    );
  const by = new Map(estimates.data.map((e) => [e.kind, e]));
  const main = ESTIMATE_ORDER.flatMap((k) => (by.get(k) ? [by.get(k) as EstimateOut] : []));
  const races = RACES.flatMap((k) => (by.get(k) ? [by.get(k) as EstimateOut] : []));
  return (
    <>
      <Heading>{t('stats.estimates')}</Heading>
      {main.map((e) => (
        <Card key={e.kind}>
          <View
            style={{
              flexDirection: 'row',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              marginBottom: 12,
            }}
          >
            <Text style={{ color: c.fg, fontSize: 17, fontWeight: '700' }}>{estimateName(e.kind)}</Text>
            <Text style={[ui.statValue, { color: c.fg, fontSize: 22 }]}>
              {estimateValue(e.kind, e.value, format)}
            </Text>
          </View>
          {e.history.length > 1 && (
            <Chart
              legendGap={18}
              height={48}
              days={[]}
              label={estimateName(e.kind)}
              layers={[
                {
                  kind: 'line',
                  label: estimateName(e.kind),
                  color: c.accent,
                  values: e.history.map((h) => h.value),
                  hideLegend: true,
                },
              ]}
            />
          )}
          <Muted>
            {methodText(e.method)}
            {e.sources.length > 0 && ': '}
            {e.sources.map((s, i) => (
              <Text key={s.id}>
                {i > 0 && ', '}
                <ActivityLink id={s.id}>{shortDay(s.day)}</ActivityLink>
              </Text>
            ))}
          </Muted>
        </Card>
      ))}
      {races.length > 0 && (
        <Card>
          <CardTitle>{t('stats.predictions')}</CardTitle>
          <Muted>{methodText(races[0]?.method ?? '')}</Muted>
          <View style={ui.stats}>
            {races.map((r) => (
              <Stat key={r.kind} label={estimateName(r.kind)} value={formatDuration(r.value)} />
            ))}
          </View>
        </Card>
      )}
    </>
  );
}

// --- the period ----------------------------------------------------------------------------------

export function Summary({ range }: { range: Range }) {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const summary = useQuery(summaryQuery(api, range));
  if (!summary.data) return null;
  const { current, previous } = summary.data;
  const labels = comparisonLabels(range, locale, t); // null for All: nothing to compare with
  const rows: [string, string, number, number][] = [
    [t('feed.title'), current.count.toLocaleString(locale), current.count, previous.count],
    [t('feed.stat.distance'), format.distance(current.distance_m), current.distance_m, previous.distance_m],
    [t('feed.stat.time'), formatHoursMinutes(current.moving_s), current.moving_s, previous.moving_s],
    [t('feed.stat.ascent'), format.elevation(current.ascent_m), current.ascent_m, previous.ascent_m],
    [t('home.load'), Math.round(current.load).toLocaleString(locale), current.load, previous.load],
  ];
  const bar = (share: number, colour: string) => (
    <View
      style={{
        height: 7,
        borderRadius: 4,
        backgroundColor: colour,
        width: `${Math.max(share * 100, share ? 2 : 0)}%`,
      }}
    />
  );
  return (
    <Card>
      <CardTitle info={t('stats.summaryNote')}>{t('stats.summaryTitle')}</CardTitle>
      {/* Each figure against the same period a year before: last year's bar, then this period's. */}
      {rows.map(([label, value, now, before]) => {
        const { percent, now: mine, before: theirs } = compare(now, before);
        const tone =
          percent == null || percent === 0 ? c.muted : percent > 0 ? c.formPositive : c.formNegative;
        return (
          <View key={label} style={{ paddingVertical: 6, gap: 4 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <Muted style={{ flex: 1, fontSize: 14 }}>{label}</Muted>
              <Text style={[ui.statValue, { color: c.fg }]}>{value}</Text>
              {labels && percent ? (
                <Text
                  style={{
                    color: mix(tone, c.fg, 0.4), // towards the text colour, to read on the pale pill
                    backgroundColor: alpha(tone, 0.15),
                    fontSize: 12,
                    fontWeight: '600',
                    paddingHorizontal: 8,
                    paddingVertical: 1,
                    borderRadius: 999,
                    overflow: 'hidden',
                  }}
                >
                  {t('stats.change', { sign: percent > 0 ? '+' : '', percent })}
                </Text>
              ) : null}
            </View>
            {labels && bar(theirs, alpha(c.muted, 0.45))}
            {labels && bar(mine, c.accent)}
          </View>
        );
      })}
      {labels && (
        <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 16, marginTop: 6 }}>
          {[
            [labels[0], alpha(c.muted, 0.45)],
            [labels[1], c.accent],
          ].map(([label, colour]) => (
            <View key={label} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <View style={{ width: 12, height: 7, borderRadius: 4, backgroundColor: colour }} />
              <Muted style={{ fontSize: 12 }}>{label}</Muted>
            </View>
          ))}
        </View>
      )}
    </Card>
  );
}

const dayLabel = (iso: string) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'short' });

export function Fitness({ range }: { range: Range }) {
  const api = useApi();
  const c = useTheme();
  const fitness = useQuery(fitnessQuery(api, range));
  const days = fitness.data ?? [];
  const top = Math.max(1, ...days.flatMap((d) => [d.ctl, d.atl])) * 1.05;
  return (
    <Card>
      <CardTitle info={t('stats.fitnessNote')}>{t('stats.fitness')}</CardTitle>
      {days.length > 1 ? (
        <Chart
          legendGap={18}
          days={days.map((d) => dayLabel(d.day))}
          label={`${t('stats.ctl')} ${days.at(-1)?.ctl.toFixed(0)}, ${t('stats.atl')} ${days.at(-1)?.atl.toFixed(0)}, ${t('stats.tsb')} ${days.at(-1)?.tsb.toFixed(0)}`}
          height={180}
          layers={[
            {
              kind: 'bars',
              series: [
                {
                  label: t('stats.tsb'),
                  color: c.formPositive,
                  values: days.map((d) => (d.tsb >= 0 ? d.tsb : null)),
                },
                {
                  label: `${t('stats.tsb')} < 0`,
                  color: c.formNegative,
                  values: days.map((d) => (d.tsb < 0 ? d.tsb : null)),
                },
              ],
            },
            {
              kind: 'line',
              label: t('stats.ctl'),
              color: c.accent,
              values: days.map((d) => d.ctl),
              domain: [0, top],
            },
            {
              kind: 'line',
              label: t('stats.atl'),
              color: c.zone5,
              values: days.map((d) => d.atl),
              domain: [0, top],
              width: 1.2,
            },
          ]}
        />
      ) : (
        <Muted>{t('stats.empty')}</Muted>
      )}
    </Card>
  );
}

const sportOrder = (s: string) => {
  const i = (SPORTS as readonly string[]).indexOf(s);
  return i < 0 ? SPORTS.length : i;
};

export function Volume({ range, unit }: { range: Range; unit: 'day' | 'week' | 'month' | 'year' }) {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const [metric, setMetric] = useState<'time' | 'distance'>('time');
  const volume = useQuery(volumeQuery(api, range, unit));
  const buckets = volume.data ?? [];
  const sports = [...new Set(buckets.flatMap((b) => Object.keys(b.by_sport)))].sort(
    (a, b) => sportOrder(a) - sportOrder(b),
  );
  const scale = format.unit === 'km' ? 1000 : 1609.344;
  const label = (start: string) => {
    const d = new Date(`${start}T12:00:00`);
    if (unit === 'year') return String(d.getFullYear());
    if (unit === 'month') return d.toLocaleDateString(locale, { month: 'short', year: '2-digit' });
    return d.toLocaleDateString(locale, { day: 'numeric', month: 'short' });
  };
  const total = buckets.reduce(
    (s, b) => s + (metric === 'time' ? b.totals.moving_s : b.totals.distance_m),
    0,
  );
  return (
    <Card>
      <CardTitle
        right={
          <Choice
            label={t('stats.volume')}
            value={metric}
            onChange={setMetric}
            options={[
              { value: 'time', label: t('stats.hours') },
              { value: 'distance', label: t('stats.distanceUnit') },
            ]}
          />
        }
      >
        {t('stats.volume')}
      </CardTitle>
      {buckets.length > 0 && (
        <>
          <Text style={[ui.statValue, { color: c.fg, alignSelf: 'flex-end' }]}>
            {metric === 'time' ? formatHoursMinutes(total) : `${(total / scale).toFixed(0)} ${format.unit}`}
          </Text>
          <Chart
            legendGap={18}
            days={buckets.map((b) => label(b.start))}
            height={170}
            label={t('stats.volume')}
            layers={[
              {
                kind: 'bars',
                series: sports.map((s) => ({
                  label: t(`sport.${asSport(s)}`),
                  color: (c as unknown as Record<string, string>)[s] ?? c.muted,
                  values: buckets.map((b) => {
                    const v = b.by_sport[s];
                    if (!v) return null;
                    return metric === 'time' ? v.moving_s / 3600 : v.distance_m / scale;
                  }),
                })),
              },
            ]}
          />
        </>
      )}
    </Card>
  );
}

const DURATION_LABEL = (s: number) => (s < 60 ? `${s} s` : s < 3600 ? `${s / 60} min` : `${s / 3600} h`);

export function Curves({ range }: { range: Range }) {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const [kind, setKind] = useState<'speed' | 'power'>('power');
  const sport = kind === 'speed' ? ['run'] : undefined;
  const period = useQuery(curvesQuery(api, kind, range, sport));
  const all = useQuery(curvesQuery(api, kind, { from: '2000-01-01' }, sport));
  const show = (v: number) => (kind === 'power' ? `${Math.round(v)} W` : format.pace(v));
  const durations = [...new Set([...(all.data ?? []), ...(period.data ?? [])].map((p) => p.duration_s))].sort(
    (a, b) => a - b,
  );
  const at = (points: { duration_s: number; value: number }[] | undefined, d: number) =>
    points?.find((p) => p.duration_s === d)?.value ?? null;
  const lines = [
    { label: t('stats.allTime'), color: c.muted, values: durations.map((d) => at(all.data, d)) },
    {
      label: t('stats.thisPeriod'),
      color: kind === 'power' ? c.ride : c.run,
      values: durations.map((d) => at(period.data, d)),
    },
  ];
  // Scaled to this period: a few all-time spikes (GPS, sprints) would flatten everything else.
  const values = lines.flatMap((l) => l.values).filter((v): v is number => v != null);
  const mine = lines[1]?.values.filter((v): v is number => v != null) ?? [];
  const domain: [number, number] = values.length
    ? [Math.min(...values) * 0.95, Math.max(...(mine.length ? mine : values)) * 1.15]
    : [0, 1];
  const layers: Layer[] = lines.map((l) => ({ kind: 'line', ...l, domain }));
  return (
    <Card>
      <CardTitle
        right={
          <Choice
            label={t('stats.curves')}
            value={kind}
            onChange={setKind}
            options={[
              { value: 'power', label: t('stats.power') },
              { value: 'speed', label: t('stats.pace') },
            ]}
          />
        }
      >
        {t('stats.curves')}
      </CardTitle>
      {durations.length > 1 ? (
        <>
          <Chart
            legendGap={18}
            days={durations.map(DURATION_LABEL)}
            height={170}
            label={
              (period.data ?? []).map((p) => `${DURATION_LABEL(p.duration_s)} ${show(p.value)}`).join(', ') ||
              t('stats.empty')
            }
            layers={layers}
          />
          {/* The values the line cannot label: a few durations with this period's best. */}
          <View style={ui.stats}>
            {durations
              .filter((d) => [60, 300, 1200, 3600].includes(d))
              .map((d) => (
                <Stat
                  key={d}
                  label={DURATION_LABEL(d)}
                  value={at(period.data, d) != null ? show(at(period.data, d) as number) : '–'}
                />
              ))}
          </View>
        </>
      ) : (
        <Muted>{t('stats.empty')}</Muted>
      )}
    </Card>
  );
}

/**
 * The period's bests (running): each distance's fastest time in it, opening Best times on that
 * distance. The records of all time, their progression and the top 10 live there.
 */
export function Records({ range }: { range: Range }) {
  const api = useApi();
  const c = useTheme();
  const records = useQuery(recordsQuery(api, range));
  const format = useFormat();
  if (!records.data?.all_time.length) return null;
  const bests = records.data.period.filter((r) => effortShown(r.distance_key, format.unit));
  return (
    <Card>
      <CardTitle>{t('stats.periodBests')}</CardTitle>
      {!bests.length && <Muted>{t('stats.noPeriodBests')}</Muted>}
      {bests.map((r) => (
        <View key={r.distance_key} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 4 }}>
          <Text
            accessibilityRole="button"
            onPress={() => router.push({ pathname: '/bests', params: { key: r.distance_key } })}
            style={{ flex: 1, color: c.fg }}
          >
            {effortName(r.distance_key)}
          </Text>
          <Text style={{ textAlign: 'right', fontVariant: ['tabular-nums'] }}>
            <ActivityLink id={r.activity_id}>{formatDuration(r.time_s)}</ActivityLink>
            <Text style={{ color: c.muted, fontSize: 11 }}> {shortDay(r.day)}</Text>
          </Text>
        </View>
      ))}
      <Pressable
        accessibilityRole="link"
        onPress={() => router.push('/bests')}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 8 }}
      >
        <Text style={{ color: c.accent, fontWeight: '700' }}>{t('stats.allBests')}</Text>
        <Icon name="chevron_right" color={c.accent} size={20} />
      </Pressable>
    </Card>
  );
}

/** The days of a (leap) year written short, once: the same for every year's line. */
let yearDays: string[] | null = null;
const YEAR_DAYS = () =>
  (yearDays ??= Array.from({ length: 366 }, (_, i) =>
    formatDate(new Date(2024, 0, 1 + i), locale, { day: 'numeric', month: 'short' }),
  ));

/**
 * Every year's distance, cumulative through it, filling the screen it is on: the chart as tall as the
 * room the years' list leaves under it. A tap on a year (`onSelect`) makes it stand out.
 */
export function Progress({ selected, onSelect }: { selected: number; onSelect?: (year: number) => void }) {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const lines = useQuery(progressQuery(api));
  const [room, setRoom] = useState(0);
  if (!lines.data?.length) return null;
  const scale = format.unit === 'km' ? 1000 : 1609.344;
  const top = Math.max(1, ...lines.data.map((l) => l.cumulative_m.at(-1) ?? 0)) / scale;
  const sorted = [...lines.data].sort((a, b) => (a.year === selected ? 1 : b.year === selected ? -1 : 0));
  const colours = yearColours(
    lines.data.map((l) => l.year),
    selected,
    c,
  );
  // The chart takes the room the years' list leaves; Chart adds 16 for the dates under it and the
  // legend's gap (the legend itself is empty: the years are listed below).
  const chartHeight = Math.max(160, room - 16 - 8);
  return (
    <Card style={{ flex: 1, marginBottom: 0 }}>
      <View style={{ flex: 1 }} onLayout={(e) => setRoom(e.nativeEvent.layout.height)}>
        {room > 0 && (
          <Chart
            legendGap={8}
            days={YEAR_DAYS()}
            height={chartHeight}
            label={lines.data
              .map((l) => `${l.year}: ${Math.round((l.cumulative_m.at(-1) ?? 0) / scale)} ${format.unit}`)
              .join(', ')}
            layers={sorted.map((l) => ({
              kind: 'line',
              label: String(l.year),
              color: colours.get(l.year) ?? c.muted,
              values: l.cumulative_m.map((m) => m / scale),
              domain: [0, top * 1.05] as [number, number],
              width: l.year === selected ? 3 : 1.6,
              opacity: l.year === selected ? 1 : 0.5,
              hideLegend: true, // the years and their totals are listed under the chart
            }))}
          />
        )}
      </View>
      {lines.data.map((l) => (
        <Pressable
          key={l.year}
          accessibilityRole={onSelect ? 'button' : undefined}
          accessibilityState={onSelect ? { selected: l.year === selected } : undefined}
          disabled={!onSelect}
          onPress={() => onSelect?.(l.year)}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: onSelect ? 6 : 0 }}
        >
          <View
            style={{
              width: 10,
              height: 10,
              borderRadius: 5,
              backgroundColor: colours.get(l.year),
              opacity: l.year === selected ? 1 : 0.5,
            }}
          />
          <Text
            style={{
              flex: 1,
              color: c.fg,
              fontWeight: l.year === selected ? '700' : '400',
            }}
          >
            {l.year}
          </Text>
          <Text style={{ color: c.fg, fontVariant: ['tabular-nums'] }}>
            {Math.round((l.cumulative_m.at(-1) ?? 0) / scale).toLocaleString(locale)} {format.unit}
          </Text>
        </Pressable>
      ))}
    </Card>
  );
}

export function Efficiency({ range }: { range: Range }) {
  const api = useApi();
  const c = useTheme();
  const points = useQuery(efficiencyQuery(api, range));
  if (!points.data || points.data.length < 3) return null;
  const values = points.data.map((p) => p.efficiency);
  const third = Math.ceil(values.length / 3);
  const mean = (v: number[]) => v.reduce((a, b) => a + b, 0) / v.length;
  const change =
    ((mean(values.slice(-third)) - mean(values.slice(0, third))) / mean(values.slice(0, third))) * 100;
  return (
    <Card>
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 12,
        }}
      >
        <Heading first>{t('stats.efficiency')}</Heading>
        <Text
          style={[ui.statValue, { color: c.fg }]}
        >{`${change > 0 ? '+' : ''}${change.toFixed(1)} %`}</Text>
      </View>
      <Chart
        legendGap={18}
        days={[]}
        height={120}
        label={t('stats.efficiency')}
        layers={[{ kind: 'line', label: t('stats.efficiency'), color: c.run, values, hideLegend: true }]}
      />
      <Muted>{t('stats.efficiencyNote')}</Muted>
    </Card>
  );
}

const COMPARED = ['effort', 'grade_adjusted_distance', 'calories'] as const;

/** How our metrics compare with what Strava computed, for people who imported an export (OS-323). */
export function CompareWithStrava() {
  const api = useApi();
  const c = useTheme();
  const comparison = useQuery(stravaComparisonQuery(api));
  const [metric, setMetric] = useState<(typeof COMPARED)[number]>('effort');
  const data = comparison.data;
  if (!data || data.metrics.every((m) => m.count < 3)) return null;
  const current = data.metrics.find((m) => m.metric === metric);
  const number = (v: number | null | undefined, digits = 2) =>
    v == null ? '–' : v.toLocaleString(locale, { maximumFractionDigits: digits });
  return (
    <Card>
      <CardTitle info={t('compare.help')}>{t('compare.title')}</CardTitle>
      <Choice
        label={t('compare.metric')}
        value={metric}
        onChange={setMetric}
        options={COMPARED.map((m) => ({ value: m, label: t(`compare.${m}`) }))}
      />
      {current && current.count >= 3 ? (
        <>
          <View style={ui.stats}>
            <Stat labelFirst label={t('compare.count')} value={String(current.count)} />
            <Stat labelFirst label="R²" value={number(current.r2, 3)} />
            <Stat labelFirst label={t('compare.mae')} value={number(current.mae, 1)} />
            <Stat labelFirst label={t('compare.ratio')} value={number(current.ratio, 3)} />
          </View>
          <Text style={{ color: c.fg, fontWeight: '700' }}>{t('compare.largest')}</Text>
          {current.largest.map((p) => (
            <View
              key={p.activity_id}
              style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 2 }}
            >
              <ActivityLink id={p.activity_id}>{t('compare.activity', { id: p.activity_id })}</ActivityLink>
              <Text style={{ color: c.fg, fontVariant: ['tabular-nums'] }}>
                Zankup {number(p.ours, 0)} · Strava {number(p.strava, 0)}
              </Text>
            </View>
          ))}
        </>
      ) : (
        <Muted>{t('compare.notEnough')}</Muted>
      )}
    </Card>
  );
}

export const sportColour = (c: ReturnType<typeof useTheme>, sport: string) =>
  (c as unknown as Record<string, string>)[asSport(sport) as Sport] ?? c.muted;

/**
 * "Now" (OS-919, from the PWA): today's form (TSB) with what it means and the fitness and fatigue
 * behind it, and this week's moving time against the average of the previous 12 weeks.
 */
