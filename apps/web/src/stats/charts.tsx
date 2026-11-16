import {
  commonTokens,
  formatHoursMinutes,
  SPORTS,
  themes,
  type CurvePoint,
  type FitnessDay,
  type Sport,
  type ThemeName,
  type VolumeBucket,
  type YearLine,
  tightScale,
  yearColours,
} from '@zankup/core';
import type { EChartsType } from 'echarts/core';
import { useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router';

import { EChart } from '../charts/EChart';
import { locale, t } from '../i18n';

const palette = (theme: ThemeName) => ({ ...commonTokens, ...themes[theme] });

/** Clicking a point that carries an activity opens it (OS-320). */
function useOpenActivity() {
  const navigate = useNavigate();
  return useCallback(
    (chart: EChartsType) => {
      const open = (params: { data?: unknown }) => {
        const data = params.data as { activityId?: number } | undefined;
        if (data?.activityId) void navigate(`/activities/${data.activityId}`);
      };
      chart.on('click', open as never);
      return () => chart.off('click');
    },
    [navigate],
  );
}

/** Fitness, fatigue and form per day (OS-303, OS-320). */
export function FitnessChart({ days, theme }: { days: FitnessDay[]; theme: ThemeName }) {
  const c = palette(theme);
  const option = useMemo(
    () => ({
      grid: { left: 40, right: 40, top: 30, bottom: 26 },
      legend: { top: 0, data: [t('stats.ctl'), t('stats.atl'), t('stats.tsb')] },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: days.map((d) => d.day), axisLabel: { hideOverlap: true } },
      yAxis: [{ type: 'value' }, { type: 'value', splitLine: { show: false } }],
      series: [
        {
          name: t('stats.ctl'),
          type: 'line',
          symbol: 'none',
          data: days.map((d) => d.ctl.toFixed(1)),
          lineStyle: { color: c.fitness, width: 2 },
          itemStyle: { color: c.fitness },
        },
        {
          name: t('stats.atl'),
          type: 'line',
          symbol: 'none',
          data: days.map((d) => d.atl.toFixed(1)),
          lineStyle: { color: c.fatigue, width: 1.5 },
          itemStyle: { color: c.fatigue },
        },
        {
          name: t('stats.tsb'),
          type: 'bar',
          yAxisIndex: 1,
          data: days.map((d) => ({
            value: d.tsb.toFixed(1),
            itemStyle: { color: d.tsb >= 0 ? c.formPositive : c.formNegative },
          })),
          barWidth: '60%',
        },
      ],
    }),
    [days, c],
  );
  return (
    <EChart
      option={option}
      theme={theme}
      height={260}
      summary={
        days.length
          ? `${t('stats.ctl')} ${days.at(-1)?.ctl.toFixed(0)}, ${t('stats.atl')} ${days.at(-1)?.atl.toFixed(0)}, ${t('stats.tsb')} ${days.at(-1)?.tsb.toFixed(0)}`
          : t('stats.empty')
      }
    />
  );
}

const sportOrder = (s: string) => (SPORTS as readonly string[]).indexOf(s);

/** Volume per bucket, stacked by sport, in hours or distance (OS-308, OS-320). */
export function VolumeChart({
  buckets,
  metric,
  unit,
  theme,
  distanceUnit,
}: {
  buckets: VolumeBucket[];
  metric: 'time' | 'distance';
  unit: 'day' | 'week' | 'month' | 'year';
  theme: ThemeName;
  distanceUnit: 'km' | 'mi';
}) {
  const c = palette(theme);
  const sports = useMemo(
    () =>
      [...new Set(buckets.flatMap((b) => Object.keys(b.by_sport)))].sort(
        (a, b) => sportOrder(a) - sportOrder(b),
      ),
    [buckets],
  );
  const scale = distanceUnit === 'km' ? 1000 : 1609.344;
  const option = useMemo(() => {
    const label = (start: string) => {
      const d = new Date(`${start}T12:00:00`);
      if (unit === 'year') return String(d.getFullYear());
      if (unit === 'month') return d.toLocaleDateString(locale, { month: 'short', year: '2-digit' });
      return d.toLocaleDateString(locale, { day: 'numeric', month: 'short' });
    };
    return {
      grid: { left: 44, right: 12, top: 30, bottom: 26 },
      legend: {
        top: 0,
        data: sports.map((s) =>
          t(`sport.${(SPORTS as readonly string[]).includes(s) ? (s as Sport) : 'other'}`),
        ),
      },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: buckets.map((b) => label(b.start)) },
      yAxis: { type: 'value' },
      series: sports.map((s) => ({
        name: t(`sport.${(SPORTS as readonly string[]).includes(s) ? (s as Sport) : 'other'}`),
        type: 'bar',
        stack: 'v',
        itemStyle: { color: (c as Record<string, string>)[s] ?? c.muted },
        data: buckets.map((b) => {
          const v = b.by_sport[s];
          if (!v) return 0;
          return metric === 'time' ? +(v.moving_s / 3600).toFixed(1) : +(v.distance_m / scale).toFixed(1);
        }),
      })),
    };
  }, [buckets, sports, metric, scale, c, unit]);
  const total = buckets.reduce(
    (s, b) => s + (metric === 'time' ? b.totals.moving_s : b.totals.distance_m),
    0,
  );
  return (
    <EChart
      option={option}
      theme={theme}
      height={240}
      summary={
        metric === 'time' ? formatHoursMinutes(total) : `${(total / scale).toFixed(0)} ${distanceUnit}`
      }
    />
  );
}

const DURATION_LABEL = (s: number) => (s < 60 ? `${s} s` : s < 3600 ? `${s / 60} min` : `${s / 3600} h`);

/** A best-effort curve for the period against all time; a point opens its activity (OS-315, OS-320). */
export function CurveChart({
  period,
  allTime,
  theme,
  format,
  colour,
}: {
  period: CurvePoint[];
  allTime: CurvePoint[];
  theme: ThemeName;
  format: (value: number) => string;
  colour: string;
}) {
  const c = palette(theme);
  const open = useOpenActivity();
  const durations = useMemo(
    () => [...new Set([...allTime, ...period].map((p) => p.duration_s))].sort((a, b) => a - b),
    [allTime, period],
  );
  const option = useMemo(() => {
    const points = (curve: CurvePoint[]) =>
      durations.map((d) => {
        const p = curve.find((x) => x.duration_s === d);
        return p ? { value: p.value, activityId: p.activity_id, day: p.day } : null;
      });
    return {
      grid: { left: 56, right: 12, top: 30, bottom: 26 },
      legend: { top: 0, data: [t('stats.thisPeriod'), t('stats.allTime')] },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (v: number) => (v == null ? '—' : format(v)),
      },
      xAxis: { type: 'category', data: durations.map(DURATION_LABEL) },
      yAxis: { type: 'value', scale: true, axisLabel: { formatter: (v: number) => format(v) } },
      series: [
        {
          name: t('stats.allTime'),
          type: 'line',
          data: points(allTime),
          lineStyle: { color: c.line, width: 3 },
          itemStyle: { color: c.muted },
          symbolSize: 6,
        },
        {
          name: t('stats.thisPeriod'),
          type: 'line',
          data: points(period),
          lineStyle: { color: colour, width: 2 },
          itemStyle: { color: colour },
          symbolSize: 7,
        },
      ],
    };
  }, [durations, period, allTime, c, colour, format]);
  return (
    <EChart
      option={option}
      theme={theme}
      height={240}
      onReady={open}
      summary={
        period.map((p) => `${DURATION_LABEL(p.duration_s)} ${format(p.value)}`).join(', ') || t('stats.empty')
      }
    />
  );
}

/**
 * Cumulative distance through each year, the selected one highlighted (OS-322); a click on a line picks
 * its year (`onSelect`). The axis ends at the tightest round value, so the lines fill the chart.
 */
export function ProgressChart({
  lines,
  selected,
  theme,
  distanceUnit,
  height = 260,
  className,
  onSelect,
}: {
  lines: YearLine[];
  selected: number;
  theme: ThemeName;
  distanceUnit: 'km' | 'mi';
  height?: number | string;
  className?: string;
  onSelect?: (year: number) => void;
}) {
  const c = palette(theme);
  const scale = distanceUnit === 'km' ? 1000 : 1609.344;
  const option = useMemo(() => {
    const colours = yearColours(
      lines.map((l) => l.year),
      selected,
      c,
    );
    const top = Math.max(1, ...lines.map((l) => (l.cumulative_m.at(-1) ?? 0) / scale));
    const axis = tightScale([0, top]);
    return {
      grid: { left: 48, right: 12, top: 12, bottom: 26 },
      legend: { show: false }, // the years and their totals are listed under the chart
      tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v} ${distanceUnit}` },
      xAxis: {
        type: 'category',
        data: Array.from({ length: 366 }, (_, i) =>
          new Date(2024, 0, i + 1).toLocaleDateString(locale, { day: 'numeric', month: 'short' }),
        ),
        axisLabel: { interval: 30 },
      },
      yAxis: {
        type: 'value',
        min: axis.domain[0],
        max: axis.domain[1],
        interval: (axis.domain[1] - axis.domain[0]) / Math.max(1, axis.ticks.length - 1),
      },
      series: lines.map((line) => ({
        name: String(line.year),
        type: 'line',
        symbol: 'none',
        triggerLineEvent: true,
        data: line.cumulative_m.map((m) => Math.round(m / scale)),
        // The other years in their own colours, but faint, so the selected one stands out.
        lineStyle: {
          width: line.year === selected ? 3 : 1.6,
          color: colours.get(line.year),
          opacity: line.year === selected ? 1 : 0.45,
        },
        itemStyle: { color: colours.get(line.year), opacity: line.year === selected ? 1 : 0.45 },
        z: line.year === selected ? 3 : 2,
      })),
    };
  }, [lines, selected, c, scale, distanceUnit]);
  const pick = useCallback(
    (chart: EChartsType) => {
      if (!onSelect) return undefined;
      const select = (params: { seriesName?: string }) => {
        const year = Number(params.seriesName);
        if (year) onSelect(year);
      };
      chart.on('click', select as never);
      return () => chart.off('click');
    },
    [onSelect],
  );
  return (
    <EChart
      option={option}
      theme={theme}
      height={height}
      className={className}
      onReady={pick}
      summary={lines
        .map((l) => `${l.year}: ${Math.round((l.cumulative_m.at(-1) ?? 0) / scale)} ${distanceUnit}`)
        .join(', ')}
    />
  );
}

/** Aerobic efficiency per activity (OS-317): speed per heartbeat, better when it climbs. */
export function EfficiencyChart({
  points,
  theme,
}: {
  points: { day: string; activity_id: number; efficiency: number }[];
  theme: ThemeName;
}) {
  const c = palette(theme);
  const open = useOpenActivity();
  const option = useMemo(
    () => ({
      grid: { left: 52, right: 14, top: 14, bottom: 26 },
      tooltip: { trigger: 'axis', valueFormatter: (v: number) => v.toFixed(2) },
      xAxis: { type: 'category', data: points.map((p) => p.day), axisLabel: { hideOverlap: true } },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: { formatter: (v: number) => v.toFixed(1) },
      },
      series: [
        {
          name: t('stats.efficiency'),
          type: 'line',
          data: points.map((p) => ({ value: p.efficiency, activityId: p.activity_id })),
          lineStyle: { color: c.accent, width: 2 },
          itemStyle: { color: c.accent },
          symbolSize: 6,
        },
      ],
    }),
    [points, c],
  );
  return (
    <EChart
      option={option}
      theme={theme}
      height={240}
      onReady={open}
      summary={points.map((p) => `${p.day} ${p.efficiency.toFixed(2)}`).join(', ')}
    />
  );
}

/** Ours against Strava's for one metric (OS-323): each point opens its activity. */
export function ComparisonChart({
  points,
  theme,
  label,
}: {
  points: { activity_id: number; ours: number; strava: number }[];
  theme: ThemeName;
  label: string;
}) {
  const c = palette(theme);
  const open = useOpenActivity();
  const option = useMemo(() => {
    // Both axes end at the same tightest round value, so the diagonal is the chart's.
    const axis = tightScale([0, Math.max(...points.flatMap((p) => [p.ours, p.strava]), 1)]);
    const max = axis.domain[1];
    const interval = (axis.domain[1] - axis.domain[0]) / Math.max(1, axis.ticks.length - 1);
    return {
      grid: { left: 48, right: 16, top: 16, bottom: 36 },
      tooltip: {
        trigger: 'item',
        formatter: (p: { data: { value: number[] } }) =>
          `Strava ${p.data.value[0]} · Zankup ${p.data.value[1]}`,
      },
      xAxis: { type: 'value', name: 'Strava', nameLocation: 'middle', nameGap: 24, min: 0, max, interval },
      yAxis: { type: 'value', name: 'Zankup', min: 0, max, interval },
      series: [
        {
          type: 'scatter',
          symbolSize: 6,
          itemStyle: { color: c.accent, opacity: 0.6 },
          data: points.map((p) => ({ value: [p.strava, p.ours], activityId: p.activity_id })),
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: c.muted, type: 'dashed' },
            data: [[{ coord: [0, 0] }, { coord: [max, max] }]],
          },
        },
      ],
    };
  }, [points, c.accent, c.muted]);
  return <EChart option={option} theme={theme} summary={label} height={560} onReady={open} />;
}
