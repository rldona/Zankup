/**
 * Best times on Progress (as Strava's): the card shows the medals of the last twelve months, one per
 * measure and up to three (of all time first, then of their year, the better place first). The whole card
 * opens app/bests.tsx on its default measure (running, 10 km), as Strava's widget does. The medal, the measures'
 * names and the medal colours are shared by both.
 */
import {
  effortShown,
  recentBestsQuery,
  SPORT_ICONS,
  bestValue,
  RUN_KEYS,
  durationLabel,
  MEDAL_COLOURS,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Text, View } from 'react-native';

import { useApi } from '../auth';
import { Icon } from '../Icon';
import { locale, t } from '../i18n';
import { useFormat, useUnits } from '../prefs';
import { CardSkeleton } from '../Skeleton';
import { useTheme } from '../theme';
import { accent, Card, Muted, Tappable } from '../ui';

/** Gold, silver and bronze in every theme (shared with the web); past the third, the theme's grey. */
export { MEDAL_COLOURS };

/** A measure's name: 5 km, ½ mile… Power: 30 s, Longest ride. */
export function bestName(key: string): string {
  if (key.startsWith('power_'))
    return t('progress.bestPower', { duration: durationLabel(Number(key.slice(6))) });
  if (key === 'longest') return t('progress.bestLongest');
  return t(`effort.${key}` as never);
}

export const sportOf = (key: string) => ((RUN_KEYS as readonly string[]).includes(key) ? 'run' : 'ride');

/** A medal with its place, as Strava's: the first is "PR". */
export function Medal({ rank, size = 40 }: { rank: number; size?: number }) {
  const c = useTheme();
  const colour = MEDAL_COLOURS[rank - 1] ?? String(c.muted);
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Icon name="military_tech-fill" color={colour} size={size} />
      <Text
        style={{
          position: 'absolute',
          bottom: size * 0.12,
          color: c.bg,
          fontSize: size * (rank === 1 ? 0.2 : 0.26),
          fontWeight: '900',
        }}
      >
        {rank === 1 ? 'PR' : rank}
      </Text>
    </View>
  );
}

export function BestsCard() {
  const api = useApi();
  const c = useTheme();
  const unit = useFormat().unit;
  const imperial = useUnits() === 'imperial';
  const medals = useQuery(recentBestsQuery(api, 365));
  if (medals.isPending) return <CardSkeleton lines={3} />;
  // The server sorts them all-time first, then by place and newest: a measure's first is its best medal.
  const shown = (medals.data ?? [])
    .filter((m) => effortShown(m.key, unit))
    .filter((m, i, all) => all.findIndex((o) => o.key === m.key) === i)
    .slice(0, 3);
  return (
    <Tappable
      accessibilityRole="button"
      accessibilityLabel={t('progress.bests')}
      onPress={() => router.push('/bests')}
    >
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
          <Icon name="trophy" color={accent(c)} size={26} />
          <Text numberOfLines={1} style={{ flex: 1, color: c.fg, fontSize: 16, fontWeight: '700' }}>
            {t('progress.bests')}
          </Text>
          <Icon name="chevron_right" color={c.muted} size={22} />
        </View>
        {!shown.length ? (
          <Muted style={{ marginTop: 12 }}>{t('progress.noRecentBests')}</Muted>
        ) : (
          <View style={{ flexDirection: 'row', marginTop: 12 }}>
            {shown.map((m) => (
              <View
                key={`${m.key}-${m.activity_id}`}
                accessibilityLabel={`${bestName(m.key)} ${bestValue(m.key, m.value, locale, imperial)}`}
                style={{ flex: 1, alignItems: 'center', gap: 4 }}
              >
                <Medal rank={m.rank} size={44} />
                <Text numberOfLines={1} style={{ color: c.fg, fontSize: 13 }}>
                  {bestName(m.key)}
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
                  <Icon name={SPORT_ICONS[sportOf(m.key)]} color={c.fg} size={18} />
                  <Text
                    style={{ color: c.fg, fontSize: 17, fontWeight: '800', fontVariant: ['tabular-nums'] }}
                  >
                    {bestValue(m.key, m.value, locale, imperial)}
                  </Text>
                </View>
                <Muted style={{ fontSize: 13 }}>
                  {t(m.scope === 'all' ? 'progress.allTimeBest' : 'progress.yearBest')}
                </Muted>
              </View>
            ))}
          </View>
        )}
      </Card>
    </Tappable>
  );
}
