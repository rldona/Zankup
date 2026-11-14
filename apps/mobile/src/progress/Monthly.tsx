/**
 * Monthly activities (as Strava's): this month's moving time adding up day by day over last month's,
 * on Progress's card and, for all sports and each one, on its own screen.
 */
import { calendarQuery } from '@zankup/core';
import { Canvas, Circle, DashPathEffect, Line, Path, Skia, vec } from '@shopify/react-native-skia';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../auth';
import { useCanvasReady } from '../charts';
import { Icon } from '../Icon';
import { locale, t } from '../i18n';
import { CardSkeleton } from '../Skeleton';
import { useTheme } from '../theme';
import { accent, Card, Muted } from '../ui';
import { months, monthsRange, type Month } from './monthTotals';
import { hoursMinutes } from './volume';

/** The calendar of this month and the last, fetched once for the card and the screen. */
export function useMonths() {
  const api = useApi();
  const today = useMemo(() => new Date(), []);
  const query = useQuery(calendarQuery(api, monthsRange(today)));
  return { calendar: query.data, today, pending: query.isPending };
}

/** "octubre" in the person's language. */
export const monthName = (m: Month) => m.start.toLocaleDateString(locale, { month: 'long' });

/**
 * The two lines: last month in grey and this month in the accent up to today, ending in a dot. With
 * `label`, a dashed line at last month's total with its name and time.
 */
export function MonthChart({
  current,
  previous,
  width,
  height,
  label = false,
}: {
  current: Month;
  previous: Month;
  width: number;
  height: number;
  label?: boolean;
}) {
  const c = useTheme();
  const ready = useCanvasReady(width);
  const pad = 10;
  const top = label ? 28 : pad;
  const slots = Math.max(current.days, previous.days) - 1;
  const max = Math.max(previous.totals.time, current.totals.time, 1);
  const x = (i: number) => pad + (i / slots) * (width - 2 * pad);
  const y = (v: number) => top + (1 - v / max) * (height - top - pad);
  const line = (values: number[]) => {
    const p = Skia.PathBuilder.Make();
    values.forEach((v, i) => (i ? p.lineTo(x(i), y(v)) : p.moveTo(x(i), y(v))));
    return p.build();
  };
  const last = current.cumulative.length - 1;
  const dot = { cx: x(Math.max(0, last)), cy: y(current.cumulative[last] ?? 0) };
  const stroke = label ? 3 : 2.5;
  const text = `${monthName(previous)} · ${hoursMinutes(previous.totals.time)}`;
  const labelWidth = text.length * 7.2 + 16;
  return (
    <View style={{ width, height }}>
      <Canvas style={{ width, height, opacity: ready ? 1 : 0 }}>
        {ready && (
          <>
            {label && (
              <Line
                p1={vec(pad + labelWidth, y(previous.totals.time))}
                p2={vec(width - pad, y(previous.totals.time))}
                color={c.muted}
                strokeWidth={1.5}
              >
                <DashPathEffect intervals={[4, 4]} />
              </Line>
            )}
            <Path path={line(previous.cumulative)} style="stroke" strokeWidth={stroke} color={c.muted} />
            {last >= 0 && (
              <>
                <Path
                  path={line(current.cumulative)}
                  style="stroke"
                  strokeWidth={stroke}
                  strokeJoin="round"
                  color={String(c.accent)}
                />
                <Circle {...dot} r={label ? 13 : 18} color={String(c.accent)} opacity={0.25} />
                <Circle {...dot} r={label ? 6 : 8} color={String(c.accent)} />
              </>
            )}
          </>
        )}
      </Canvas>
      {label && (
        <Text
          numberOfLines={1}
          style={{
            position: 'absolute',
            left: pad,
            top: y(previous.totals.time) - 9,
            color: c.fg,
            fontSize: 12,
            fontWeight: '700',
          }}
        >
          {text}
        </Text>
      )}
    </View>
  );
}

/** Progress's card: this month's time, last month's, and the two lines small. */
export function MonthlyCard() {
  const c = useTheme();
  const { calendar, today, pending } = useMonths();
  const [width, setWidth] = useState(0);
  if (pending || !calendar) return <CardSkeleton lines={3} />;
  const { current, previous } = months(calendar, today);
  return (
    <Card>
      <Pressable accessibilityRole="button" onPress={() => router.push('/monthly')} style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Icon name="calendar_month" color={accent(c)} size={26} />
          <Text numberOfLines={1} style={{ flex: 1, color: c.fg, fontSize: 16, fontWeight: '700' }}>
            {t('monthly.title')}
          </Text>
          <Muted style={{ fontSize: 14 }}>{t('monthly.thisMonth')}</Muted>
          <Icon name="chevron_right" color={c.muted} size={22} />
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View style={{ flex: 1 }}>
            <Muted style={{ fontSize: 14 }}>{t('monthly.time')}</Muted>
            <Text style={{ color: c.fg, fontSize: 30, fontWeight: '800' }}>
              {hoursMinutes(current.totals.time)}
            </Text>
            <Muted style={{ fontSize: 14 }}>
              {t('monthly.lastMonth', { value: hoursMinutes(previous.totals.time) })}
            </Muted>
          </View>
          <View style={{ width: '42%' }} onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
            {width > 0 && <MonthChart current={current} previous={previous} width={width} height={90} />}
          </View>
        </View>
      </Pressable>
    </Card>
  );
}
