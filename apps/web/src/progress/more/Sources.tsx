/**
 * Data sources (OS-1005), as the app's and Strava's: the devices and services that recorded something
 * in the last 30 days and when each was last used, then "Apps and devices": where Zankup gets
 * activities and health data from, whether each is connected, and the AI assistants, each opening
 * where it is set up in Settings.
 */
import {
  devicesUsedQuery,
  healthSettingsQuery,
  sourcesQuery,
  type MaterialIcon as MaterialIconName,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../../api';
import { t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle } from '../../ui';
import { deviceName, lastUsed } from './data';
import '../more.css';

export function SourcesCard() {
  const devices = useQuery(devicesUsedQuery(api, 30));
  if (devices.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={4} />
      </Pulse>
    );
  return (
    <div className="panel sourcescard">
      <CardTitle>{t('sources.title')}</CardTitle>
      <p className="note">{t('sources.note')}</p>
      <ul className="sourcerows">
        {(devices.data ?? []).map((d) => (
          <li key={`${d.kind}-${deviceName(d)}`}>
            <MaterialIcon name="devices" />
            <span>
              <b>{deviceName(d)}</b>
              <small>{t('sources.lastUsed', { when: lastUsed(d.last_used) })}</small>
            </span>
          </li>
        ))}
        <li>
          <Link to="/progress/sources">
            <MaterialIcon name="link" />
            <span>
              <b>{t('sources.connect')}</b>
              <small>{t('sources.connectNote')}</small>
            </span>
          </Link>
        </li>
      </ul>
      <Link to="/progress/sources" className="sourcesmanage">
        {t('sources.manage')}
      </Link>
    </div>
  );
}

type Item = { icon: MaterialIconName; label: string; note: string; to: string; connected?: boolean };

export function SourcesPage() {
  const sources = useQuery(sourcesQuery(api));
  const health = useQuery(healthSettingsQuery(api));
  const garmin = sources.data?.find((s) => s.kind === 'garmin');
  const on = (source: string) => (health.data?.sources ?? []).some((s) => s.source === source && s.connected);
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
        { icon: 'upload', label: t('sources.import'), note: t('sources.importNote'), to: '/settings/import' },
      ],
    ],
    [
      t('sources.tools'),
      [{ icon: 'smart_toy', label: t('assistants.title'), note: t('sources.aiNote'), to: '/settings/ai' }],
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
    <section className="page sources">
      <div className="titlebar">
        <h1>{t('sources.manageTitle')}</h1>
      </div>
      {sources.isPending || health.isPending ? (
        <Pulse className="sourcegroups">
          <CardSkeleton lines={2} />
          <CardSkeleton lines={1} />
          <CardSkeleton lines={2} />
        </Pulse>
      ) : (
        <div className="sourcegroups">
          {groups.map(([title, items]) => (
            <nav key={title} className="panel menu sourcegroup" aria-label={title}>
              <h2>{title}</h2>
              {items.map((item) => (
                <Link key={item.label} to={item.to}>
                  <MaterialIcon name={item.icon} />
                  <span>
                    <b>{item.label}</b>
                    <small>{item.note}</small>
                  </span>
                  {item.connected && (
                    <span className="sourceon" role="img" aria-label={t('sources.connected')}>
                      <MaterialIcon name="check" />
                    </span>
                  )}
                  <i aria-hidden="true">›</i>
                </Link>
              ))}
            </nav>
          ))}
        </div>
      )}
    </section>
  );
}
