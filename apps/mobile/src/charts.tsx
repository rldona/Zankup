/**
 * Trend charts drawn with Skia (OS-512), as complete as the PWA's: lines and bars (stacked, or around
 * zero when values go negative) over a grid with round values on the left axis, a right axis when a
 * second scale is drawn, a few days along the bottom and a legend. Touching or sliding sideways picks a
 * day and shows its values; a vertical swipe still scrolls the screen. New values (another period, sport
 * or metric) slide into place as the zones' bars do (`MorphPath`).
 */
import { niceScale, tightScale } from '@zankup/core';
import {
  Canvas,
  DashPathEffect,
  Group,
  Line,
  Path,
  Skia,
  vec,
  type SkPath,
} from '@shopify/react-native-skia';
import { useEffect, useMemo, useState, type ComponentProps } from 'react';
import { Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Easing, useDerivedValue, useSharedValue, withTiming } from 'react-native-reanimated';

import { useTheme } from './theme';

type Value = number | null | undefined;

export type Layer =
  | {
      kind: 'line';
      label: string;
      color: string;
      values: Value[];
      domain?: [number, number];
      /** Line width (2 by default) and opacity, to set one line apart from the rest. */
      width?: number;
      opacity?: number;
      /** Left out of the legend (many lines of the same kind). */
      hideLegend?: boolean;
    }
  | {
      kind: 'bars';
      /** Several series are stacked; values below zero hang from a zero line. */
      series: { label: string; color: string; values: Value[] }[];
      domain?: [number, number];
    };

/** The range a layer is drawn on: its own unless given, and always including zero for bars. */
export function domainOf(layer: Layer): [number, number] {
  if (layer.domain) return layer.domain;
  if (layer.kind === 'line') {
    const values = layer.values.filter((v): v is number => v != null);
    if (!values.length) return [0, 1];
    const lo = Math.min(...values);
    const hi = Math.max(...values);
    const pad = (hi - lo) * 0.1 || 1;
    return [lo - pad, hi + pad];
  }
  const length = Math.max(0, ...layer.series.map((s) => s.values.length));
  let lo = 0;
  let hi = 0;
  for (let i = 0; i < length; i++) {
    const total = layer.series.reduce((sum, s) => sum + (s.values[i] ?? 0), 0);
    lo = Math.min(lo, total);
    hi = Math.max(hi, total);
  }
  return hi === lo ? [lo, lo + 1] : [lo, hi];
}

/** The polyline of a series, broken where a day has no value. */
export function linePath(values: Value[], width: number, height: number, [lo, hi]: [number, number]) {
  const step = values.length > 1 ? width / (values.length - 1) : 0;
  const y = (v: number) => height - ((v - lo) / (hi - lo || 1)) * height;
  const segments: [number, number][][] = [];
  let current: [number, number][] = [];
  values.forEach((v, i) => {
    if (v == null) {
      if (current.length) segments.push(current);
      current = [];
    } else current.push([i * step, y(v)]);
  });
  if (current.length) segments.push(current);
  return segments;
}

const round = (v: number) => Math.round(v * 1e6) / 1e6;

/** A tick or a value, short: 1,5 · 120 · 12k. */
export function shortNumber(v: number) {
  const a = Math.abs(v);
  if (a >= 10000) return `${round(v / 1000)}k`;
  if (a >= 100 || Number.isInteger(v)) return String(Math.round(v));
  return String(Math.round(v * 10) / 10).replace('.', ',');
}

/** The days written under the chart: about five, spread evenly, always the first and the last. */
export function dayTicks(count: number, wanted = 5) {
  if (count <= 0) return [];
  if (count <= wanted) return Array.from({ length: count }, (_, i) => i);
  const every = (count - 1) / (wanted - 1);
  return Array.from({ length: wanted }, (_, i) => Math.round(i * every));
}

/**
 * Whether a Skia canvas of this width can be drawn on. On Android a canvas that draws before its
 * surface exists stays blank and does not redraw on its own, which happens when a chart mounts with its
 * data already there (a period changed, a card unfolded). The drawing waits two frames: the canvas gets
 * its surface in the first, the drawing lands in the second. Hide the chart until then, so its labels
 * do not show ahead of it.
 */
export function useCanvasReady(width: number): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (!width) return;
    let id = requestAnimationFrame(() => {
      id = requestAnimationFrame(() => setReady(true));
    });
    return () => cancelAnimationFrame(id);
  }, [width]);
  return ready;
}

/** How a chart moves to new values: as the zones' bars on Progress (a third of a second, easing out). */
export const MORPH = { duration: 320, easing: Easing.out(Easing.cubic) };

const skPath = (d: string): SkPath => Skia.Path.MakeFromSVGString(d) ?? Skia.Path.Make();

/**
 * A path that slides to each new `d` from what is on screen, when both have the same shape (as many
 * points, the same gaps). When they do not (a longer period has more days), or the chart has just been
 * drawn, it grows from `flat`: the same shape lying on the chart's floor. Waits for the canvas (`ready`).
 */
export function useMorphPath(d: string, flat: string, ready: boolean) {
  const empty = useMemo(() => Skia.Path.Make(), []);
  const from = useSharedValue(empty);
  const to = useSharedValue(empty);
  const progress = useSharedValue(1);
  useEffect(() => {
    if (!ready) return;
    const next = skPath(d);
    const before = to.get();
    const t = progress.get();
    const shown = t >= 1 ? before : (before.interpolate(from.get(), t) ?? before);
    const start = shown.isInterpolatable(next) ? shown : skPath(flat);
    if (!start.isInterpolatable(next)) {
      from.set(next);
      to.set(next);
      progress.set(1);
      return;
    }
    from.set(start);
    to.set(next);
    progress.set(0);
    progress.set(withTiming(1, MORPH));
  }, [d, flat, ready, from, to, progress]);
  return useDerivedValue(() => {
    const t = progress.get();
    const end = to.get();
    return t >= 1 ? end : (end.interpolate(from.get(), t) ?? end);
  });
}

/** A Skia `Path` drawn from `d` that slides to new values (`useMorphPath`). */
export function MorphPath({
  d,
  flat,
  ready,
  ...paint
}: Omit<ComponentProps<typeof Path>, 'path'> & { d: string; flat: string; ready: boolean }) {
  const path = useMorphPath(d, flat, ready);
  return <Path path={path} {...paint} />;
}

const n = (v: number) => Math.round(v * 100) / 100;

/**
 * Circles as one path, each four curves, so a set of points can slide too: a radius of 0 keeps the
 * same curves (a circle Skia adds on its own is left out when it has no size).
 */
export function circlesPath(
  points: readonly (readonly [number, number])[],
  r: number | ((i: number) => number),
) {
  return points
    .map(([x, y], i) => {
      const rr = typeof r === 'number' ? r : r(i);
      const k = rr * 0.5523;
      return (
        `M${n(x + rr)} ${n(y)}C${n(x + rr)} ${n(y + k)} ${n(x + k)} ${n(y + rr)} ${n(x)} ${n(y + rr)}` +
        `C${n(x - k)} ${n(y + rr)} ${n(x - rr)} ${n(y + k)} ${n(x - rr)} ${n(y)}` +
        `C${n(x - rr)} ${n(y - k)} ${n(x - k)} ${n(y - rr)} ${n(x)} ${n(y - rr)}` +
        `C${n(x + k)} ${n(y - rr)} ${n(x + rr)} ${n(y - k)} ${n(x + rr)} ${n(y)}Z`
      );
    })
    .join('');
}

/** Rectangles as one path, each four lines: a bar with no height keeps its place in the shape. */
export function rectsPath(rects: readonly { x: number; y: number; w: number; h: number }[]) {
  return rects
    .map(
      ({ x, y, w, h }) => `M${n(x)} ${n(y)}L${n(x + w)} ${n(y)}L${n(x + w)} ${n(y + h)}L${n(x)} ${n(y + h)}Z`,
    )
    .join('');
}

/**
 * A line through points and, for its fill, down to `base` and back. Flattened (`flat`), the points lie
 * just over `base`: a fill with no height at all left Android's canvas blank.
 */
export function polyline(points: readonly (readonly [number, number])[], base: number, flat = false) {
  const pts = flat ? points.map(([x]) => [x, base - 1] as const) : points;
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${n(x)} ${n(y)}`).join('');
  const last = pts[pts.length - 1];
  const area = last ? `${line}L${n(last[0])} ${n(base)}L${n(pts[0][0])} ${n(base)}Z` : '';
  return { line, area };
}

/**
 * A line resampled at other x's (each between the two points around it), so a line of another length can
 * turn into it instead of growing from the floor: a longer range starts as the shorter one, stretched.
 */
export function resample(points: readonly (readonly [number, number])[], xs: readonly number[]) {
  return xs.map((x) => {
    if (!points.length) return [x, 0] as const;
    let i = 0;
    while (i < points.length - 2 && points[i + 1][0] < x) i++;
    const [x0, y0] = points[i];
    const [x1, y1] = points[Math.min(i + 1, points.length - 1)];
    const t = x1 === x0 ? 0 : Math.max(0, Math.min(1, (x - x0) / (x1 - x0)));
    return [x, y0 + (y1 - y0) * t] as const;
  });
}

/**
 * Where a line far above zero (a score, a race time) starts when it changes: the one on screen,
 * stretched over the new points, so it turns into the new line instead of growing from the floor.
 * The first drawing starts where it is (no animation).
 */
export function useLineFrom(points: readonly (readonly [number, number])[]) {
  const key = points.map(([x, y]) => `${n(x)},${n(y)}`).join(' ');
  const [drawn, setDrawn] = useState({ key, points, from: points });
  if (drawn.key !== key) {
    const xs = points.map(([x]) => x);
    setDrawn({ key, points, from: drawn.points.length ? resample(drawn.points, xs) : points });
  }
  return drawn.key === key ? drawn.from : points;
}

/** A hollow ring on each point, sliding with the line: drawn as two paths (the fill, then the ring). */
export function Rings({
  points,
  base,
  r,
  fill,
  colour,
  strokeWidth = 2.5,
  ready,
  from,
}: {
  points: readonly (readonly [number, number])[];
  /** Where they grow from: the floor, or points of their own (as many). */
  base: number;
  from?: readonly (readonly [number, number])[];
  r: number | ((i: number) => number);
  fill: string;
  colour: string;
  strokeWidth?: number;
  ready: boolean;
}) {
  const d = circlesPath(points, r);
  const flat = circlesPath(from ?? points.map(([px]) => [px, base - 1] as const), 0.5);
  return (
    <>
      <MorphPath d={d} flat={flat} ready={ready} color={fill} />
      <MorphPath d={d} flat={flat} ready={ready} style="stroke" strokeWidth={strokeWidth} color={colour} />
    </>
  );
}

const AXIS = 34;
const TOP = 8;

export function Chart({
  layers,
  days,
  height = 160,
  label,
  legendGap = 6,
}: {
  layers: Layer[];
  /** One label per value: a few of them are written under the chart. */
  days: string[];
  height?: number;
  /** What the chart says, for TalkBack. */
  label: string;
  /** Room between the chart and its legend (more on the activity's chart cards). */
  legendGap?: number;
}) {
  const c = useTheme();
  const [width, setWidth] = useState(0);
  const [picked, setPicked] = useState<number | null>(null);
  const ready = useCanvasReady(width);
  const count = days.length;

  // Layers given the same domain share a scale. The first scale is the left axis, the second the right.
  const scales = useMemo(() => {
    const keys: string[] = [];
    const of = layers.map((layer, index) => {
      const key = layer.domain ? layer.domain.join(':') : `own:${index}`;
      if (!keys.includes(key)) keys.push(key);
      return keys.indexOf(key);
    });
    // One scale: the tightest round one. Two (a right axis too): four steps each, so both axes'
    // labels sit on the same grid lines.
    const fit = keys.length > 1 ? niceScale : tightScale;
    const nice = keys.map((key) => fit(domainOf(layers[of.indexOf(keys.indexOf(key))])));
    return { of, nice };
  }, [layers]);
  const right = scales.nice.length > 1;
  const left = AXIS;
  const plot = Math.max(0, width - left - (right ? AXIS : 8));
  const inner = height - TOP;
  const slot = count ? plot / count : 0;
  const bar = Math.max(1, Math.min(14, slot * 0.7));
  const yOf = (scale: number) => {
    const [lo, hi] = scales.nice[scale].domain;
    return (v: number) => TOP + inner - ((v - lo) / (hi - lo || 1)) * inner;
  };

  const floor = TOP + inner;
  const drawn = layers.map((layer, index) => {
    const scale = scales.of[index];
    const domain = scales.nice[scale].domain;
    const y = yOf(scale);
    if (layer.kind === 'line') {
      // Lines run from the centre of the first slot to the centre of the last, over the bars; they grow
      // up from the floor.
      const segments = linePath(layer.values, plot - slot, inner, domain);
      const draw = (flat: boolean) =>
        segments
          .map((segment) =>
            segment.length === 1
              ? segment.map(([x, py]) => `M${n(x + slot / 2 - 1)} ${n(flat ? floor : py + TOP)}h2`).join('')
              : segment
                  .map(([x, py], i) => `${i ? 'L' : 'M'}${n(x + slot / 2)} ${n(flat ? floor : py + TOP)}`)
                  .join(''),
          )
          .join('');
      return (
        <MorphPath
          key={index}
          d={draw(false)}
          flat={draw(true)}
          ready={ready}
          style="stroke"
          strokeWidth={layer.width ?? 3}
          strokeCap="round"
          strokeJoin="round"
          color={layer.color}
          opacity={layer.opacity ?? 1}
        />
      );
    }
    // A series' bars as one path, a bar on every day (with no height on a day without), so another
    // period of as many days slides into place; they grow from the zero line.
    const zero = y(0);
    const base = Array.from({ length: count }, () => 0);
    return (
      <Group key={index} opacity={picked == null ? 1 : 0.9}>
        {layer.series.map((s, k) => {
          const bars = base.map((under, i) => {
            const v = s.values[i] ?? 0;
            const top = y(under + v);
            const bottom = y(under);
            base[i] = under + v;
            const h = v ? Math.max(1, Math.abs(bottom - top)) : 0;
            return { x: i * slot + (slot - bar) / 2, y: Math.min(top, bottom), w: bar, h };
          });
          if (!bars.some((b) => b.h)) return null;
          return (
            <MorphPath
              key={k}
              d={rectsPath(bars)}
              flat={rectsPath(bars.map((b) => ({ ...b, y: zero - (b.h ? 0.5 : 0), h: b.h ? 1 : 0 })))}
              ready={ready}
              color={s.color}
            />
          );
        })}
      </Group>
    );
  });

  const pick = (x: number) => {
    if (!slot) return;
    setPicked(Math.max(0, Math.min(count - 1, Math.floor((x - left) / slot))));
  };
  const gesture = Gesture.Race(
    Gesture.Pan()
      .runOnJS(true)
      .activeOffsetX([-6, 6])
      .failOffsetY([-12, 12])
      .onStart((e) => pick(e.x))
      .onUpdate((e) => pick(e.x)),
    Gesture.Tap()
      .runOnJS(true)
      .onEnd((e) => {
        const i = slot ? Math.floor((e.x - left) / slot) : -1;
        setPicked((current) => (current === i || i < 0 || i >= count ? null : i));
      }),
  );

  const legend = layers.flatMap((layer) =>
    layer.kind === 'line'
      ? layer.hideLegend
        ? []
        : [{ label: layer.label, color: layer.color }]
      : layer.series,
  );
  const readout =
    picked == null
      ? []
      : layers
          .flatMap((layer) =>
            layer.kind === 'line'
              ? [{ label: layer.label, color: layer.color, value: layer.values[picked] }]
              : layer.series.map((s) => ({ label: s.label, color: s.color, value: s.values[picked] })),
          )
          .filter((item) => item.value != null)
          .slice(0, 8);
  const zeroLines = scales.nice.map((s) => s.domain[0] < 0 && s.domain[1] > 0);
  const leftTicks = scales.nice[0]?.ticks ?? [];
  const steps = Math.max(1, leftTicks.length - 1);
  const rightTicks = right ? scales.nice[1].ticks : [];
  const ticks = dayTicks(count);
  const pickedX = picked == null ? 0 : left + picked * slot + slot / 2;
  const muted = { color: c.muted, fontSize: 10, fontVariant: ['tabular-nums' as const] };

  return (
    <View accessible accessibilityLabel={label} style={{ gap: legendGap, opacity: ready ? 1 : 0 }}>
      <GestureDetector gesture={gesture}>
        <View
          collapsable={false}
          onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
          style={{ height: height + 16 }}
        >
          {width > 0 && (
            <>
              <Canvas style={{ width, height }}>
                {ready && (
                  <>
                    {leftTicks.map((_, i) => {
                      const py = Math.round(TOP + inner - (i / steps) * inner) - 0.5;
                      return (
                        <Line
                          key={i}
                          p1={vec(left, py)}
                          p2={vec(left + plot, py)}
                          color={c.line}
                          strokeWidth={1}
                        >
                          {i > 0 && <DashPathEffect intervals={[3, 4]} />}
                        </Line>
                      );
                    })}
                    {zeroLines.map((on, scale) =>
                      on ? (
                        <Line
                          key={`zero${scale}`}
                          p1={vec(left, yOf(scale)(0))}
                          p2={vec(left + plot, yOf(scale)(0))}
                          color={c.muted}
                          strokeWidth={1}
                        />
                      ) : null,
                    )}
                    <Group transform={[{ translateX: left }]}>{drawn}</Group>
                    {picked != null && (
                      <Line p1={vec(pickedX, TOP)} p2={vec(pickedX, height)} color={c.fg} strokeWidth={1} />
                    )}
                  </>
                )}
              </Canvas>
              {leftTicks.map((v, i) => (
                <Text
                  key={`l${i}`}
                  style={[
                    muted,
                    {
                      position: 'absolute',
                      left: 0,
                      width: left - 6,
                      textAlign: 'right',
                      top: TOP + inner - (i / steps) * inner - 7,
                    },
                  ]}
                >
                  {shortNumber(v)}
                </Text>
              ))}
              {rightTicks.map((v, i) => (
                <Text
                  key={`r${i}`}
                  style={[
                    muted,
                    {
                      position: 'absolute',
                      right: 0,
                      width: AXIS - 6,
                      textAlign: 'left',
                      top: TOP + inner - (i / steps) * inner - 7,
                    },
                  ]}
                >
                  {shortNumber(v)}
                </Text>
              ))}
              {ticks.map((i) => {
                const x = left + i * slot + slot / 2;
                return (
                  <Text
                    key={`d${i}`}
                    numberOfLines={1}
                    style={[
                      muted,
                      {
                        position: 'absolute',
                        top: height + 2,
                        width: 60,
                        textAlign: 'center',
                        left: Math.max(left - 12, Math.min(width - 60, x - 30)),
                      },
                    ]}
                  >
                    {days[i]}
                  </Text>
                );
              })}
              {picked != null && (
                <View
                  pointerEvents="none"
                  style={{
                    position: 'absolute',
                    top: 0,
                    ...(pickedX < width / 2 ? { left: pickedX + 8 } : { right: width - pickedX + 8 }),
                    backgroundColor: c.surface2,
                    borderColor: c.line,
                    borderWidth: 1,
                    borderRadius: 8,
                    paddingHorizontal: 8,
                    paddingVertical: 6,
                    gap: 2,
                    maxWidth: width * 0.6,
                  }}
                >
                  <Text style={{ color: c.fg, fontSize: 12, fontWeight: '700' }}>{days[picked]}</Text>
                  {readout.map((item) => (
                    <View key={item.label} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                      <View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: item.color }} />
                      <Text style={{ color: c.muted, fontSize: 12, flexShrink: 1 }} numberOfLines={1}>
                        {item.label}
                      </Text>
                      <Text
                        style={{
                          color: c.fg,
                          fontSize: 12,
                          fontWeight: '600',
                          fontVariant: ['tabular-nums'],
                        }}
                      >
                        {shortNumber(item.value as number)}
                      </Text>
                    </View>
                  ))}
                </View>
              )}
            </>
          )}
        </View>
      </GestureDetector>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 12 }}>
        {legend.map((item) => (
          <View key={item.label} style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
            <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: item.color }} />
            <Text style={{ color: c.muted, fontSize: 12 }}>{item.label}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}
