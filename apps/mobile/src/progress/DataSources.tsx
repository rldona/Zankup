/**
 * Data sources (as Strava's): the devices and services that recorded something in the last 30 days,
 * when each was last used, and a way into the apps and devices.
 */
import { daysAgo, devicesUsedQuery, SOURCE_NAMES, type DeviceUse } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, Text, View } from 'react-native';

import { useApi } from '../auth';
import { Icon } from '../Icon';
import { t } from '../i18n';
import { CardSkeleton } from '../Skeleton';
import { useTheme } from '../theme';
import { accent, Card, Muted } from '../ui';

/** The device's name, or its source's when the source names none. */
export const deviceName = (d: DeviceUse) => d.name ?? SOURCE_NAMES[d.source] ?? t('sources.file');

/** "hoy", "Hace 1 día", "Hace 2 semanas". */
export function lastUsed(iso: string, now = new Date()): string {
  const days = daysAgo(iso, now);
  if (days === 0) return t('sources.today');
  if (days < 7) return t('sources.daysAgo', { count: days });
  return t('sources.weeksAgo', { count: Math.floor(days / 7) });
}

function Row({
  icon,
  title,
  note,
  onPress,
}: {
  icon: 'devices' | 'link';
  title: string;
  note: string;
  onPress?: () => void;
}) {
  const c = useTheme();
  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      disabled={!onPress}
      onPress={onPress}
      style={{ flexDirection: 'row', alignItems: 'center', gap: 16, paddingVertical: 4 }}
    >
      <Icon name={icon} color={c.fg} size={26} />
      <View style={{ flex: 1 }}>
        <Text numberOfLines={1} style={{ color: c.fg, fontSize: 16 }}>
          {title}
        </Text>
        <Text numberOfLines={1} style={{ color: c.muted, fontSize: 13 }}>
          {note}
        </Text>
      </View>
    </Pressable>
  );
}

export function DataSourcesCard() {
  const api = useApi();
  const c = useTheme();
  const devices = useQuery(devicesUsedQuery(api, 30));
  if (devices.isPending) return <CardSkeleton lines={4} />;
  const open = () => router.push('/sources');
  return (
    <Card>
      <Text style={{ color: c.fg, fontSize: 16, fontWeight: '700' }}>{t('sources.title')}</Text>
      <Muted style={{ fontSize: 14 }}>{t('sources.note')}</Muted>
      <View style={{ gap: 14, marginTop: 6 }}>
        {(devices.data ?? []).map((d) => (
          <Row
            key={`${d.kind}-${deviceName(d)}`}
            icon="devices"
            title={deviceName(d)}
            note={t('sources.lastUsed', { when: lastUsed(d.last_used) })}
          />
        ))}
        <Row icon="link" title={t('sources.connect')} note={t('sources.connectNote')} onPress={open} />
      </View>
      <Pressable accessibilityRole="button" onPress={open} style={{ alignSelf: 'flex-end', paddingTop: 8 }}>
        <Text style={{ color: accent(c), fontSize: 15, fontWeight: '700' }}>{t('sources.manage')}</Text>
      </Pressable>
    </Card>
  );
}
