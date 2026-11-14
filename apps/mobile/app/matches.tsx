/**
 * The efforts on the same route as an activity (as Strava's "Matched activities"): a line of their pace
 * or speed over time, the one opened marked, and the list by year (date, pace or speed, time and
 * relative effort), each opening its activity.
 */
import { activityQuery } from '@zankup/core';
import { Canvas, Circle, Line, Path, vec } from '@shopify/react-native-skia';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { useApi } from '../src/auth';
import { useCanvasReady } from '../src/charts';
import { clock, speedText, useActivityContext } from '../src/detail/Context';
import { Icon } from '../src/Icon';
import { locale, t } from '../src/i18n';
import { asSport, useUnits } from '../src/prefs';
import { smoothPath } from '../src/progress/raceTimes';
import { ChartSkeleton } from '../src/Skeleton';
import { useTheme } from '../src/theme';
import { accent, Muted } from '../src/ui';

export default function MatchesScreen() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const api = useApi();
  const c = useTheme();
  const imperial = useUnits() === 'imperial';
  const activity = useQuery(activityQuery(api, id)).data;
  const context = useActivityContext(id);
  const [width, setWidth] = useState(0);
  const ready = useCanvasReady(width);
  const pace = activity ? ['run', 'walk'].includes(asSport(activity.sport)) : false;
  const matches = context?.matches ?? [];
  const height = 200;
  const values = matches.map((m) => m.speed_mps ?? 0).filter((v) => v > 0);
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  // Faster is higher, for a pace too.
  const y = (v: number) => 16 + (1 - (v - lo) / (hi - lo || 1)) * (height - 40);
  const x = (i: number) => 16 + (i / Math.max(1, matches.length - 1)) * (width - 32);
  const points = matches.map((m, i) => ({ x: x(i), y: y(m.speed_mps ?? lo) }));
  const years = [...new Set(matches.map((m) => m.day.slice(0, 4)))].sort().reverse();
  return (
    <ScrollView style={{ backgroundColor: c.bg }} contentContainerStyle={{ paddingBottom: 32 }}>
      <View style={{ padding: 16 }} onLayout={(e) => setWidth(e.nativeEvent.layout.width - 32)}>
        {!context || !width ? (
          <ChartSkeleton height={height} />
        ) : (
          <View style={{ opacity: ready ? 1 : 0 }}>
            <Canvas style={{ width, height }}>
              {ready && (
                <>
                  {[lo, (lo + hi) / 2, hi].map((v, i) => (
                    <Line key={i} p1={vec(0, y(v))} p2={vec(width, y(v))} color={c.line} strokeWidth={1} />
                  ))}
                  <Path
                    path={smoothPath(points)}
                    style="stroke"
                    strokeWidth={2.5}
                    color={String(c.fitness)}
                  />
                  {matches.map((m, i) => (
                    <Circle
                      key={m.activity_id}
                      cx={points[i].x}
                      cy={points[i].y}
                      r={m.this ? 7 : 4.5}
                      color={m.this ? String(c.accent) : String(c.muted)}
                    />
                  ))}
                </>
              )}
            </Canvas>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <Muted style={{ fontSize: 12 }}>{speedText(lo, pace, imperial)}</Muted>
              <Muted style={{ fontSize: 12 }}>{speedText(hi, pace, imperial)}</Muted>
            </View>
          </View>
        )}
      </View>
      {years.map((year) => {
        const rows = matches.filter((m) => m.day.startsWith(year)).reverse();
        return (
          <View key={year}>
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                paddingHorizontal: 16,
                paddingVertical: 10,
                backgroundColor: c.surface,
              }}
            >
              <Text style={{ color: c.fg, fontSize: 14, fontWeight: '800' }}>{year}</Text>
              <Muted style={{ fontSize: 13 }}>{rows.length}</Muted>
            </View>
            {rows.map((m) => (
              <Pressable
                key={m.activity_id}
                accessibilityRole="button"
                disabled={m.this}
                onPress={() => router.push(`/activity/${m.activity_id}`)}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 10,
                  paddingHorizontal: 16,
                  paddingVertical: 14,
                  borderLeftWidth: 4,
                  borderLeftColor: m.this ? accent(c) : 'transparent',
                }}
              >
                <Text style={{ flex: 1, color: c.fg, fontSize: 15 }}>
                  {new Date(`${m.day}T12:00:00`).toLocaleDateString(locale, {
                    day: 'numeric',
                    month: 'long',
                  })}
                </Text>
                <Muted style={{ fontSize: 13, fontVariant: ['tabular-nums'] }}>
                  {speedText(m.speed_mps, pace, imperial)} · {clock(m.moving_s)}
                  {m.relative_effort ? ` · ${Math.round(m.relative_effort)} ${t('detail.er')}` : ''}
                </Muted>
                {!m.this && <Icon name="chevron_right" color={c.muted} size={20} />}
              </Pressable>
            ))}
          </View>
        );
      })}
    </ScrollView>
  );
}
