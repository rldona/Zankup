import type { ThemeName } from '@zankup/core';
import type { EChartsCoreOption, EChartsType } from 'echarts/core';
import { useEffect, useRef, useState } from 'react';

import { reducedMotion, useInView } from './inView';
import { morphOptions, withIds } from './morph';

type Loaded = typeof import('./echarts');

let loading: Promise<Loaded> | null = null;
const load = () => (loading ??= import('./echarts'));

/**
 * An ECharts chart that loads when it comes near the screen, follows the theme, resizes with its
 * box and respects reduced motion (OS-215). `summary` is what screen readers get instead of the
 * drawing. `onReady` runs after every option change, to wire events; it returns their cleanup.
 * A new option (another filter) keeps the same chart and its series, so bars and lines slide from
 * the old values to the new ones in 320 ms (OS-1012, see ./morph.ts).
 */
export function EChart({
  option,
  theme,
  summary,
  height = 200,
  onReady,
  className,
}: {
  option: EChartsCoreOption;
  theme: ThemeName;
  summary: string;
  /** Pixels, or a CSS length (`100%` fills a box that has a height). */
  height?: number | string;
  onReady?: (chart: EChartsType) => (() => void) | undefined;
  className?: string;
}) {
  const [box, seen] = useInView<HTMLDivElement>();
  const [chart, setChart] = useState<EChartsType | null>(null);
  const latest = useRef(onReady);
  // The option on screen and the chart it is on: a new chart (a theme) starts afresh.
  const shown = useRef<{ chart: EChartsType; option: Record<string, unknown> } | null>(null);
  useEffect(() => {
    latest.current = onReady;
  });

  useEffect(() => {
    const element = box.current;
    if (!seen || !element) return;
    let disposed = false;
    let instance: EChartsType | null = null;
    let observer: ResizeObserver | null = null;
    void load().then(({ echarts }) => {
      if (disposed) return;
      instance = echarts.init(element, `zankup-${theme}`, { renderer: 'svg' });
      observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => instance?.resize());
      observer?.observe(element);
      setChart(instance);
    });
    return () => {
      disposed = true;
      observer?.disconnect();
      instance?.dispose();
      setChart(null);
    };
  }, [seen, theme, box]);

  useEffect(() => {
    // A theme change disposes the chart above and changes the option in the same render: this runs
    // once more with the disposed chart, whose zrender is gone (getZr() is null), before the new one
    // arrives. Wiring it would throw, so wait for the new one.
    if (!chart || chart.isDisposed()) return;
    const next: Record<string, unknown> = withIds({
      animation: !reducedMotion(),
      animationDurationUpdate: 320,
      animationEasingUpdate: 'cubicOut',
      ...option,
    });
    const previous = shown.current?.chart === chart ? shown.current.option : null;
    chart.setOption(next as EChartsCoreOption, morphOptions(previous, next));
    shown.current = { chart, option: next };
    const cleanup = latest.current?.(chart);
    // On unmount the chart may already be disposed (the effect above cleans up first): its events
    // went with it, and touching it again would throw.
    return () => {
      if (!chart.isDisposed()) cleanup?.();
    };
  }, [chart, option]);

  return (
    <div className={className} role="img" aria-label={summary}>
      <div ref={box} style={{ height }} />
    </div>
  );
}
