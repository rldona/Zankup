/**
 * The splits as Strava's workout analysis: a bar per split, as wide as its distance and taller the
 * faster, in pace blue, over the elevation as a grey area, with the average dashed across. With `axis`,
 * the pace on the left; with `numbers`, each split's number under it. A finger on it (when `onSelect`
 * is given) picks the split under it.
 */
import { ZONE_SCALES } from '@zankup/core';
import { Canvas, Path } from '@shopify/react-native-skia';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { useCanvasReady } from '../charts';
import { t } from '../i18n';
import { router } from 'expo-router';
import { useUnits } from '../prefs';
import { useTheme } from '../theme';
import { accent, Card, Muted } from '../ui';
import { speedText } from './Context';
import { splitAt } from './splitsElevation';

/** The bars' blue: Strava's lighter pace blue, so the elevation shows through. */
export const SPLIT_BLUE = ZONE_SCALES.pace[2];

export function SplitsChart({
  splits,
  pace,
  elevation,
  height = 150,
  axis = false,
  numbers = false,
  selected = null,
  onSelect,
}: {
  splits: { distance_m: number; time_s: number | null }[];
  pace: boolean;
  /** The altitude in equal steps over the distance (elevationProfile), or none. */
  elevation: (number | null)[];
  height?: number;
  axis?: boolean;
  numbers?: boolean;
  selected?: number | null;
  onSelect?: (index: number) => void;
}) {
  const c = useTheme();
  const imperial = useUnits() === 'imperial';
  const [width, setWidth] = useState(0);
  const ready = useCanvasReady(width);
  const speeds = splits.map((s) => (s.time_s ? s.distance_m / s.time_s : 0));
  const usable = speeds.filter((v) => v > 0);
  const lo = Math.min(...usable) * 0.9;
  const hi = Math.max(...usable);
  const total = splits.reduce((a, s) => a + s.distance_m, 0);
  const time = splits.reduce((a, s) => a + (s.time_s ?? 0), 0);
  const mean = time ? total / time : 0;
  const h = (v: number) => (v > 0 ? 12 + ((v - lo) / (hi - lo || 1)) * (height - 12) : 4);
  // The elevation fills the chart's height from its lowest to its highest point.
  const alts = elevation.filter((v): v is number => v != null);
  const aLo = alts.length ? Math.min(...alts) : 0;
  const aHi = alts.length ? Math.max(...alts) : 1;
  const ay = (v: number) => height - 6 - ((v - aLo) / (aHi - aLo || 1)) * (height * 0.85);
  const area =
    alts.length > 1 && width > 0
      ? elevation
          .map((v, k) => {
            const x = (k / (elevation.length - 1)) * width;
            return `${k ? 'L' : 'M'}${x} ${ay(v ?? aLo)}`;
          })
          .join('') + `L${width} ${height}L0 ${height}Z`
      : null;
  const pick = (x: number) =>
    onSelect?.(
      splitAt(
        Math.min(1, Math.max(0, x / (width || 1))),
        splits.map((s) => s.distance_m),
      ),
    );
  const paceOnly = (v: number) => speedText(v, pace, imperial).split(' ')[0];
  return (
    <View style={{ flexDirection: 'row' }}>
      {axis && (
        <View style={{ width: 46, height }}>
          {[0.25, 0.5, 0.75, 1].map((f) => {
            const v = lo + f * (hi - lo);
            return (
              <Text
                key={f}
                style={{
                  position: 'absolute',
                  top: height - h(v) - 8,
                  color: c.fg,
                  fontSize: 12,
                  fontVariant: ['tabular-nums'],
                }}
              >
                {paceOnly(v)}
              </Text>
            );
          })}
          <Text style={{ position: 'absolute', bottom: 0, color: c.fg, fontSize: 12 }}>
            {pace ? `/${imperial ? 'mi' : 'km'}` : imperial ? 'mph' : 'km/h'}
          </Text>
        </View>
      )}
      <View style={{ flex: 1 }}>
        <View
          onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
          onStartShouldSetResponder={() => onSelect != null}
          onMoveShouldSetResponder={() => onSelect != null}
          onResponderGrant={(e) => pick(e.nativeEvent.locationX)}
          onResponderMove={(e) => pick(e.nativeEvent.locationX)}
          style={{ height, flexDirection: 'row', alignItems: 'flex-end', gap: 4 }}
        >
          {area && ready && (
            <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0, width, height }}>
              <Path path={area} color={String(c.muted)} opacity={0.35} />
            </Canvas>
          )}
          {splits.map((s, i) => (
            <View
              key={i}
              accessible
              accessibilityLabel={`${i + 1}: ${speedText(speeds[i], pace, imperial)}`}
              pointerEvents="none"
              style={{
                flex: Math.max(0.2, s.distance_m / (total / splits.length)),
                height: h(speeds[i] ?? 0),
                borderTopLeftRadius: 8,
                borderTopRightRadius: 8,
                backgroundColor: SPLIT_BLUE,
                opacity: selected == null || selected === i ? 0.85 : 0.45,
              }}
            />
          ))}
          {mean > 0 && (
            <View
              pointerEvents="none"
              style={{
                position: 'absolute',
                left: 0,
                right: 0,
                bottom: h(mean),
                borderTopWidth: 1.5,
                borderStyle: 'dashed',
                borderColor: c.fg,
              }}
            />
          )}
        </View>
        {numbers && (
          <View style={{ flexDirection: 'row', gap: 4, marginTop: 6 }}>
            {splits.map((s, i) => (
              <Text
                key={i}
                style={{
                  flex: Math.max(0.2, s.distance_m / (total / splits.length)),
                  textAlign: 'center',
                  color: selected === i ? c.fg : c.muted,
                  fontSize: 12,
                  fontWeight: selected === i ? '800' : '400',
                }}
              >
                {i + 1}
              </Text>
            ))}
          </View>
        )}
      </View>
    </View>
  );
}

/**
 * "Training analysis" on a run's page, as Strava's: the splits' chart over the elevation, the average
 * and the fastest split, and a link to the whole analysis (app/analysis/[id].tsx).
 */
export function SplitBars({
  activityId,
  splits,
  pace,
  elevation,
}: {
  activityId: number;
  splits: { distance_m: number; time_s: number | null }[];
  pace: boolean;
  elevation: (number | null)[];
}) {
  const c = useTheme();
  const imperial = useUnits() === 'imperial';
  if (splits.length < 2) return null;
  const speeds = splits.map((s) => (s.time_s ? s.distance_m / s.time_s : 0));
  const total = splits.reduce((a, s) => a + s.distance_m, 0);
  const time = splits.reduce((a, s) => a + (s.time_s ?? 0), 0);
  return (
    <Card style={{ gap: 8 }}>
      <Text style={{ color: c.fg, fontSize: 17, fontWeight: '800' }}>{t('detail.analysis')}</Text>
      <SplitsChart splits={splits} pace={pace} elevation={elevation} />
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
        <Muted style={{ fontSize: 12 }}>
          {t('detail.analysisAverage', { pace: speedText(time ? total / time : 0, pace, imperial) })}
        </Muted>
        <Muted style={{ fontSize: 12 }}>
          {t('detail.analysisFastest', { pace: speedText(Math.max(...speeds), pace, imperial) })}
        </Muted>
      </View>
      <Text
        accessibilityRole="link"
        onPress={() => router.push(`/analysis/${activityId}`)}
        style={{ alignSelf: 'flex-end', color: accent(c), fontSize: 15, fontWeight: '700', paddingTop: 4 }}
      >
        {t('detail.analysisOpen')}
      </Text>
    </Card>
  );
}
