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
import { useResolvedTheme } from '../../theme';

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
  const top = ticks.at(-1) || 1;
  const rings = values.length <= 60;
  // Callers pass fresh arrays and functions on every render: the chart is drawn again only when what
  // it shows changes, so hovering (which re-renders the caller) does not redraw it.
  const tickText = ticks.map(tickLabel);
  const signature = JSON.stringify([values, previous, labels, ticks, tickText]);
  const option = useMemo(() => {
    const words = new Map(labels.map((l) => [l.index, l.text]));
    return {
      grid: { left: 6, right: 6, top: 14, bottom: 26, containLabel: true },
      tooltip: {
        trigger: 'axis',
        showContent: false,
        axisPointer: { type: 'line', lineStyle: { color: c.fg, width: 2, type: 'solid' } },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: values.map((_, i) => i),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          interval: 0,
          color: c.fg,
          fontSize: 12,
          formatter: (_: string, i: number) => words.get(i) ?? '',
        },
      },
      yAxis: {
        type: 'value',
        position: 'right',
        min: 0,
        max: top,
        interval: ticks[1] || top,
        axisLabel: { color: c.fg, fontSize: 12, formatter: (v: number) => tickText[nearest(ticks, v)] ?? '' },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        ...(previous?.length
          ? [
              {
                type: 'line',
                data: previous.slice(0, values.length),
                symbol: 'none',
                silent: true,
                lineStyle: { color: c.fatigue, width: 2 },
                itemStyle: { color: c.fatigue },
                emphasis: { disabled: true },
              },
            ]
          : []),
        {
          type: 'line',
          data: values.map((v) => Math.min(v, top)),
          symbol: rings ? 'circle' : 'none',
          symbolSize: 9,
          showAllSymbol: true,
          lineStyle: { color: c.accent, width: 3 },
          itemStyle: { color: c.surface, borderColor: c.accent, borderWidth: 2 },
          areaStyle: { color: c.accent, opacity: 0.3 },
          emphasis: { scale: 1.8, itemStyle: { color: c.accent, borderColor: c.accent } },
        },
      ],
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, c]);

  // The handlers as they are now: the chart wires its events once per option.
  const handlers = useRef({ onHover, onPick });
  useEffect(() => {
    handlers.current = { onHover, onPick };
  });
  const chart = useRef<EChartsType | null>(null);
  const shown = useRef<number | null>(null);
  const [focus, setFocus] = useState<number | null>(null);
  const main = previous?.length ? 1 : 0;

  const ready = useCallback((instance: EChartsType) => {
    chart.current = instance;
    const pointer = (event: { axesInfo?: { axisDim: string; value: number }[] }) => {
      const info = event.axesInfo?.find((a) => a.axisDim === 'x');
      if (!info || info.value === shown.current) return;
      shown.current = info.value;
      handlers.current.onHover(info.value);
    };
    const click = () => {
      if (shown.current != null) handlers.current.onPick?.(shown.current);
    };
    const out = () => {
      shown.current = null;
      handlers.current.onHover(null);
    };
    instance.on('updateAxisPointer', pointer as never);
    instance.getZr().on('click', click);
    instance.getZr().on('globalout', out);
    return () => {
      instance.off('updateAxisPointer');
      instance.getZr().off('click', click);
      instance.getZr().off('globalout', out);
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
      chart.current?.dispatchAction({ type: 'showTip', seriesIndex: main, dataIndex: i });
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
