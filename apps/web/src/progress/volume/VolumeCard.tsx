/**
 * Progress's first card (as the app's and Strava's): a chip per sport, this week's totals (or those of
 * the week under the pointer) and the last 12 weeks of the sport's main measure, with the way into the
 * detail page.
 */
import {
  addDays,
  bucketValue,
  isoDay,
  metricsFor,
  monthLabels,
  sportsIn,
  SPORTS,
  volumeQuery,
  weekAt,
  weekTitle,
  type Bucket,
  type VolumeMetric,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { api } from '../../api';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle } from '../../ui';
import { asSport, sportIcon, useVolumeFormat } from './format';
import { VolumeChart } from './VolumeChart';

export function VolumeCard() {
  const format = useVolumeFormat();
  const [today] = useState(() => isoDay(new Date()));
  const volume = useQuery(volumeQuery(api, { from: weekAt(11, today), to: addDays(today, 1) }, 'week'));
  const [sport, setSport] = useState<string | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  if (volume.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={6} />
      </Pulse>
    );
  const buckets = (volume.data ?? []) as Bucket[];
  const sports = sportsIn(buckets, SPORTS, asSport);
  const metrics = metricsFor(sport);
  const chartMetric = metrics[0] ?? 'time';
  const valueOf = (b: Bucket, m: VolumeMetric) => bucketValue(b, m, sport, asSport);
  const last = buckets.length - 1;
  const index = hovered ?? last;
  const week = buckets[index];
  const values = buckets.map((b) => valueOf(b, chartMetric));
  const ticks = format.ticks(Math.max(...values, 0), chartMetric, 2);

  const chip = (s: string | null) => (
    <button
      key={s ?? 'all'}
      type="button"
      className="chip"
      aria-pressed={s === sport}
      onClick={() => {
        setSport(s);
        setHovered(null);
      }}
    >
      <MaterialIcon name={sportIcon(s)} />
      {s ? t(`sport.${asSport(s)}`) : t('progress.allSports')}
    </button>
  );

  return (
    <article className="panel vcard">
      <div className="chips vchips" role="group" aria-label={t('progress.chooseSport')}>
        {chip(null)}
        {sports.map(chip)}
      </div>
      <CardTitle>
        {index === last ? t('progress.thisWeek') : week ? weekTitle(week.start, locale) : ''}
      </CardTitle>
      <dl className="vfigs">
        {metrics.map((m) => (
          <div key={m}>
            <dt>{t(`progress.metricShort.${m}`)}</dt>
            <dd className="num">{format.value(m, week ? valueOf(week, m) : 0)}</dd>
          </div>
        ))}
      </dl>
      <p className="vsub">{t('progress.last12Weeks')}</p>
      <VolumeChart
        values={values}
        ticks={ticks}
        tickLabel={(v) => format.tick(chartMetric, v)}
        labels={monthLabels(
          buckets.map((b) => b.start),
          locale,
        )}
        onHover={setHovered}
        height={190}
        summary={`${t('progress.last12Weeks')}: ${values.map((v) => format.value(chartMetric, v)).join(', ')}`}
      />
      <Link className="vmore" to={sport ? `/progress/volume?sport=${sport}` : '/progress/volume'}>
        {t('progress.moreDetail')}
      </Link>
    </article>
  );
}
