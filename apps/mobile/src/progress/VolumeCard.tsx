/**
 * Progress's first card (as Strava's): a chip per sport, this week's totals (or the week touched on the
 * chart) and the last 12 weeks of the sport's main measure, with a way into the detail screen.
 */
import {
  SPORT_ICONS,
  SPORTS,
  volumeQuery,
  isoDay,
  bucketValue,
  metricsFor,
  monthLabels as coreMonthLabels,
  sportsIn,
  type Bucket,
  type VolumeMetric,
  addDays,
  volumeFormat,
  weekAt,
  weekTitle as coreWeekTitle,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { useApi } from '../auth';
import { Icon, type IconName } from '../Icon';
import { locale, t } from '../i18n';
import { asSport, useUnits } from '../prefs';
import { CardSkeleton } from '../Skeleton';
import { useTheme } from '../theme';
import { Chip, CHIP_ICON } from './Chip';
import { Card } from '../ui';
import { VolumeChart } from './VolumeChart';

/** Totals, axis labels and dates written for the person's units and language. */
export function useVolumeFormat() {
  return volumeFormat(useUnits() === 'imperial', locale);
}

/** "7 sept - 13 sept 2026": a week as the headers write it. */
export const weekTitle = (monday: string) => coreWeekTitle(monday, locale);

/** Months under a weekly chart: one where a month starts, as SEP · OCT · NOV. */
export const monthLabels = (starts: string[]) => coreMonthLabels(starts, locale);

/** A sport's chip or row icon, and the all-sports one. */
export const sportIcon = (sport: string | null): IconName =>
  sport ? SPORT_ICONS[asSport(sport)] : 'monitoring';

export function VolumeCard() {
  const api = useApi();
  const c = useTheme();
  const format = useVolumeFormat();
  const today = isoDay(new Date());
  const volume = useQuery(volumeQuery(api, { from: weekAt(11, today), to: addDays(today, 1) }, 'week'));
  const [sport, setSport] = useState<string | null>(null);
  const [picked, setPicked] = useState<number | null>(null);
  if (volume.isPending) return <CardSkeleton lines={6} />;
  const buckets = (volume.data ?? []) as Bucket[];
  const sports = sportsIn(buckets, SPORTS, asSport);
  const metrics = metricsFor(sport);
  const chartMetric = metrics[0];
  const valueOf = (b: Bucket, m: VolumeMetric) => bucketValue(b, m, sport, asSport);
  const last = buckets.length - 1;
  const index = picked ?? last;
  const week = buckets[index];
  const values = buckets.map((b) => valueOf(b, chartMetric));
  const ticks = format.ticks(Math.max(...values, 0), chartMetric, 2);

  const chip = (s: string | null) => (
    <Chip
      key={s ?? 'all'}
      on={s === sport}
      label={s ? t(`sport.${asSport(s)}`) : t('progress.allSports')}
      icon={(colour) => <Icon name={sportIcon(s)} color={colour} size={CHIP_ICON} />}
      onPress={() => {
        setSport(s);
        setPicked(null);
      }}
    />
  );

  return (
    <Card style={{ gap: 14 }}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        accessibilityRole="radiogroup"
        contentContainerStyle={{ gap: 8 }}
      >
        {chip(null)}
        {sports.map(chip)}
      </ScrollView>
      <Text style={{ color: c.fg, fontSize: 18, fontWeight: '800' }}>
        {index === last ? t('progress.thisWeek') : week ? weekTitle(week.start) : ''}
      </Text>
      <View style={{ flexDirection: 'row', gap: 28, flexWrap: 'wrap' }}>
        {metrics.map((m) => (
          <View key={m}>
            <Text style={{ color: c.fg, fontSize: 13 }}>{t(`progress.metricShort.${m}`)}</Text>
            <Text style={{ color: c.fg, fontSize: 18, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
              {format.value(m, week ? valueOf(week, m) : 0)}
            </Text>
          </View>
        ))}
      </View>
      <Text style={{ color: c.fg, fontSize: 13 }}>{t('progress.last12Weeks')}</Text>
      <VolumeChart
        values={values}
        ticks={ticks}
        tickLabel={(v) => format.tick(chartMetric, v)}
        labels={monthLabels(buckets.map((b) => b.start))}
        picked={index}
        onPick={setPicked}
        height={190}
        label={`${t('progress.last12Weeks')}: ${values.map((v) => format.value(chartMetric, v)).join(', ')}`}
      />
      <Pressable
        accessibilityRole="button"
        onPress={() => router.push({ pathname: '/progress-detail', params: sport ? { sport } : {} })}
        style={{
          alignItems: 'center',
          paddingVertical: 14,
          borderRadius: 999,
          borderWidth: 1.5,
          borderColor: c.line,
          backgroundColor: c.bg,
        }}
      >
        <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700' }}>{t('progress.moreDetail')}</Text>
      </Pressable>
    </Card>
  );
}
