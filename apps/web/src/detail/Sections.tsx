/**
 * The sections the activity's page puts between its charts (OS-1006, as the app's Sections): heart-rate
 * zones (the engine's, or from max HR), power zones and curve, the route this activity repeats and its
 * segments. Zones as thick bars with the time beside them (#283).
 */
import {
  curvesQuery,
  formatDuration,
  themes,
  timeInZones,
  ZONE_SHARES,
  zoneLimits,
  type ActivityDetail,
  type Series,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../api';
import { t } from '../i18n';
import { CurveChart } from '../stats/charts';
import { useResolvedTheme } from '../theme';
import { CardTitle } from '../ui';
import { ZoneRows } from './Context';

/** "114–132 bpm", the last one "171+ bpm". */
const range = (limits: number[], i: number, unit: string) =>
  `${limits[i]}${i < limits.length - 1 ? `–${(limits[i + 1] ?? 0) - 1}` : '+'} ${unit}`;

export function HrZones({
  series,
  activity,
  hrMax,
  estimated,
}: {
  series: Series;
  activity: ActivityDetail;
  hrMax: number | null;
  estimated: boolean;
}) {
  if (!series.hr?.some((v) => v != null)) return null;
  const engine =
    activity.metrics?.hr_zones && activity.metrics.hr_zone_bounds
      ? { seconds: activity.metrics.hr_zones, bounds: activity.metrics.hr_zone_bounds }
      : null;
  if (!hrMax && !engine) {
    return (
      <section className="panel">
        <CardTitle>{t('activity.zones')}</CardTitle>
        <p className="note">{t('activity.zonesNone')}</p>
      </section>
    );
  }
  // The engine's zones follow your zone model (OS-301); without them, share of max HR here.
  const seconds = engine?.seconds ?? timeInZones(series, hrMax ?? 190);
  const limits = engine?.bounds ?? zoneLimits(hrMax ?? 190);
  return (
    <section className="panel zones">
      <CardTitle>{t('activity.zones')}</CardTitle>
      <ZoneRows kind="hr" seconds={seconds} range={(i) => range(limits, i, 'bpm')} />
      {!engine && hrMax && (
        <p className="note">
          {t('activity.zonesNote', {
            low: Math.round((ZONE_SHARES[0] ?? 0) * 100),
            high: Math.round((ZONE_SHARES.at(-1) ?? 0) * 100),
            hrMax,
          })}
          {estimated && ` ${t('activity.zonesEstimate', { hrMax })}`}
        </p>
      )}
    </section>
  );
}

export function PowerZones({ activity }: { activity: ActivityDetail }) {
  const zones = activity.metrics?.power_zones;
  const bounds = activity.metrics?.power_zone_bounds;
  if (!zones?.length || !bounds) return null;
  return (
    <section className="panel zones">
      <CardTitle>{t('activity.powerZones')}</CardTitle>
      <ZoneRows kind="power" seconds={zones} range={(i) => range(bounds, i, 'W')} />
    </section>
  );
}

/** The best power for each duration in this activity, against the best of all time. */
export function PowerCurve({ activity }: { activity: ActivityDetail }) {
  const theme = useResolvedTheme();
  const points = activity.metrics?.power_curve ?? [];
  const allTime = useQuery({
    ...curvesQuery(api, 'power', { from: '2000-01-01' }),
    enabled: points.length > 0,
  });
  if (!points.length || !allTime.data) return null;
  const curve = points.map((c) => ({
    duration_s: c.duration_s,
    value: c.value,
    activity_id: activity.id,
    day: activity.start_utc.slice(0, 10),
  }));
  return (
    <section className="panel">
      <CardTitle>{t('activity.powerCurve')}</CardTitle>
      <CurveChart
        period={curve}
        allTime={allTime.data}
        theme={theme}
        format={(v) => `${Math.round(v)} W`}
        colour={themes[theme].ride}
      />
    </section>
  );
}

/** The route this activity repeats and the segments it went through (OS-312, OS-325). */
export function RouteAndSegments({ activity }: { activity: ActivityDetail }) {
  const segments = activity.segments ?? [];
  if (!activity.route_group && !segments.length) return null;
  return (
    <>
      {activity.route_group && (
        <p className="note routeinfo">
          <Link to={`/routes/${activity.route_group.group}`}>
            {t('activity.routeRank', { count: activity.route_group.count, rank: activity.route_group.rank })}{' '}
            ›
          </Link>
        </p>
      )}
      {segments.length > 0 && (
        <section className="panel">
          <CardTitle>{t('activity.segmentsTitle')}</CardTitle>
          <ul className="rows">
            {segments.map((s) => (
              <li key={s.segment_id}>
                <Link to={`/segments/${s.segment_id}`}>
                  <b>{s.name}</b>
                </Link>
                <span className="num">
                  {formatDuration(s.elapsed_s)} · {s.rank === 1 ? '🏆 ' : ''}
                  {t('activity.segmentRank', {
                    rank: s.rank,
                    count: s.count,
                    best: formatDuration(s.best_s),
                  })}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
