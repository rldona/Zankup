/**
 * Best times, from Progress (as Strava's): one measure (a run distance, a ride's best power over a
 * duration or its distance) with two tabs. Analysis: the record and the second and third, a chart of
 * the bests with their trend, twelve at a time, that scrolls sideways with the finger, and those bests
 * by year below, each with its medal of the year; the chart and the list move together. Top 10: the ten
 * best. Each best opens its activity.
 */
import { bestsQuery, effortShown, SPORT_ICONS } from '@zankup/core';
import { Canvas, Circle, Group, Line, Path, rect, vec } from '@shopify/react-native-skia';
import { useQuery } from '@tanstack/react-query';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useDerivedValue,
  useSharedValue,
  withDecay,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { useApi } from '../src/auth';
import { useCanvasReady } from '../src/charts';
import { Icon } from '../src/Icon';
import { locale, t } from '../src/i18n';
import { useFormat, useUnits } from '../src/prefs';
import {
  bestValue,
  CHART_SLOTS,
  lowerIsBetter,
  chartScale,
  offsetRange,
  placeOf,
  ranked,
  RIDE_KEYS,
  RUN_KEYS,
  RUN_METRES,
  trend,
  yearMedal,
  yearsOf,
  type Best,
} from '../src/progress/bests';
import { bestName, Medal, MEDAL_COLOURS, sportOf } from '../src/progress/BestsCard';
import { Chip, CHIP_ICON } from '../src/progress/Chip';
import { Panel } from '../src/progress/Panel';
import { racePace, smoothPath } from '../src/progress/raceTimes';
import { useTheme } from '../src/theme';
import { accent, InfoButton, Muted } from '../src/ui';

/** Where a year's header is among the list's children (a header before each year's first best). */
function listIndex(ordered: Best[], year: string): number {
  let index = 0;
  for (let i = 0; i < ordered.length; i++) {
    const y = ordered[i].day.slice(0, 4);
    if (i === 0 || !ordered[i - 1].day.startsWith(y)) {
      if (y === year) return index;
      index += 1;
    }
    index += 1;
  }
  return 0;
}

const day = (iso: string, withYear = true) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'long',
    ...(withYear ? { year: 'numeric' } : {}),
  });

export default function BestsScreen() {
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const imperial = useUnits() === 'imperial';
  const params = useLocalSearchParams<{ key?: string }>();
  const [key, setKey] = useState(params.key ?? '10k');
  // The measure shown until the picked one loads: it loads in the background, with no skeleton.
  const [previous, setPrevious] = useState(params.key ?? '10k');
  const [tab, setTab] = useState<'analysis' | 'top'>('analysis');
  // The chart and the list move together, frame by frame on the UI thread (as Strava's): `offset` is
  // the chart's left slot; whoever the finger is on drives the other (1: the chart, 2: the list).
  const list = useAnimatedRef<Animated.ScrollView>();
  const offset = useSharedValue(0);
  const driver = useSharedValue(0);
  // Where each row of the list starts (newest first), and the sticky year header's height.
  const rowTops = useRef<number[]>([]);
  const rowTop = useSharedValue<number[]>([]);
  const yearHeader = useSharedValue(44);
  const [panel, setPanel] = useState<'sport' | 'year' | null>(null);
  const sport = sportOf(key);
  const keys = (sport === 'run' ? [...RUN_KEYS] : [...RIDE_KEYS]).filter((k) => effortShown(k, format.unit));
  // An hour fresh: bests change only with a new activity, and a sync refreshes them.
  const bests = useQuery({ ...bestsQuery(api, key), staleTime: 60 * 60_000 });
  // The last measure's bests, from the cache, while the picked one loads.
  const before = useQuery({ ...bestsQuery(api, previous), enabled: false });
  const shownKey = bests.data || !before.data ? key : previous;
  const all = useMemo(() => (bests.data ?? before.data ?? []) as Best[], [bests.data, before.data]);
  const loaded = bests.data != null || before.data != null;
  const top = ranked(shownKey, all);
  const value = (v: number) => bestValue(shownKey, v, locale, imperial);
  const pace = (v: number) =>
    sportOf(shownKey) === 'run'
      ? `${racePace(v, RUN_METRES[shownKey] ?? 1000, imperial)} /${imperial ? 'mi' : 'km'}`
      : null;
  const open = (b: Best) => router.push(`/activity/${b.activity_id}`);

  const pick = (k: string) => {
    if (bests.data) setPrevious(key);
    setKey(k);
  };
  // Newest first, as the list shows them; oldest first, as the chart draws them.
  const ordered = useMemo(() => [...all].sort((a, b) => (a.day < b.day ? 1 : a.day > b.day ? -1 : 0)), [all]);
  const chrono = useMemo(() => [...ordered].reverse(), [ordered]);
  const medals = useMemo(
    () => new Map(all.map((b) => [b.activity_id, yearMedal(shownKey, all, b)])),
    [all, shownKey],
  );
  const n = chrono.length;
  const [low, high] = offsetRange(n);
  // Another measure: the newest best at the chart's right edge and the list at its top.
  useEffect(() => {
    // Rows keep their place by index; their onLayout fills in what moved (clearing here could race it).
    rowTops.current.length = chrono.length;
    rowTop.set([...rowTops.current]);
    driver.set(0);
    offset.set(offsetRange(chrono.length)[1]);
    list.current?.scrollTo({ y: 0, animated: false });
  }, [chrono, list, offset, driver, rowTop]);

  // The list's top best, fractional while between two rows, as the chart's right edge, and back.
  const offsetAt = (y: number) => {
    'worklet';
    const tops = rowTop.get();
    if (tops.length < n || n === 0) return null;
    const line = y + yearHeader.get();
    let k = 0;
    while (k < n - 1 && (tops[k + 1] ?? 0) <= line) k++;
    const next = tops[k + 1];
    const share =
      next != null && next > (tops[k] ?? 0)
        ? Math.min(1, Math.max(0, (line - (tops[k] ?? 0)) / (next - (tops[k] ?? 0))))
        : 0;
    const right = n - 1 - (k + share);
    return Math.min(high, Math.max(low, right - (CHART_SLOTS - 1)));
  };
  const listAt = (o: number) => {
    'worklet';
    const tops = rowTop.get();
    if (tops.length < n || n === 0) return null;
    const right = Math.min(n - 1, Math.max(0, o + CHART_SLOTS - 1));
    const k = n - 1 - right;
    const k0 = Math.floor(k);
    const k1 = Math.min(n - 1, k0 + 1);
    const a = tops[k0] ?? 0;
    const y = a + (k - k0) * ((tops[k1] ?? a) - a);
    return Math.max(0, y - yearHeader.get());
  };
  const onScroll = useAnimatedScrollHandler(
    {
      onBeginDrag: () => {
        driver.set(2);
      },
      onScroll: (e) => {
        if (driver.get() === 1) return; // the chart is moving the list
        const o = offsetAt(e.contentOffset.y);
        if (o != null) offset.set(o);
      },
    },
    [n, low, high],
  );
  useAnimatedReaction(
    () => offset.get(),
    (o) => {
      if (driver.get() !== 1) return;
      const y = listAt(o);
      if (y != null) scrollTo(list, 0, y, false);
    },
    [n],
  );
  const scrollToDay = (target: string) => {
    const k = ordered.findIndex((b) => b.day <= target);
    const y = k <= 0 ? 0 : Math.max(0, (rowTops.current[k] ?? 0) - yearHeader.get());
    driver.set(0);
    list.current?.scrollTo({ y, animated: true });
  };

  return (
    <View style={{ flex: 1, backgroundColor: c.page }}>
      <Stack.Screen
        options={{ title: bestName(key), headerRight: () => <InfoButton text={t('progress.bestsInfo')} /> }}
      />
      <View style={{ flexDirection: 'row', borderBottomWidth: 1, borderColor: c.line }}>
        {(['analysis', 'top'] as const).map((tb) => {
          const on = tb === tab;
          return (
            <Pressable
              key={tb}
              accessibilityRole="tab"
              accessibilityState={{ selected: on }}
              onPress={() => setTab(tb)}
              style={{
                flex: 1,
                alignItems: 'center',
                paddingVertical: 12,
                borderBottomWidth: 3,
                borderColor: on ? accent(c) : 'transparent',
              }}
            >
              <Text style={{ color: on ? c.fg : c.muted, fontSize: 15, fontWeight: '700' }}>
                {t(tb === 'analysis' ? 'progress.analysis' : 'progress.top10')}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        // Its own height, never squeezed by the list below (the chips were cut at the bottom).
        style={{ flexGrow: 0, flexShrink: 0 }}
        contentContainerStyle={{ gap: 8, paddingHorizontal: 16, paddingVertical: 12, alignItems: 'center' }}
      >
        <Chip
          on
          role="button"
          label={t(`sport.${sport}`)}
          icon={(colour) => (
            <>
              <Icon name={SPORT_ICONS[sport]} color={colour} size={CHIP_ICON} />
            </>
          )}
          onPress={() => setPanel('sport')}
        />
        {keys.map((k) => (
          <Chip key={k} on={k === key} label={bestName(k)} onPress={() => pick(k)} />
        ))}
      </ScrollView>

      {tab === 'analysis' && top.length ? (
        // Analysis: the record, the chart and the filter stay; the list scrolls below them.
        <View style={{ flex: 1 }}>
          <View style={{ paddingHorizontal: 16, gap: 4 }}>
            <Text style={{ color: c.fg, fontSize: 15, fontWeight: '700' }}>{t('progress.allTime')}</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <Medal rank={1} size={52} />
              <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
                  <Text
                    style={{ color: c.fg, fontSize: 30, fontWeight: '800', fontVariant: ['tabular-nums'] }}
                  >
                    {value(top[0].value)}
                  </Text>
                  {pace(top[0].value) && <Muted style={{ fontSize: 15 }}>{pace(top[0].value)}</Muted>}
                </View>
                <Muted style={{ fontSize: 13 }}>{day(top[0].day)}</Muted>
              </View>
              {top.slice(1, 3).map((b, i) => (
                <View key={b.activity_id} style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}>
                  <Medal rank={i + 2} size={24} />
                  <Text style={{ color: c.fg, fontSize: 13, fontVariant: ['tabular-nums'] }}>
                    {value(b.value)}
                  </Text>
                </View>
              ))}
            </View>
          </View>
          <BestsChart
            bestKey={shownKey}
            all={all}
            chrono={chrono}
            medals={medals}
            offset={offset}
            driver={driver}
            label={value}
          />
          <View style={{ paddingHorizontal: 16, paddingVertical: 8 }}>
            <Pressable
              accessibilityRole="button"
              onPress={() => setPanel('year')}
              style={{
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center',
                gap: 8,
                paddingVertical: 12,
                borderRadius: 999,
                borderWidth: 1.25,
                borderColor: c.line,
              }}
            >
              <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700' }}>{t('progress.filterYear')}</Text>
              <Icon name="keyboard_arrow_down" color={c.fg} size={22} />
            </Pressable>
          </View>
          <Animated.ScrollView
            ref={list}
            style={{ flex: 1 }}
            contentContainerStyle={{ paddingBottom: 32 }}
            scrollEventThrottle={16}
            onScroll={onScroll}
            stickyHeaderIndices={yearsOf(all).map((y) => listIndex(ordered, y))}
          >
            {ordered.flatMap((b, i) => {
              const y = b.day.slice(0, 4);
              const medal = medals.get(b.activity_id);
              const row = (
                <Pressable
                  key={b.activity_id}
                  accessibilityRole="button"
                  onPress={() => open(b)}
                  onLayout={(e) => {
                    rowTops.current[i] = e.nativeEvent.layout.y;
                    rowTop.set([...rowTops.current]);
                  }}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 10,
                    paddingHorizontal: 16,
                    paddingVertical: 14,
                    borderBottomWidth: 1,
                    borderColor: c.line,
                  }}
                >
                  <Text style={{ flex: 1, color: c.fg, fontSize: 15 }}>{day(b.day, false)}</Text>
                  {medal && (
                    <View
                      style={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: 2,
                        paddingHorizontal: 6,
                        paddingVertical: 2,
                        borderRadius: 6,
                        backgroundColor: c.surface2,
                      }}
                    >
                      <Medal rank={medal} size={22} />
                      <Text style={{ color: c.fg, fontSize: 13, fontWeight: '700' }}>{y}</Text>
                    </View>
                  )}
                  <Text style={{ color: c.fg, fontSize: 15, fontVariant: ['tabular-nums'] }}>
                    {value(b.value)}
                  </Text>
                  {pace(b.value) && (
                    <Muted style={{ width: 82, textAlign: 'right', fontSize: 14 }}>{pace(b.value)}</Muted>
                  )}
                </Pressable>
              );
              if (i > 0 && ordered[i - 1].day.startsWith(y)) return [row];
              // The year's header stays on top while its bests scroll under it.
              return [
                <View
                  key={`y${y}`}
                  onLayout={(e) => yearHeader.set(e.nativeEvent.layout.height)}
                  style={{ paddingHorizontal: 16, paddingVertical: 12, backgroundColor: c.surface }}
                >
                  <Text style={{ color: c.fg, fontSize: 15, fontWeight: '800' }}>{y}</Text>
                </View>,
                row,
              ];
            })}
          </Animated.ScrollView>
        </View>
      ) : (
        <ScrollView contentContainerStyle={{ paddingBottom: 32 }}>
          {!loaded ? null : !top.length ? (
            <Muted style={{ padding: 16 }}>{t('progress.noBests')}</Muted>
          ) : (
            top.slice(0, 10).map((b) => {
              const place = placeOf(shownKey, all, b);
              return (
                <Pressable
                  key={b.activity_id}
                  accessibilityRole="button"
                  onPress={() => open(b)}
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 16,
                    paddingHorizontal: 16,
                    paddingVertical: 12,
                  }}
                >
                  <View
                    style={{
                      width: 56,
                      height: 56,
                      borderRadius: 28,
                      alignItems: 'center',
                      justifyContent: 'center',
                      backgroundColor: place <= 3 ? `${MEDAL_COLOURS[place - 1]}33` : c.surface,
                    }}
                  >
                    {place <= 3 ? (
                      <Medal rank={place} size={36} />
                    ) : (
                      <Text style={{ color: c.fg, fontSize: 18, fontWeight: '800' }}>{place}</Text>
                    )}
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text
                      style={{ color: c.fg, fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] }}
                    >
                      {value(b.value)}
                    </Text>
                    <Muted style={{ fontSize: 13 }}>
                      {[pace(b.value), day(b.day)].filter(Boolean).join(' · ')}
                    </Muted>
                  </View>
                  <Icon name="chevron_right" color={c.muted} size={22} />
                </Pressable>
              );
            })
          )}
        </ScrollView>
      )}

      <Panel open={panel === 'sport'} onClose={() => setPanel(null)}>
        <View style={{ paddingHorizontal: 20, paddingBottom: 8 }}>
          <Text style={{ color: c.fg, fontSize: 17, fontWeight: '800', marginBottom: 8 }}>
            {t('progress.filterSport')}
          </Text>
          {(['run', 'ride'] as const).map((s) => {
            const on = s === sport;
            return (
              <Pressable
                key={s}
                accessibilityRole="radio"
                accessibilityState={{ checked: on }}
                onPress={() => {
                  pick(s === 'run' ? '10k' : 'longest');
                  setPanel(null);
                }}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 16, paddingVertical: 14 }}
              >
                <Icon name={SPORT_ICONS[s]} color={on ? accent(c) : c.fg} size={26} />
                <Text style={{ flex: 1, color: on ? accent(c) : c.fg, fontSize: 15 }}>{t(`sport.${s}`)}</Text>
                {on && <Icon name="check" color={accent(c)} size={26} />}
              </Pressable>
            );
          })}
        </View>
      </Panel>
      <Panel open={panel === 'year'} onClose={() => setPanel(null)}>
        <ScrollView contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 8 }}>
          {[null, ...yearsOf(all)].map((y) => (
            <Pressable
              key={y ?? 'last'}
              accessibilityRole="radio"
              accessibilityState={{ checked: false }}
              onPress={() => {
                if (y) scrollToDay(`${y}-12-31`);
                else {
                  driver.set(0);
                  list.current?.scrollTo({ y: 0, animated: true });
                }
                setPanel(null);
              }}
              style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 14 }}
            >
              <Text style={{ flex: 1, color: c.fg, fontSize: 15 }}>{y ?? t('progress.last12Months')}</Text>
            </Pressable>
          ))}
        </ScrollView>
      </Panel>
    </View>
  );
}

/**
 * The bests as Strava's chart: one slot each, oldest to newest, twelve on screen, the better ones higher
 * (a faster time or more watts), the year's medals in their colours, the bests of the year at the right
 * edge filled and the rest hollow, and the trend through them as a smooth line. The scale goes from the
 * record to the slow days and stays while it scrolls. It follows the finger sideways and keeps going a
 * little after a fling; the list below moves with it frame by frame. Everything is drawn once: moving
 * only shifts it, so nothing is redrawn while the finger is on it.
 */
function BestsChart({
  bestKey,
  all,
  chrono,
  medals,
  offset,
  driver,
  label,
}: {
  bestKey: string;
  all: Best[];
  /** Every best, oldest first. */
  chrono: Best[];
  medals: Map<number, number | null>;
  /** The slot at the left edge, shared with the list. */
  offset: SharedValue<number>;
  /** Who moves it: 1 the chart (this), 2 the list. */
  driver: SharedValue<number>;
  label: (v: number) => string;
}) {
  const c = useTheme();
  const [width, setWidth] = useState(0);
  const ready = useCanvasReady(width);
  const height = 260;
  const axis = 70;
  const top = 14;
  const base = height - 34;
  const plot = Math.max(1, width - axis - 8);
  const slot = plot / CHART_SLOTS;
  const n = chrono.length;
  const [low, high] = offsetRange(n);
  const start = useSharedValue(0);
  // The best at the right edge, for the dates under the chart and the year drawn filled: it changes a
  // few times a second at most, and only the labels follow it.
  const [right, setRight] = useState(n - 1);
  useAnimatedReaction(
    () => Math.min(n - 1, Math.max(0, Math.round(offset.get()) + CHART_SLOTS - 1)),
    (now, before) => {
      if (now !== before) scheduleOnRN(setRight, now);
    },
    [n],
  );
  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-12, 12])
    .onStart(() => {
      cancelAnimation(offset);
      start.set(offset.get());
      driver.set(1);
    })
    .onUpdate((e) => {
      // Dragged right shows earlier bests, as Strava's.
      offset.set(Math.min(high, Math.max(low, start.get() - e.translationX / slot)));
    })
    .onEnd((e) => {
      offset.set(withDecay({ velocity: -e.velocityX / slot, clamp: [low, high], deceleration: 0.997 }));
    });
  const shift = useDerivedValue(() => [{ translateX: -offset.get() * slot }]);
  const lower = lowerIsBetter(bestKey);
  const { record, worst } = useMemo(() => chartScale(bestKey, all), [bestKey, all]);
  const span = Math.abs(worst - record) || Math.abs(record) * 0.1 || 1;
  // Better is higher: the record at the top, the slow days at the bottom (slower ones sit on it).
  const y = (v: number) => {
    const behind = lower ? v - record : record - v;
    return top + Math.min(1, Math.max(0, behind / span)) * (base - top);
  };
  const middle = (record + worst) / 2;
  const year = chrono[Math.min(n - 1, Math.max(0, right))]?.day.slice(0, 4) ?? '';
  // Drawn once per measure, size and year: the lines, the trend and the points.
  const drawing = useMemo(() => {
    if (!ready || !n) return null;
    const x = (i: number) => axis + 8 + slot / 2 + i * slot;
    const line = trend(chrono, 45, chrono);
    return (
      <>
        {chrono.map((b, i) => (
          <Line
            key={`l${b.activity_id}`}
            p1={vec(x(i), top)}
            p2={vec(x(i), base)}
            color={c.line}
            strokeWidth={1}
          />
        ))}
        {n > 1 && (
          <Path
            path={smoothPath(chrono.map((_, i) => ({ x: x(i), y: y(line[i] ?? record) })))}
            style="stroke"
            strokeWidth={2.5}
            color={String(c.fitness)}
          />
        )}
        {chrono.map((b, i) => {
          const m = medals.get(b.activity_id);
          const colour = m ? MEDAL_COLOURS[m - 1] : String(c.muted);
          return b.day.startsWith(year) ? (
            <Circle key={b.activity_id} cx={x(i)} cy={y(b.value)} r={6} color={colour} />
          ) : (
            <Group key={b.activity_id}>
              <Circle cx={x(i)} cy={y(b.value)} r={6} color={String(c.page)} />
              <Circle cx={x(i)} cy={y(b.value)} r={5} style="stroke" strokeWidth={2} color={colour} />
            </Group>
          );
        })}
      </>
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, chrono, medals, slot, year, record, worst, c]);
  const left = chrono[Math.max(0, right - CHART_SLOTS + 1)];
  const last = chrono[right];
  return (
    <GestureDetector gesture={pan}>
      <View
        onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
        style={{ height, marginTop: 16, opacity: ready ? 1 : 0 }}
      >
        {width > 0 && (
          <>
            <Canvas style={{ position: 'absolute', width, height }}>
              {ready && (
                <>
                  {[record, middle, worst].map((v, i) => (
                    <Line key={i} p1={vec(axis, y(v))} p2={vec(width, y(v))} color={c.line} strokeWidth={1} />
                  ))}
                  <Group clip={rect(axis, 0, width - axis, height)}>
                    <Group transform={shift}>{drawing}</Group>
                  </Group>
                </>
              )}
            </Canvas>
            {[record, middle, worst].map((v, i) => (
              <Text
                key={i}
                style={{
                  position: 'absolute',
                  left: 4,
                  top: y(v) - 9,
                  color: c.fg,
                  fontSize: 12,
                  fontVariant: ['tabular-nums'],
                }}
              >
                {label(v)}
              </Text>
            ))}
            {left && last && (
              <Muted style={{ position: 'absolute', left: axis + 8, top: base + 10, fontSize: 12 }}>
                {day(left.day)} – {day(last.day)}
              </Muted>
            )}
          </>
        )}
      </View>
    </GestureDetector>
  );
}
