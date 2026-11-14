/**
 * Apps and devices (as Strava's "Other services"): where Zankup gets activities and health data from,
 * whether each is connected, and the AI assistants, each opening where it is set up.
 */
import { healthSettingsQuery, sourcesQuery } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Text } from 'react-native';

import { useApi } from '../src/auth';
import { Icon, type IconName } from '../src/Icon';
import { t } from '../src/i18n';
import { useTheme } from '../src/theme';
import { accent, Card, MenuRow, Screen } from '../src/ui';

type Item = { icon: IconName; label: string; note: string; to: string; connected?: boolean };

export default function SourcesScreen() {
  const api = useApi();
  const c = useTheme();
  const garmin = useQuery(sourcesQuery(api)).data?.find((s) => s.kind === 'garmin');
  const health = useQuery(healthSettingsQuery(api)).data?.sources ?? [];
  const on = (source: string) => health.some((s) => s.source === source && s.connected);
  const groups: [string, Item[]][] = [
    [
      t('sources.devices'),
      [
        {
          icon: 'sync',
          label: 'Garmin Connect',
          note: t('sources.garminNote'),
          to: '/settings/account',
          connected: !!garmin?.configured && !!garmin.last_sync_at,
        },
        { icon: 'upload', label: t('sources.import'), note: t('sources.importNote'), to: '/import' },
      ],
    ],
    [
      t('sources.tools'),
      [{ icon: 'smart_toy', label: t('assistants.title'), note: t('sources.aiNote'), to: '/assistants' }],
    ],
    [
      t('sources.services'),
      [
        {
          icon: 'favorite',
          label: 'Google Health',
          note: t('sources.googleNote'),
          to: '/settings/health',
          connected: on('google_health'),
        },
        {
          icon: 'ecg_heart',
          label: 'Health Connect',
          note: t('sources.hcNote'),
          to: '/settings/health',
          connected: on('health_connect'),
        },
      ],
    ],
  ];
  return (
    <Screen header>
      {groups.map(([title, items]) => (
        <Card key={title} style={{ paddingVertical: 0, gap: 0 }}>
          <Text style={{ color: c.fg, fontSize: 14, fontWeight: '700', paddingTop: 14 }}>{title}</Text>
          {items.map((item, i) => (
            <MenuRow
              key={item.label}
              icon={<Icon name={item.icon} color={c.fg} />}
              label={item.label}
              note={item.note}
              onPress={() => router.push(item.to as never)}
              last={i === items.length - 1}
              end={item.connected ? <Icon name="check" color={accent(c)} size={24} /> : undefined}
            />
          ))}
        </Card>
      ))}
    </Screen>
  );
}
