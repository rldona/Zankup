/**
 * A goal over time (OS-1005), as the app's fold on its goals list: this period's totals for the goal's
 * sport, then the goal over the last 12 weeks or months (each one's value against the target), or the
 * year's running total month by month against the target's diagonal. Hovering reads a week or month.
 */
import {
  chartPeriods,
  formatDate,
  isoDay,
  metricOf,
  runningTotal,
  volumeQuery,
  type Bucket,
  type GoalMetric,
  type GoalOut,
  type GoalPeriod,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { api } from '../../api';
import { EChart } from '../../charts/EChart';
import { locale, t } from '../../i18n';
import { Bone, Pulse } from '../../Skeleton';
import { useResolvedTheme } from '../../theme';
import { goalValue, palette, type Format } from './data';
import '../more.css';

export function GoalHistory({ goal, format }: { goal: GoalOut; format: Format }) {
  const theme = useResolvedTheme();
  const period = goal.period as GoalPeriod;
  const metric = goal.metric as GoalMetric;
  const [today] = useState(() => isoDay(new Date()));
  const range = chartPeriods(period, today);
  const volume = useQuery(volumeQuery(api, { from: range.from, to: range.to }, range.unit));
  const buckets = useMemo(() => (volume.data ?? []) as Bucket[], [volume.data]);
  const year = period === 'year';

  const option = useMemo(() => {
    const c = palette(theme);
    const of = (b: Bucket) => (goal.sport ? b.by_sport[goal.sport] : b.totals);
    const values = buckets.map((b) => metricOf(of(b), metric));
    const shown = year ? runningTotal(values) : values;
    const when = (start: string, long = false) =>
      formatDate(
        new Date(`${start}T12:00:00`),
        locale,
        long ? { day: 'numeric', month: 'short' } : { month: 'short' },
      ).replace('.', '');
    // A year has its twelve months whether or not they have begun; the weeks and months are those fetched.
    const labels = year
      ? Array.from({ length: 12 }, (_, m) =>
          formatDate(new Date(Number(today.slice(0, 4)), m, 15), locale, { month: 'short' }).replace('.', ''),
        )
      : buckets.map((b, i) =>
          i === 0 || b.start.slice(5, 7) !== buckets[i - 1]?.start.slice(5, 7) ? when(b.start) : '',
        );
    const last = shown.length - 1;
    const halo = (value: number) => [
      {
        type: 'scatter',
        silent: true,
        tooltip: { show: false },
        data: [[last, value]],
        symbolSize: 28,
        itemStyle: { color: c.accent, opacity: 0.3 },
        z: 4,
      },
    ];
    return {
      grid: { left: 14, right: 14, top: 18, bottom: 26 },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { type: 'line', lineStyle: { color: c.line } },
        formatter: (params: { dataIndex: number }[]) => {
          // A year's line starts at zero, before its first month.
          const i = (params[0]?.dataIndex ?? 0) - (year ? 1 : 0);
          const bucket = buckets[i];
          if (!bucket) return '';
          const head = year
            ? formatDate(new Date(`${bucket.start}T12:00:00`), locale, { month: 'long' })
            : when(bucket.start, true);
          const target = year ? (goal.target * (i + 1)) / 12 : goal.target;
          return `${head}<br>${t('progress.goalActual')}: <b>${goalValue(metric, shown[i] ?? 0, format)}</b><br>${t('progress.goalTarget')}: ${goalValue(metric, target, format)}`;
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: !year,
        data: year ? ['', ...labels] : labels,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: c.line } },
        axisLabel: { color: c.fg, interval: 0 },
        splitLine: { show: true, lineStyle: { color: c.line } },
      },
      yAxis: {
        type: 'value',
        show: false,
        min: 0,
        max: Math.max(goal.target, ...shown, 1),
      },
      series: year
        ? [
            {
              type: 'line',
              silent: true,
              symbol: 'none',
              data: [0, ...Array.from({ length: 12 }, (_, m) => (goal.target * (m + 1)) / 12)],
              lineStyle: { color: c.muted, width: 2 },
              tooltip: { show: false },
            },
            {
              type: 'line',
              symbol: 'none',
              data: [0, ...shown],
              lineStyle: { color: c.accent, width: 3, join: 'round' },
              itemStyle: { color: c.accent },
              z: 3,
            },
            ...(last >= 0
              ? [
                  { ...halo(shown[last] ?? 0)[0], data: [[last + 1, shown[last]]] },
                  {
                    type: 'scatter',
                    silent: true,
                    tooltip: { show: false },
                    data: [[last + 1, shown[last]]],
                    symbolSize: 14,
                    itemStyle: { color: c.accent },
                    z: 5,
                  },
                ]
              : []),
          ]
        : [
            {
              type: 'scatter',
              data: shown.map(() => goal.target),
              symbolSize: 12,
              itemStyle: { color: c.muted },
            },
            {
              type: 'scatter',
              data: shown.map((v, i) => (i === last ? { value: v, symbolSize: 14 } : v)),
              symbolSize: 12,
              itemStyle: { color: c.accent },
              z: 3,
            },
            ...(last >= 0 ? halo(shown[last] ?? 0) : []),
          ],
    };
  }, [buckets, goal, metric, year, theme, format, today]);

  // The total so far this period: its week or month, or the whole year.
  const of = (b: Bucket) => (goal.sport ? b.by_sport[goal.sport] : b.totals);
  const now = buckets.at(-1);
  const totals = year ? buckets.map(of) : [now ? of(now) : undefined];
  const sum = (m: GoalMetric) => totals.reduce((s, x) => s + metricOf(x, m), 0);
  const heading =
    period === 'week'
      ? t('progress.last12Weeks')
      : period === 'month'
        ? t('progress.last12Months')
        : t('goals.period.year');

  return (
    <div className="goalhistory">
      <h3>{heading}</h3>
      {volume.isPending ? (
        <Pulse>
          <Bone width="70%" />
          <Bone height={150} radius={10} style={{ marginTop: 12 }} />
        </Pulse>
      ) : (
        <>
          <p className="goalhistorytotals num">
            {[
              format.distance(sum('distance')),
              goalValue('time', sum('time'), format),
              format.elevation(sum('ascent')),
              t('progress.activitiesCount', { count: sum('count') }),
            ].join(' · ')}
          </p>
          <div className="goalhistorykey">
            <span>{t(`progress.metricShort.${metric}`)}</span>
            <span>
              <i className="target" aria-hidden="true" />
              {t('progress.goalTarget')}
              <i className="actual" aria-hidden="true" />
              {t('progress.goalActual')}
            </span>
          </div>
          {buckets.length > 0 && (
            <EChart
              option={option}
              theme={theme}
              height={150}
              summary={`${heading}: ${goalValue(metric, sum(metric), format)} / ${goalValue(metric, goal.target, format)}`}
            />
          )}
        </>
      )}
    </div>
  );
}
