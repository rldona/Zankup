import {
  chartValue,
  commonTokens,
  formatDuration,
  nearestIndex,
  KIND_COLOURS,
  rangeOf,
  sampleIndices,
  themes,
  xAxis,
  type ActivityChart,
  type Series,
  type ThemeName,
} from '@zankup/core';
import type { EChartsType } from 'echarts/core';
import { useCallback, useEffect, useMemo, useRef } from 'react';

import { EChart } from '../charts/EChart';
import { locale, t } from '../i18n';
import { CardTitle } from '../ui';

const MAX_POINTS = 1000;
const METRES_PER_MILE = 1609.344;

/** An axis from `lo` to `hi` in round steps (about three), so its labels read cleanly: 0, 100, 200, 300. */
function niceRange([lo, hi]: [number, number]): [number, number, number] {
  const raw = (hi - lo) / 3 || 1;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = ([1, 2, 2.5, 5, 10].find((m) => m * power >= raw) ?? 10) * power;
  return [Math.floor(lo / step) * step, Math.ceil(hi / step) * step, step];
}

/** Room on the left for the axis labels. */
const LEFT = 48;

/**
 * One of the activity's charts on its own card (OS-907), as the app's: its heading, the chart and a
 * footer of centred figures. Hovering shows the value by the cursor (and moves the map's); a click pins
 * it there, and another click hides it. With "Section" on, dragging picks a stretch, shaded on every
 * card and on the map. With `backdrop` (the altitude), the elevation in grey behind the line, as
 * Strava's (OS-1007).
 */
export function ChartCard({
  chart,
  series,
  units,
  theme,
  section,
  selecting,
  onHover,
  onSection,
  backdrop,
}: {
  chart: ActivityChart;
  series: Series;
  units: 'metric' | 'imperial';
  theme: ThemeName;
  section: [number, number] | null;
  selecting: boolean;
  onHover: (index: number | null) => void;
  onSection: (range: [number, number] | null) => void;
  /** The altitude, one value per sample, drawn in grey behind the chart's own line. */
  backdrop?: (number | null)[];
}) {
  const axis = useMemo(() => xAxis(series), [series]);
  const byDistance = axis.kind === 'dist';
  const scale = byDistance ? (units === 'metric' ? 1000 : METRES_PER_MILE) : 60;
  const unitLabel = units === 'metric' ? 'km' : 'mi';
  const kept = useMemo(() => sampleIndices(series.t.length, MAX_POINTS), [series]);
  const tokens = useMemo(() => ({ ...commonTokens, ...themes[theme] }), [theme]);
  const colour =
    chart.colour in KIND_COLOURS
      ? KIND_COLOURS[chart.colour as keyof typeof KIND_COLOURS]
      : tokens[chart.colour as 'muted' | 'zone4'];

  // The elevation fills about four fifths of the chart's height, from a little under its lowest point.
  const backdropRange = useMemo((): [number, number] | null => {
    // A loop, not Math.min(...): a long activity has more samples than a call takes arguments.
    let lo = Infinity;
    let hi = -Infinity;
    for (const v of backdrop ?? []) {
      if (v == null) continue;
      if (v < lo) lo = v;
      if (v > hi) hi = v;
    }
    if (!Number.isFinite(lo) || !Number.isFinite(hi)) return null;
    const span = hi - lo || 1;
    return [lo - span * 0.05, hi + span * 0.25];
  }, [backdrop]);

  const option = useMemo(() => {
    const x = (i: number) => (axis.values[i] ?? 0) / scale;
    const [min, max, step] = niceRange(rangeOf(chart.values, !!chart.fromZero));
    const where = (index: number) => {
      const time = formatDuration(series.t[index] ?? 0);
      const dist = series.dist?.[index];
      return byDistance && dist != null
        ? `${(dist / scale).toLocaleString(locale, { maximumFractionDigits: 1 })} ${unitLabel} · ${time}`
        : time;
    };
    return {
      grid: { left: LEFT, right: 14, top: 10, bottom: 28 },
      toolbox: { show: false },
      brush: { xAxisIndex: 0, brushType: 'lineX', brushMode: 'single', transformable: false },
      tooltip: {
        // While picking a section the drag is for the section, not for reading values.
        show: !selecting,
        trigger: 'axis',
        triggerOn: 'mousemove',
        confine: true,
        axisPointer: { type: 'line', lineStyle: { color: tokens.fg, width: 1 } },
        backgroundColor: tokens.surface2,
        borderColor: tokens.line,
        textStyle: { color: tokens.fg },
        formatter: (params: { seriesId?: string; data: [number, number | null] }[]) => {
          const point = (params.find((p) => p.seriesId === 'value') ?? params[0])?.data;
          if (!point || point[1] == null) return '';
          const index = nearestIndex(axis.values, point[0] * scale);
          return `<b>${chartValue(chart, point[1], locale)}</b><br/><span style="opacity:.7">${where(index)}</span>`;
        },
      },
      xAxis: {
        type: 'value',
        min: 0,
        max: x(axis.values.length - 1),
        axisLabel: {
          formatter: (v: number) => (byDistance ? `${Math.round(v)} ${unitLabel}` : formatDuration(v * 60)),
          hideOverlap: true,
        },
        splitLine: { show: false },
      },
      yAxis: [
        {
          type: 'value',
          inverse: !!chart.inverted,
          min,
          max,
          interval: step,
          axisLabel: {
            formatter: (v: number) =>
              chart.readout.kind === 'pace'
                ? `${Math.floor(v)}:${String(Math.round((v % 1) * 60)).padStart(2, '0')}`
                : v.toLocaleString(locale),
            hideOverlap: true,
          },
          splitLine: { lineStyle: { color: tokens.line } },
        },
        // The elevation's own scale, hidden: its lowest point at the bottom, its highest near the top.
        { type: 'value', show: false, scale: true, min: backdropRange?.[0], max: backdropRange?.[1] },
      ],
      series: [
        {
          // Stable ids, so a new option (a section, a theme) morphs the lines instead of redrawing them.
          id: 'value',
          type: 'line',
          z: 3,
          symbol: 'none',
          sampling: 'lttb',
          connectNulls: false,
          data: kept.map((i) => [x(i), chart.values[i] ?? null]),
          lineStyle: { width: 1.6, color: colour },
          // A soft fill under the line, from the axis (or from the top for a pace, drawn upside down).
          areaStyle: { color: colour, opacity: 0.14, origin: chart.inverted ? 'end' : 'start' },
          markArea: section
            ? {
                silent: true,
                itemStyle: { color: tokens.fg, opacity: 0.08 },
                data: [[{ xAxis: x(section[0]) }, { xAxis: x(section[1]) }]],
              }
            : undefined,
        },
        ...(backdrop && backdropRange
          ? [
              {
                id: 'elevation',
                type: 'line',
                yAxisIndex: 1,
                z: 1,
                silent: true,
                symbol: 'none',
                sampling: 'lttb',
                data: kept.map((i) => [x(i), backdrop[i] ?? null]),
                lineStyle: { width: 0 },
                areaStyle: { color: tokens.muted, opacity: 0.22, origin: 'start' },
                tooltip: { show: false },
              },
            ]
          : []),
      ],
    };
  }, [
    axis,
    scale,
    byDistance,
    unitLabel,
    series,
    chart,
    kept,
    tokens,
    colour,
    section,
    selecting,
    backdrop,
    backdropRange,
  ]);

  const handlers = useRef({ onHover, onSection });
  useEffect(() => {
    handlers.current = { onHover, onSection };
  });
  // A click pins the tooltip where it is; the next click (on it or anywhere on the chart) hides it.
  const pinned = useRef(false);

  const ready = useCallback(
    (instance: EChartsType) => {
      instance.dispatchAction({
        type: 'takeGlobalCursor',
        key: 'brush',
        brushOption: selecting ? { brushType: 'lineX', brushMode: 'single' } : { brushType: false },
      });
      const toIndex = (v: number) => nearestIndex(axis.values, v * scale);
      // Every option change draws the chart afresh, with its tooltip loose again.
      pinned.current = false;
      let shown: number | null = null;
      const pointer = (event: { axesInfo?: { axisDim: string; value: number }[] }) => {
        const info = event.axesInfo?.find((a) => a.axisDim === 'x');
        if (!info || pinned.current) return;
        const index = toIndex(info.value);
        if (index !== shown) {
          shown = index;
          handlers.current.onHover(index);
        }
      };
      const click = () => {
        if (selecting) return;
        if (pinned.current) {
          pinned.current = false;
          instance.setOption({ tooltip: { alwaysShowContent: false, triggerOn: 'mousemove' } });
          instance.dispatchAction({ type: 'hideTip' });
          instance.dispatchAction({ type: 'updateAxisPointer', currTrigger: 'leave' });
          shown = null;
          handlers.current.onHover(null);
        } else if (shown != null) {
          pinned.current = true;
          instance.setOption({ tooltip: { alwaysShowContent: true, triggerOn: 'none' } });
        }
      };
      const brushed = (event: { areas?: { coordRange?: number[] }[] }) => {
        const range = event.areas?.[0]?.coordRange;
        if (!range || range.length < 2) return;
        const [a, b] = [toIndex(range[0] ?? 0), toIndex(range[1] ?? 0)];
        handlers.current.onSection(b > a ? [a, b] : null);
        instance.dispatchAction({ type: 'brush', areas: [] }); // the shaded section stays as a markArea
      };
      const out = () => {
        if (pinned.current) return;
        shown = null;
        handlers.current.onHover(null);
      };
      instance.on('updateAxisPointer', pointer as never);
      instance.on('brushEnd', brushed as never);
      instance.getZr().on('click', click);
      instance.getZr().on('globalout', out);
      return () => {
        instance.off('updateAxisPointer');
        instance.off('brushEnd');
        instance.getZr().off('click', click);
        instance.getZr().off('globalout', out);
      };
    },
    [axis, scale, selecting],
  );

  return (
    <article className="panel chartcard">
      <CardTitle>{chart.label}</CardTitle>
      <EChart
        option={option}
        theme={theme}
        height={190}
        onReady={ready}
        summary={t('activity.chartSummary', {
          metric: chart.label,
          values: chart.footer.map(([label, value]) => `${label} ${value}`).join(', '),
        })}
      />
      {chart.footer.length > 0 && (
        <dl className="chartfoot num">
          {chart.footer.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </article>
  );
}
