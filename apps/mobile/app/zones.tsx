/**
 * Training zones, from Progress (as Strava's): the time in each HR, pace or power zone over 7 days to a
 * year, for every sport or one, the zone with most time and how its share moved against the span
 * before, and a sentence on what changed most. The pencil opens the zones' settings.
 */
import {
  zonesQuery,
  isoDay,
  PROGRESS_RANGES,
  volumeRange,
  type ProgressRange,
  defaultZoneKind,
  glance,
  shareChange,
  topZone,
  zoneKinds,
  type ZoneKind,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../src/auth';
import { Icon } from '../src/Icon';
import { locale, t } from '../src/i18n';
import { asSport } from '../src/prefs';
import { Chip, CHIP_ICON } from '../src/progress/Chip';
import { Panel } from '../src/progress/Panel';
import { SportSheet } from '../src/progress/SportSheet';
import { sportIcon } from '../src/progress/VolumeCard';
import { ShareBadge, ZoneRows } from '../src/progress/ZonesCard';
import { useTheme } from '../src/theme';
import { accent, Card, InfoButton, Muted, onAccent, Screen } from '../src/ui';

const KIND_LABEL = {
  hr: 'progress.heartRate',
  power: 'progress.power',
  pace: 'progress.pace',
} as const;

const longDate = (iso: string) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });

export default function ZonesScreen() {
  const api = useApi();
  const c = useTheme();
  const today = isoDay(new Date());
  const [sport, setSport] = useState<string | null>(null);
  const [chosen, setKind] = useState<ZoneKind>('hr');
  // Only the zones the sport has (a run: HR and pace; a ride: HR and power; the rest: HR).
  const kinds = zoneKinds(sport);
  const kind = kinds.includes(chosen) ? chosen : 'hr';
  const [range, setRange] = useState<ProgressRange>('1m');
  const [picking, setPicking] = useState(false);
  const { current } = volumeRange(range, today);
  // Loaded in the background: while a new range or sport loads, the zones shown stay (when they are of
  // the same kind) and the bars move to the new times, with no skeleton in between.
  const zones = useQuery({
    ...zonesQuery(api, kind, current, sport ? [sport] : undefined),
    placeholderData: (before) => (before?.kind === kind ? before : undefined),
  });
  const loaded = zones.data != null && !zones.isPlaceholderData;
  const seconds = zones.data?.seconds ?? Array<number>(kind === 'power' ? 7 : 5).fill(0);
  const previous = zones.data?.previous ?? [];
  const top = topZone(seconds);
  const change = top ? shareChange(seconds, previous, top.index) : null;
  const note = glance(seconds, previous);

  return (
    <Screen header>
      <Stack.Screen
        options={{
          headerRight: () => (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 18 }}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={t('progress.editZones')}
                hitSlop={10}
                onPress={() => router.push('/settings/heart')}
              >
                <Icon name="edit" color={c.fg} size={24} />
              </Pressable>
              <InfoButton text={t('progress.zonesInfo')} />
            </View>
          ),
        }}
      />
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        <Chip
          on
          role="button"
          label={sport ? t(`sport.${asSport(sport)}`) : t('progress.allSports')}
          icon={(colour) => <Icon name={sportIcon(sport)} color={colour} size={CHIP_ICON} />}
          onPress={() => setPicking(true)}
        />
        {kinds.map((k) => (
          <Chip key={k} on={kind === k} label={t(KIND_LABEL[k])} onPress={() => setKind(k)} />
        ))}
      </View>

      <>
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 12 }}>
          <View style={{ flex: 1 }}>
            <Text numberOfLines={1} style={{ color: c.fg, fontSize: 24, fontWeight: '800' }}>
              {top
                ? t('progress.zonesTopLong', { share: top.share, zone: top.index + 1 })
                : zones.data || zones.isError
                  ? t('progress.noZones')
                  : ' '}
            </Text>
            <Muted style={{ fontSize: 13 }}>
              {longDate(current.from)} – {t('progress.today')}
            </Muted>
          </View>
          {change != null && change !== 0 && (
            <View style={{ alignItems: 'flex-end', gap: 2 }}>
              <ShareBadge points={change} />
              <Muted style={{ fontSize: 13 }}>{t('progress.vsBefore')}</Muted>
            </View>
          )}
        </View>
        <ZoneRows kind={kind} seconds={seconds} bounds={zones.data?.bounds ?? []} />
        {loaded && kind === 'power' && !zones.data?.bounds.length && <Muted>{t('progress.noFtp')}</Muted>}
        {loaded && kind === 'pace' && !zones.data?.bounds.length && (
          <Muted>{t('progress.noThreshold')}</Muted>
        )}
      </>

      <View style={{ flexDirection: 'row', gap: 4, marginTop: 4 }}>
        {PROGRESS_RANGES.map((r) => {
          const on = r === range;
          return (
            <Pressable
              key={r}
              accessibilityRole="radio"
              accessibilityState={{ checked: on }}
              onPress={() => setRange(r)}
              style={{
                flex: 1,
                alignItems: 'center',
                paddingVertical: 9,
                borderRadius: 999,
                backgroundColor: on ? accent(c) : 'transparent',
              }}
            >
              <Text style={{ color: on ? onAccent(c) : accent(c), fontSize: 14, fontWeight: '800' }}>
                {t(`progress.range.${r}`)}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {note && (
        <Card style={{ gap: 10 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <Icon name={sportIcon(sport)} color={c.fg} size={22} />
            <Text style={{ color: c.fg, fontSize: 14 }}>{t('progress.zonesGlance')}</Text>
          </View>
          <Text style={{ color: c.fg, fontSize: 15, lineHeight: 22 }}>
            {t(note.up ? 'progress.zoneUp' : 'progress.zoneDown', {
              zone: note.zone + 1,
              percent: note.percent,
              since: t(`progress.zoneSince.${range}`),
            })}{' '}
            {t(`progress.zoneAdvice.${note.band}.${note.up ? 'up' : 'down'}`)}
          </Text>
        </Card>
      )}

      <Panel open={picking} onClose={() => setPicking(false)}>
        <SportSheet
          sport={sport}
          found={[]}
          onPick={(s) => {
            setSport(s);
            // Each sport opens on its own zones: a run on pace, a ride on power.
            setKind(defaultZoneKind(s));
            setPicking(false);
          }}
        />
      </Panel>
    </Screen>
  );
}
