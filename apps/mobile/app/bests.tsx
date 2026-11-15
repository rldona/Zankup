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
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View, type ScrollViewInstance } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withDecay,
  withTiming,
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
  offsetFor,
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
import { isoDay } from '../src/progress/fitness';
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
  const today = isoDay(new Date());
  const [key, setKey] = useState(params.key ?? '10k');
  // The measure shown until the picked one loads: it loads in the background, with no skeleton.
  const [previous, setPrevious] = useState(params.key ?? '10k');
  const [tab, setTab] = useState<'analysis' | 'top'>('analysis');
  // The best at the top of the list (none: the newest): the chart puts it at its right edge.
  const [anchor, setAnchor] = useState<number | null>(null);
  const list = useRef<ScrollViewInstance>(null);
  const rows = useRef(new Map<number, { y: number; h: number }>());
  const yearHeader = useRef(44);
  const [panel, setPanel] = useState<'sport' | 'year' | null>(null);
  const sport = sportOf(key);
  const keys = (sport === 'run' ? [...RUN_KEYS] : [...RIDE_KEYS]).filter((k) => effortShown(k, format.unit));
  const bests = useQuery(bestsQuery(api, key));
  // The last measure's bests, from the cache, while the picked one loads.
  const before = useQuery({ ...bestsQuery(api, previous), enabled: false });
  const shownKey = bests.data || !before.data ? key : previous;
  const all = (bests.data ?? before.data ?? []) as Best[];
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
    setAnchor(null);
    rows.current.clear();
    list.current?.scrollTo({ y: 0, animated: false });
  };
  // Newest first, as the list shows them.
  const ordered = [...all].sort((a, b) => (a.day < b.day ? 1 : a.day > b.day ? -1 : 0));
  // Oldest first, as the chart draws them; the list's top best is the chart's right edge.
  const chrono = [...ordered].reverse();
  const at = chrono.findIndex((b) => b.activity_id === anchor);
  const focus = at >= 0 ? at : chrono.length - 1;
  const focusYear = chrono[focus]?.day.slice(0, 4) ?? today.slice(0, 4);
  // The list and the chart move together (as Strava's): scrolling the list scrolls the chart to the
  // best at the top, and dragging the chart scrolls the list to the best at its right edge.
  const onListScroll = (offset: number) => {
    const line = offset + yearHeader.current;
    const atTop = ordered.find((b) => {
      const r = rows.current.get(b.activity_id);
      return r != null && r.y + r.h > line;
    });
    if (atTop && atTop.activity_id !== (anchor ?? ordered[0]?.activity_id)) setAnchor(atTop.activity_id);
  };
  const scrollToBest = (best: Best | undefined, animated: boolean) => {
    const r = best && rows.current.get(best.activity_id);
    if (!best || r == null) return;
    const y = best === ordered[0] ? 0 : Math.max(0, r.y - yearHeader.current);
    list.current?.scrollTo({ y, animated });
  };
  const scrollToDay = (target: string) =>
    scrollToBest(ordered.find((b) => b.day <= target) ?? ordered.at(-1), true);

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
          on={false}
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
            focus={focus}
            focusYear={focusYear}
            label={value}
            onMove={(i) => scrollToBest(chrono[i], false)}
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
          <ScrollView
            ref={list}
            style={{ flex: 1 }}
            contentContainerStyle={{ paddingBottom: 32 }}
            scrollEventThrottle={32}
            onScroll={(e) => onListScroll(e.nativeEvent.contentOffset.y)}
            stickyHeaderIndices={yearsOf(all).map((y) => listIndex(ordered, y))}
          >
            {ordered.flatMap((b, i) => {
              const y = b.day.slice(0, 4);
              const medal = yearMedal(shownKey, all, b);
              const row = (
                <Pressable
                  key={b.activity_id}
                  accessibilityRole="button"
                  onPress={() => open(b)}
                  onLayout={(e) =>
                    rows.current.set(b.activity_id, {
                      y: e.nativeEvent.layout.y,
                      h: e.nativeEvent.layout.height,
                    })
                  }
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
                  onLayout={(e) => (yearHeader.current = e.nativeEvent.layout.height)}
                  style={{ paddingHorizontal: 16, paddingVertical: 12, backgroundColor: c.surface }}
                >
                  <Text style={{ color: c.fg, fontSize: 15, fontWeight: '800' }}>{y}</Text>
                </View>,
                row,
              ];
            })}
          </ScrollView>
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
              accessibilityState={{ checked: y === focusYear }}
              onPress={() => {
                if (y) scrollToDay(`${y}-12-31`);
                else list.current?.scrollTo({ y: 0, animated: true });
                setPanel(null);
              }}
              style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 14 }}
            >
              <Text style={{ flex: 1, color: y === focusYear ? accent(c) : c.fg, fontSize: 15 }}>
                {y ?? t('progress.last12Months')}
              </Text>
              {y === focusYear && <Icon name="check" color={accent(c)} size={24} />}
            </Pressable>
          ))}
        </ScrollView>
      </Panel>
    </View>
  );
}

/**
 * The bests as Strava's chart: one slot each, oldest to newest, twelve on screen, the better ones higher
 * (a faster time or more watts), the year's medals in their colours, the bests of the list's year filled
 * and the rest hollow, and the trend through them as a smooth line. The scale goes from the record to
 * the slow days and stays while it scrolls. It follows the finger sideways and keeps going a little after
 * a fling; the list below scrolls with it.
 */
function BestsChart({
  bestKey,
  all,
  chrono,
  focus,
  focusYear,
  label,
  onMove,
}: {
  bestKey: string;
  all: Best[];
  /** Every best, oldest first. */
  chrono: Best[];
  /** The best at the list's top: the chart scrolls to put it at the right edge. */
  focus: number;
  focusYear: string;
  label: (v: number) => string;
  /** The chart was dragged: the best now at its right edge, oldest first. */
  onMove: (index: number) => void;
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
  const offset = useSharedValue(offsetFor(focus, n));
  const start = useSharedValue(0);
  const dragging = useSharedValue(false);
  const [first, setFirst] = useState(() => Math.round(offsetFor(focus, n)));
  // The list moved (or another measure was picked): scroll to its top best, unless the finger is on the chart.
  useEffect(() => {
    if (!dragging.get())
      offset.set(withTiming(offsetFor(focus, n), { duration: 250, easing: Easing.out(Easing.cubic) }));
  }, [focus, n, offset, dragging]);
  useAnimatedReaction(
    () => Math.round(offset.get()),
    (now, before) => {
      if (now === before) return;
      scheduleOnRN(setFirst, now);
      if (dragging.get()) scheduleOnRN(onMove, Math.min(n - 1, Math.max(0, now + CHART_SLOTS - 1)));
    },
    [n, onMove],
  );
  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-12, 12])
    .onStart(() => {
      cancelAnimation(offset);
      start.set(offset.get());
      dragging.set(true);
    })
    .onUpdate((e) => {
      // Dragged right shows earlier bests, as Strava's.
      offset.set(Math.min(high, Math.max(low, start.get() - e.translationX / slot)));
    })
    .onEnd((e) => {
      offset.set(
        withDecay({ velocity: -e.velocityX / slot, clamp: [low, high], deceleration: 0.994 }, (finished) => {
          if (finished) dragging.set(false);
        }),
      );
    });
  const shift = useDerivedValue(() => [{ translateX: -offset.get() * slot }]);
  const lower = lowerIsBetter(bestKey);
  const { record, worst } = chartScale(bestKey, all);
  const span = Math.abs(worst - record) || Math.abs(record) * 0.1 || 1;
  // Better is higher: the record at the top, the slow days at the bottom (slower ones sit on it).
  const y = (v: number) => {
    const behind = lower ? v - record : record - v;
    return top + Math.min(1, Math.max(0, behind / span)) * (base - top);
  };
  const x = (i: number) => axis + 8 + slot / 2 + i * slot;
  const middle = (record + worst) / 2;
  const line = trend(chrono, 45, chrono);
  const left = chrono[Math.max(0, first)];
  const right = chrono[Math.min(n - 1, first + CHART_SLOTS - 1)];
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
                    <Group transform={shift}>
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
                        const m = yearMedal(bestKey, all, b);
                        const colour = m ? MEDAL_COLOURS[m - 1] : String(c.muted);
                        const filled = b.day.startsWith(focusYear);
                        return filled ? (
                          <Circle key={b.activity_id} cx={x(i)} cy={y(b.value)} r={6} color={colour} />
                        ) : (
                          <Group key={b.activity_id}>
                            <Circle cx={x(i)} cy={y(b.value)} r={6} color={String(c.page)} />
                            <Circle
                              cx={x(i)}
                              cy={y(b.value)}
                              r={5}
                              style="stroke"
                              strokeWidth={2}
                              color={colour}
                            />
                          </Group>
                        );
                      })}
                    </Group>
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
            {left && right && (
              <Muted style={{ position: 'absolute', left: axis + 8, top: base + 10, fontSize: 12 }}>
                {day(left.day)} – {day(right.day)}
              </Muted>
            )}
          </>
        )}
      </View>
    </GestureDetector>
  );
}
