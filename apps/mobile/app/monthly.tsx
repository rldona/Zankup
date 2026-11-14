/**
 * Monthly activities (as Strava's): this month against the last for all sports, then for each sport
 * done in either, with its time, distance, elevation and count.
 */
import { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';

import { t } from '../src/i18n';
import { asSport } from '../src/prefs';
import { MonthChart, useMonths } from '../src/progress/Monthly';
import { months, monthSports, type MonthTotals } from '../src/progress/monthTotals';
import { hoursMinutes } from '../src/progress/volume';
import { useVolumeFormat } from '../src/progress/VolumeCard';
import { ChartSkeleton } from '../src/Skeleton';
import { useTheme } from '../src/theme';
import { Muted } from '../src/ui';

function Figures({ totals, count }: { totals: MonthTotals; count: boolean }) {
  const c = useTheme();
  const format = useVolumeFormat();
  // A sport with nothing this month shows only its count, as Strava's.
  const figures: [string, string][] = totals.count
    ? [
        [t('monthly.time'), hoursMinutes(totals.time)],
        ...(totals.distance > 0
          ? ([
              [t('monthly.distance'), format.value('distance', totals.distance, 1)],
              [t('monthly.ascent'), format.value('ascent', totals.ascent)],
            ] as [string, string][])
          : []),
        ...(count ? ([[t('monthly.count'), String(totals.count)]] as [string, string][]) : []),
      ]
    : [[t('monthly.count'), '0']];
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: 28, rowGap: 8 }}>
      {figures.map(([label, value]) => (
        <View key={label}>
          <Muted style={{ fontSize: 13 }}>{label}</Muted>
          <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>{value}</Text>
        </View>
      ))}
    </View>
  );
}

export default function MonthlyScreen() {
  const c = useTheme();
  const { calendar, today } = useMonths();
  const [width, setWidth] = useState(0);
  const all = calendar ? months(calendar, today) : null;
  const sections = calendar
    ? monthSports(calendar).map((sport) => ({ sport, ...months(calendar, today, sport) }))
    : [];
  return (
    <ScrollView style={{ backgroundColor: c.bg }} contentContainerStyle={{ paddingBottom: 32 }}>
      <View
        style={{ padding: 20, gap: 18, backgroundColor: c.surface }}
        onLayout={(e) => setWidth(e.nativeEvent.layout.width - 40)}
      >
        {!all || !width ? (
          <ChartSkeleton height={260} />
        ) : (
          <>
            <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>
              {t('monthly.activities', { count: all.current.totals.count })}
            </Text>
            <Figures totals={all.current.totals} count={false} />
            <MonthChart {...all} width={width} height={260} label />
          </>
        )}
      </View>
      {width > 0 &&
        sections.map((s) => (
          <View key={s.sport} style={{ marginTop: 8, padding: 20, gap: 18, backgroundColor: c.surface }}>
            <Text style={{ color: c.fg, fontSize: 20, fontWeight: '800' }}>
              {t(`sport.${asSport(s.sport)}`)}
            </Text>
            <Figures totals={s.current.totals} count />
            <MonthChart current={s.current} previous={s.previous} width={width} height={220} label />
          </View>
        ))}
    </ScrollView>
  );
}
