/**
 * Volume over time as Strava's Progress draws it (the app's VolumeChart): a line over a solid fill, a
 * ring on each point when there are few, the axis on the right and months (or days) along the bottom;
 * optionally the range compared with as a second line. Hovering a point is the app's touch: `onHover`
 * gets its index (null when the pointer leaves), a click calls `onPick`, and the arrow keys move along
 * the points when the chart has the focus.
 */
import { commonTokens, themes, type ChartLabel } from '@zankup/core';
import type { EChartsType } from 'echarts/core';
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';

import { EChart } from '../../charts/EChart';
import { pointToIndex, resample } from '../../charts/resample';
import { useResolvedTheme } from '../../theme';

/** Where the real points are among the series (after the comparison and the line). */
const POINTS = 2;

/** The tick closest to an axis value (ECharts' steps can be a rounding away from ours). */
const nearest = (ticks: number[], v: number) =>
  ticks.reduce((best, t, i) => (Math.abs(t - v) < Math.abs((ticks[best] ?? 0) - v) ? i : best), 0);

export function VolumeChart({
  values,
  previous,
  ticks,
  tickLabel,
  labels,
  onHover,
  onPick,
  height = 230,
  summary,
}: {
  values: number[];
  /** The range compared with, drawn as a second line on the same scale. */
  previous?: number[] | null;
  /** The right axis, from zero up. */
  ticks: number[];
  tickLabel: (v: number) => string;
  /** Words under some points (their index). */
  labels: ChartLabel[];
  onHover: (index: number | null) => void;
  onPick?: (index: number) => void;
  height?: number;
  summary: string;
}) {
  const theme = useResolvedTheme();
  const c = useMemo(() => ({ ...commonTokens, ...themes[theme] }), [theme]);
  // Callers pass fresh arrays and functions on every render: the chart is drawn again only when what
  // it shows changes, so hovering (which re-renders the caller) does not redraw it. What it shows is
  // read back from its text, so the option below depends on nothing else.
  const signature = JSON.stringify([values, previous ?? null, labels, ticks, ticks.map(tickLabel)]);
  const option = useMemo(() => {
    const [shownValues, before, words, steps, tickText] = JSON.parse(signature) as [
      number[],
      number[] | null,
      ChartLabel[],
      number[],
      string[],
    ];
    const top = steps.at(-1) || 1;
    const wordAt = new Map(words.map((l) => [l.index, l.text]));
    const n = shownValues.length;
    return {
      grid: { left: 6, right: 6, top: 14, bottom: 26, containLabel: true },
      // Moved only by the pointer below, onto the real points.
      tooltip: {
        trigger: 'axis',
        triggerOn: 'none',
        showContent: false,
        axisPointer: { type: 'line', lineStyle: { color: c.fg, width: 2, type: 'solid' } },
      },
      xAxis: {
        // The points by their place, so the line can be drawn between them (see charts/resample).
        type: 'value',
        min: 0,
        max: Math.max(1, n - 1),
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: {
          customValues: words.map((l) => l.index),
          color: c.fg,
          fontSize: 12,
          formatter: (v: number) => wordAt.get(Math.round(v)) ?? '',
        },
      },
      yAxis: {
        type: 'value',
        position: 'right',
        min: 0,
        max: top,
        interval: steps[1] || top,
        axisLabel: { color: c.fg, fontSize: 12, formatter: (v: number) => tickText[nearest(steps, v)] ?? '' },
        splitLine: { lineStyle: { color: c.line } },
      },
      // Their own ids, in a fixed order (the pointer finds the points by it): the lines morph from one
      // range to another, the comparison comes and goes, and other points are drawn afresh.
      series: [
        {
          id: 'previous',
          type: 'line',
          data: before?.length ? resample(before.slice(0, n)) : [],
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.fatigue, width: 2 },
          emphasis: { disabled: true },
        },
        {
          id: 'value',
          type: 'line',
          data: resample(shownValues.map((v) => Math.min(v, top))),
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.accent, width: 3 },
          areaStyle: { color: c.accent, opacity: 0.3 },
          emphasis: { disabled: true },
        },
        {
          id: `points:${n}`,
          type: 'scatter',
          data: shownValues.map((v, i) => [i, Math.min(v, top)]),
          symbol: n <= 60 ? 'circle' : 'none',
          symbolSize: 9,
          itemStyle: { color: c.surface, borderColor: c.accent, borderWidth: 2 },
          emphasis: { scale: 1.8, itemStyle: { color: c.accent, borderColor: c.accent } },
        },
      ],
    };
  }, [signature, c]);

  // The handlers as they are now: the chart wires its events once per option.
  const handlers = useRef({ onHover, onPick });
  useEffect(() => {
    handlers.current = { onHover, onPick };
  });
  const chart = useRef<EChartsType | null>(null);
  const shown = useRef<number | null>(null);
  const [focus, setFocus] = useState<number | null>(null);

  const count = useRef(values.length);
  useEffect(() => {
    count.current = values.length;
  });
  const ready = useCallback((instance: EChartsType) => {
    chart.current = instance;
    const pointer = pointToIndex(instance, {
      count: () => count.current,
      seriesIndex: () => POINTS,
      onIndex: (index) => {
        shown.current = index;
        handlers.current.onHover(index);
      },
    });
    const click = () => {
      if (shown.current != null) handlers.current.onPick?.(shown.current);
    };
    instance.getZr().on('click', click);
    return () => {
      pointer();
      instance.getZr().off('click', click);
    };
  }, []);

  const key = (event: KeyboardEvent) => {
    if (!values.length) return;
    const last = values.length - 1;
    const from = focus ?? last;
    const moves: Record<string, number> = {
      ArrowLeft: Math.max(0, from - 1),
      ArrowRight: Math.min(last, from + 1),
      Home: 0,
      End: last,
    };
    if (event.key in moves) {
      event.preventDefault();
      const i = moves[event.key] ?? last;
      setFocus(i);
      shown.current = i;
      chart.current?.dispatchAction({ type: 'showTip', seriesIndex: POINTS, dataIndex: i });
      onHover(i);
    } else if ((event.key === 'Enter' || event.key === ' ') && focus != null && onPick) {
      event.preventDefault();
      onPick(focus);
    }
  };

  return (
    <div
      className="vchart"
      tabIndex={0}
      onKeyDown={key}
      onBlur={() => {
        if (focus == null) return;
        setFocus(null);
        chart.current?.dispatchAction({ type: 'hideTip' });
        chart.current?.dispatchAction({ type: 'updateAxisPointer', currTrigger: 'leave' });
        onHover(null);
      }}
    >
      <EChart option={option} theme={theme} height={height} summary={summary} onReady={ready} />
    </div>
  );
}
