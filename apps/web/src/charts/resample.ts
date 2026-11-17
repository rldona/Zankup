/**
 * A line that morphs from one range to another (a month into a year, weeks into days) instead of
 * crossing itself on the way. ECharts morphs a line point by point: a month's 30 points turning into
 * a year's 365 sent each one sliding across the chart to another day, cutting straight lines and
 * crossings. Drawn instead through the same number of evenly spread places at every range, each point
 * keeps its place across the width and only moves up or down to its new height.
 *
 * The x of each place is in the data's own index (0 is the first value, `values.length - 1` the last),
 * so the chart's x axis is a value axis from 0 to the last index, and the real points (rings, the
 * pointer) sit on their whole indexes over it.
 */
import type { EChartsType } from 'echarts/core';

/** As many places as two years' days: every range's line has them all. */
export const SAMPLES = 730;

/**
 * The values at SAMPLES evenly spread places, as [index, value]. Gaps (nulls) between two values are
 * bridged, as `connectNulls` draws them; before the first value and after the last there is no line.
 * `smooth` follows a monotone curve through the values (as ECharts' `smoothMonotone`), not straight
 * segments, for a line drawn smooth.
 */
export function resample(
  values: readonly (number | null | undefined)[],
  { samples = SAMPLES, smooth = false }: { samples?: number; smooth?: boolean } = {},
): [number, number | null][] {
  const last = values.length - 1;
  const known: number[] = [];
  values.forEach((v, i) => {
    if (v != null && Number.isFinite(v)) known.push(i);
  });
  const at = (i: number) => values[i] ?? 0;
  const slopes = smooth ? monotoneSlopes(known, at) : null;
  let j = 0;
  return Array.from({ length: samples }, (_, k): [number, number | null] => {
    const x = last > 0 ? (k * last) / (samples - 1) : 0;
    const first = known[0];
    const end = known.at(-1);
    if (first == null || end == null || x < first - 1e-9 || x > end + 1e-9) return [x, null];
    // The known values on either side of x (the places only go up, so the search carries on).
    while (j < known.length - 2 && (known[j + 1] ?? 0) < x) j++;
    const i0 = known[j] ?? first;
    const i1 = known[j + 1] ?? i0;
    const a = at(i0);
    const b = at(i1);
    if (i1 === i0) return [x, a];
    const h = i1 - i0;
    const s = (x - i0) / h;
    if (!slopes) return [x, a + (b - a) * s];
    // Cubic Hermite with the monotone slopes: it never overshoots the values it passes through.
    const m0 = (slopes[j] ?? 0) * h;
    const m1 = (slopes[j + 1] ?? 0) * h;
    const s2 = s * s;
    const s3 = s2 * s;
    return [x, (2 * s3 - 3 * s2 + 1) * a + (s3 - 2 * s2 + s) * m0 + (-2 * s3 + 3 * s2) * b + (s3 - s2) * m1];
  });
}

/** Fritsch–Carlson slopes at each known point: a curve through them that keeps their ups and downs. */
function monotoneSlopes(known: number[], at: (i: number) => number): number[] {
  const n = known.length;
  if (n < 2) return known.map(() => 0);
  const deltas: number[] = [];
  for (let k = 0; k < n - 1; k++) {
    const i0 = known[k] ?? 0;
    const i1 = known[k + 1] ?? i0;
    deltas.push((at(i1) - at(i0)) / (i1 - i0 || 1));
  }
  const slopes = known.map((_, k) => {
    if (k === 0) return deltas[0] ?? 0;
    if (k === n - 1) return deltas[n - 2] ?? 0;
    const d0 = deltas[k - 1] ?? 0;
    const d1 = deltas[k] ?? 0;
    return d0 * d1 <= 0 ? 0 : (d0 + d1) / 2;
  });
  for (let k = 0; k < n - 1; k++) {
    const d = deltas[k] ?? 0;
    if (d === 0) {
      slopes[k] = 0;
      slopes[k + 1] = 0;
      continue;
    }
    const a = (slopes[k] ?? 0) / d;
    const b = (slopes[k + 1] ?? 0) / d;
    const r = a * a + b * b;
    if (r > 9) {
      const t = 3 / Math.sqrt(r);
      slopes[k] = t * a * d;
      slopes[k + 1] = t * b * d;
    }
  }
  return slopes;
}

/**
 * The pointer on a resampled chart: it picks the real point (whole index) nearest to it, shows the
 * axis pointer and the point's emphasis there, and tells `onIndex` (null when it leaves). The chart's
 * tooltip needs `triggerOn: 'none'`, so only this moves it; `seriesIndex` is the series of the real
 * points. Returns the cleanup.
 */
export function pointToIndex(
  chart: EChartsType,
  {
    count,
    seriesIndex,
    onIndex,
  }: { count: () => number; seriesIndex: () => number; onIndex: (index: number | null) => void },
): () => void {
  let shown: number | null = null;
  const show = (index: number | null) => {
    if (index === shown) return;
    shown = index;
    chart.dispatchAction({ type: 'downplay', seriesIndex: seriesIndex() });
    if (index == null) {
      chart.dispatchAction({ type: 'hideTip' });
      chart.dispatchAction({ type: 'updateAxisPointer', currTrigger: 'leave' });
    } else {
      chart.dispatchAction({ type: 'showTip', seriesIndex: seriesIndex(), dataIndex: index });
      chart.dispatchAction({ type: 'highlight', seriesIndex: seriesIndex(), dataIndex: index });
    }
    onIndex(index);
  };
  const move = (event: { offsetX: number; offsetY: number }) => {
    const n = count();
    if (!n || !chart.containPixel({ gridIndex: 0 }, [event.offsetX, event.offsetY])) return show(null);
    const at = chart.convertFromPixel({ gridIndex: 0 }, [event.offsetX, event.offsetY]) as number[] | null;
    const i = Math.round(at?.[0] ?? Number.NaN);
    if (Number.isFinite(i)) show(Math.max(0, Math.min(n - 1, i)));
  };
  const out = () => show(null);
  const zr = chart.getZr();
  zr.on('mousemove', move);
  zr.on('click', move);
  zr.on('globalout', out);
  return () => {
    zr.off('mousemove', move);
    zr.off('click', move);
    zr.off('globalout', out);
  };
}
