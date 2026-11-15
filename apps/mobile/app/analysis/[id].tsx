/**
 * Workout analysis, from a run's "Training analysis" card (as Strava's): the splits' chart, large, over
 * the elevation, with the pace on the left and each split's number under it; a finger along it picks a
 * split, highlighted in the chart and in the list of splits below (distance, time and pace).
 */
import { activityQuery, formatDuration, usesPace, type Series } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View, type ScrollViewInstance } from 'react-native';

import { useApi } from '../../src/auth';
import { SplitsChart } from '../../src/detail/SplitsChart';
import { elevationProfile } from '../../src/detail/splitsElevation';
import { t } from '../../src/i18n';
import { streamsQuery } from '../../src/offline';
import { asSport, useFormat } from '../../src/prefs';
import { useTheme } from '../../src/theme';
import { accent } from '../../src/ui';

export default function AnalysisScreen() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const api = useApi();
  const c = useTheme();
  const format = useFormat();
  const activity = useQuery({ ...activityQuery(api, id), enabled: Number.isInteger(id) });
  const a = activity.data;
  const streams = useQuery({ ...streamsQuery(api, id), enabled: a?.streams != null });
  const series = streams.data as Series | undefined;
  const elevation = useMemo(
    () => elevationProfile(series?.dist, series?.alt, a?.distance_m ?? 0, 200),
    [series, a?.distance_m],
  );
  const [selected, setSelected] = useState<number | null>(null);
  const list = useRef<ScrollViewInstance>(null);
  const rows = useRef<number[]>([]);
  if (!a || a.splits.length < 2) return <View style={{ flex: 1, backgroundColor: c.page }} />;
  const pace = usesPace(asSport(a.sport));
  const select = (i: number) => {
    if (i === selected) return;
    setSelected(i);
    list.current?.scrollTo({ y: Math.max(0, (rows.current[i] ?? 0) - 60), animated: true });
  };
  return (
    <View style={{ flex: 1, backgroundColor: c.page }}>
      <View style={{ paddingHorizontal: 16, paddingVertical: 16, backgroundColor: c.bg }}>
        <Text style={{ color: c.fg, fontSize: 15, fontWeight: '700', lineHeight: 22 }}>
          {t('detail.analysisHint')}
        </Text>
      </View>
      <View style={{ paddingHorizontal: 12, paddingTop: 20, paddingBottom: 12 }}>
        <SplitsChart
          splits={a.splits}
          pace={pace}
          elevation={elevation}
          height={300}
          axis
          numbers
          selected={selected}
          onSelect={select}
        />
      </View>
      <View style={{ paddingHorizontal: 16, paddingVertical: 12, backgroundColor: c.bg }}>
        <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700' }}>{t('activity.splits')}</Text>
      </View>
      <ScrollView ref={list} style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 32 }}>
        {a.splits.map((s, i) => {
          const on = i === selected;
          const weight = on ? '800' : '400';
          return (
            <Pressable
              key={i}
              accessibilityRole="button"
              accessibilityState={{ selected: on }}
              onPress={() => setSelected(on ? null : i)}
              onLayout={(e) => (rows.current[i] = e.nativeEvent.layout.y)}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 12,
                paddingHorizontal: 16,
                paddingVertical: 16,
                backgroundColor: on ? c.surface2 : 'transparent',
                borderLeftWidth: 4,
                borderLeftColor: on ? accent(c) : 'transparent',
              }}
            >
              <Text style={{ width: 28, color: c.fg, fontSize: 16, fontWeight: weight }}>{i + 1}</Text>
              <Text style={{ flex: 1, color: c.fg, fontSize: 16, fontWeight: weight }}>
                {format.distance(s.distance_m)}
              </Text>
              <Text style={{ color: c.fg, fontSize: 16, fontWeight: weight, fontVariant: ['tabular-nums'] }}>
                {s.time_s ? formatDuration(s.time_s) : '–'}
              </Text>
              <Text
                style={{
                  width: 96,
                  textAlign: 'right',
                  color: c.fg,
                  fontSize: 16,
                  fontWeight: weight,
                  fontVariant: ['tabular-nums'],
                }}
              >
                {s.time_s ? format.speedFor(s.distance_m / s.time_s, pace) : '–'}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}
