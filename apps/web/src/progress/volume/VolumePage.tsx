/**
 * Progress in detail, from the Volume card (the app's progress-detail, as Strava's): a sport (chosen in
 * a dialog with a search), what to measure (time, distance, elevation or activities), a range from 7
 * days to a year, its total against the span before it or the same span some years back, the chart and
 * a sentence on how it went. Hovering the chart reads a day or week; a click (or its box) opens what was
 * done in it. On a desktop the comparison and the sentence sit beside the chart.
 */
import {
  addDays,
  bucketValue,
  calendarQuery,
  comparableYears,
  glanceText,
  isoDay,
  metricsFor,
  mondayOf,
  percentChange,
  PROGRESS_RANGES,
  rangeLabels,
  sportsIn,
  SPORTS,
  streakQuery,
  volumeQuery,
  volumeRange,
  weekTitle,
  yearsBack,
  type Bucket,
  type CalendarDay,
  type DayRange,
  type ProgressRange,
  type VolumeMetric,
} from '@zankup/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { Link, useSearchParams } from 'react-router';

import { api } from '../../api';
import { Modal } from '../../detail/Tools';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { Bone, ChartSkeleton, Pulse } from '../../Skeleton';
import { SportIcon } from '../../SportIcon';
import { InfoTip } from '../../ui';
import { asSport, longDate, shortDate, sportIcon, useVolumeFormat } from './format';
import { VolumeChart } from './VolumeChart';

export function VolumePage() {
  const format = useVolumeFormat();
  const [params] = useSearchParams();
  const asked = params.get('sport');
  const initial = asked && (SPORTS as readonly string[]).includes(asked) ? asked : null;
  const [today] = useState(() => isoDay(new Date()));
  const [sport, setSport] = useState<string | null>(initial);
  const [metric, setMetric] = useState<VolumeMetric>(metricsFor(initial)[0] ?? 'time');
  const [range, setRange] = useState<ProgressRange>('3m');
  const [compare, setCompare] = useState<number | null>(null);
  const [picked, setPicked] = useState<number | null>(null);
  const [dialog, setDialog] = useState<'sport' | 'compare' | 'bucket' | null>(null);

  const { current, previous, unit } = volumeRange(range, today);
  const against: DayRange = compare ? yearsBack(current, compare) : previous;
  const now = useQuery({ ...volumeQuery(api, current, unit), placeholderData: keepPreviousData });
  const before = useQuery({ ...volumeQuery(api, against, unit), placeholderData: keepPreviousData });
  const first = useQuery(streakQuery(api)).data?.first_activity;
  const buckets = (now.data ?? []) as Bucket[];
  const earlier = (before.data ?? []) as Bucket[];
  const value = (b: Bucket) => bucketValue(b, metric, sport, asSport);
  const values = buckets.map(value);
  const previousValues = earlier.map(value);
  const total = values.reduce((a, b) => a + b, 0);
  const previousTotal = previousValues.reduce((a, b) => a + b, 0);
  const pct = percentChange(total, previousTotal);
  const metrics: VolumeMetric[] = [...metricsFor(sport), 'count'];
  const years = comparableYears(current, first);
  const bucket = picked != null ? buckets[picked] : undefined;

  const end = unit === 'day' || range === 'ytd' ? t('progress.today') : longDate(addDays(mondayOf(today), 6));
  const ticks = format.ticks(Math.max(0, ...values, ...(compare ? previousValues : [])), metric, 3);
  const periodOf = (b: Bucket) => (unit === 'day' ? longDate(b.start) : weekTitle(b.start, locale));
  const yearsLabel = (k: number) => (k === 1 ? t('progress.lastYear') : t('progress.yearsAgo', { count: k }));
  const glance = glanceText({
    range,
    compare,
    total,
    previousTotal,
    format: (v) => format.value(metric, v, 1),
    t,
  });
  const reset = () => setPicked(null);

  const change = (p: number | null) =>
    p == null ? null : (
      <b className={`num vchange${p > 0 ? ' up' : ''}`}>
        {p > 0 ? '▲' : p < 0 ? '▼' : ''} {Math.abs(p)} %
      </b>
    );
  const dot = (colour: string) => <i className="vdot" style={{ '--dot': colour } as CSSProperties} />;
  const span = (r: DayRange) => `${shortDate(r.from)} - ${shortDate(addDays(r.to, -1))}`;

  return (
    <section className="page vpage">
      <div className="titlebar">
        <h1>{t('nav.progress')}</h1>
        <InfoTip text={t('progress.volumeInfo')} />
      </div>

      <div className="chips vchips">
        <button type="button" className="chip on" aria-haspopup="dialog" onClick={() => setDialog('sport')}>
          <MaterialIcon name={sportIcon(sport)} />
          {sport ? t(`sport.${asSport(sport)}`) : t('progress.allSports')}
          <MaterialIcon name="keyboard_arrow_down" />
        </button>
        {metrics.map((m) => (
          <button
            key={m}
            type="button"
            className="chip"
            aria-pressed={m === metric}
            onClick={() => {
              setMetric(m);
              reset();
            }}
          >
            {t(m === 'ascent' ? 'progress.metricLong.ascent' : `progress.metricShort.${m}`)}
          </button>
        ))}
      </div>

      <div className="vlayout">
        <div className="panel vmain">
          <h2 className="vlabel">{t(`progress.total.${metric}`)}</h2>
          {compare ? (
            <div className="vtotals">
              <div>
                <span className="vbig small num">{format.value(metric, total, 1)}</span> {change(pct)}
                <p className="note">
                  {dot('var(--accent)')}
                  {span(current)}
                </p>
              </div>
              <div className="end">
                <span className="vbig small num">{format.value(metric, previousTotal, 1)}</span>
                <p className="note">
                  {dot('var(--fatigue)')}
                  {span(against)}
                </p>
              </div>
            </div>
          ) : (
            <div className="vtotals">
              <div>
                <span className="vbig num">{format.value(metric, total, 1)}</span>
                <p className="note">
                  {longDate(current.from)} - {end}
                </p>
              </div>
              {pct != null && (
                <div className="end">
                  {change(pct)}
                  <p className="note">{t(`progress.vs.${range}`)}</p>
                </div>
              )}
            </div>
          )}

          <div className="vreadout">
            {bucket && (
              <button
                type="button"
                className="vbox"
                aria-label={`${t('progress.weekDetail')}: ${periodOf(bucket)}`}
                onClick={() => setDialog('bucket')}
              >
                <span>
                  <b className="num">{format.value(metric, value(bucket), 1)}</b>
                  <small>{periodOf(bucket)}</small>
                </span>
                <MaterialIcon name="chevron_right" />
              </button>
            )}
          </div>
          {now.isPending ? (
            <ChartSkeleton height={260} />
          ) : (
            <VolumeChart
              values={values}
              previous={compare ? previousValues : null}
              ticks={ticks}
              tickLabel={(v) => format.tick(metric, v)}
              labels={rangeLabels(
                range,
                buckets.map((b) => b.start),
                locale,
              )}
              onHover={(i) => {
                if (i != null) setPicked(i);
              }}
              onPick={(i) => {
                setPicked(i);
                setDialog('bucket');
              }}
              height={280}
              summary={`${t(`progress.total.${metric}`)}: ${format.value(metric, total, 1)}`}
            />
          )}

          <div className="vranges" role="radiogroup" aria-label={t('stats.period')}>
            {PROGRESS_RANGES.map((r) => (
              <button
                key={r}
                type="button"
                role="radio"
                aria-checked={r === range}
                onClick={() => {
                  setRange(r);
                  reset();
                }}
              >
                {t(`progress.range.${r}`)}
              </button>
            ))}
          </div>
        </div>

        <div className="vside">
          <div className="vcompare">
            <button type="button" className="vcomparebtn" onClick={() => setDialog('compare')}>
              <i className="vswatch" aria-hidden="true" />
              <span>{compare ? yearsLabel(compare) : t('progress.compare')}</span>
              {!compare && <MaterialIcon name="keyboard_arrow_down" />}
            </button>
            {compare ? (
              <button
                type="button"
                className="iconbtn"
                aria-label={t('progress.clearCompare')}
                title={t('progress.clearCompare')}
                onClick={() => setCompare(null)}
              >
                <MaterialIcon name="close" />
              </button>
            ) : null}
          </div>
          <div className="panel vglance">
            <h2>
              <MaterialIcon name={sportIcon(sport)} />
              {t('progress.glanceTitle')}
            </h2>
            <p>{glance}</p>
          </div>
        </div>
      </div>

      {dialog === 'sport' && (
        <SportDialog
          sport={sport}
          found={sportsIn([...buckets, ...earlier], SPORTS, asSport)}
          onClose={() => setDialog(null)}
          onPick={(s) => {
            setSport(s);
            if (metric !== 'count' && !metricsFor(s).includes(metric)) setMetric(metricsFor(s)[0] ?? 'time');
            reset();
            setDialog(null);
          }}
        />
      )}
      {dialog === 'compare' && (
        <Modal title={t('progress.compareWith')} onClose={() => setDialog(null)}>
          <div className="vchoices" role="radiogroup" aria-label={t('progress.compareWith')}>
            {[null, ...years].map((k) => (
              <button
                key={k ?? 0}
                type="button"
                role="radio"
                aria-checked={compare === k}
                onClick={() => {
                  setCompare(k);
                  setDialog(null);
                }}
              >
                <span>
                  {k ? yearsLabel(k) : t('progress.compareNone')}
                  {k ? (
                    <small>
                      {longDate(yearsBack(current, k).from)} -{' '}
                      {longDate(addDays(yearsBack(current, k).to, -1))}
                    </small>
                  ) : null}
                </span>
                <i className="vradio" aria-hidden="true" />
              </button>
            ))}
          </div>
        </Modal>
      )}
      {dialog === 'bucket' && bucket && (
        <BucketDialog
          from={bucket.start}
          to={addDays(bucket.start, unit === 'day' ? 1 : 7)}
          title={periodOf(bucket)}
          sport={sport}
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  );
}

/** "Filter by sport": every sport, or one, with a search over their names (the app's SportSheet). */
function SportDialog({
  sport,
  found,
  onPick,
  onClose,
}: {
  sport: string | null;
  found: string[];
  onPick: (sport: string | null) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState('');
  const needle = query.trim().toLocaleLowerCase(locale);
  const list = (found.length ? found : [...SPORTS]).filter((s) =>
    t(`sport.${asSport(s)}`)
      .toLocaleLowerCase(locale)
      .includes(needle),
  );
  const row = (s: string | null, title: string, note?: string) => (
    <button key={s ?? 'all'} type="button" role="radio" aria-checked={s === sport} onClick={() => onPick(s)}>
      <MaterialIcon name={sportIcon(s)} />
      <span>
        {title}
        {note ? <small>{note}</small> : null}
      </span>
      {s === sport && <MaterialIcon name="check" />}
    </button>
  );
  return (
    <Modal title={t('progress.filterSport')} onClose={onClose}>
      <label className="vsearch">
        <MaterialIcon name="search" />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('nav.search')}
          aria-label={t('nav.search')}
        />
      </label>
      <div className="vchoices sports" role="radiogroup" aria-label={t('progress.filterSport')}>
        {!needle && row(null, t('progress.allSports'), t('progress.allSportsNote'))}
        {list.map((s) => row(s, t(`sport.${asSport(s)}`)))}
      </div>
    </Modal>
  );
}

/** A day's or week's totals and its activities, each opening its page. */
function BucketDialog({
  from,
  to,
  title,
  sport,
  onClose,
}: {
  from: string;
  to: string;
  title: string;
  sport: string | null;
  onClose: () => void;
}) {
  const format = useVolumeFormat();
  const calendar = useQuery(calendarQuery(api, { from, to }));
  const activities = ((calendar.data ?? []) as CalendarDay[])
    .flatMap((d) => d.activities.map((a) => ({ ...a, day: d.day })))
    .filter((a) => !sport || asSport(a.sport) === sport)
    .reverse();
  const stats = (distance: number, ascent: number, time: number) => (
    <dl className="vfigs">
      {(
        [
          ['distance', distance],
          ['ascent', ascent],
          ['time', time],
        ] as const
      )
        .filter(([m, v]) => m === 'time' || v > 0)
        .map(([m, v]) => (
          <div key={m}>
            <dt>{t(`progress.metricShort.${m}`)}</dt>
            <dd className="num">{format.value(m, v)}</dd>
          </div>
        ))}
    </dl>
  );
  const sum = (k: 'distance_m' | 'ascent_m' | 'moving_s') => activities.reduce((s, a) => s + (a[k] ?? 0), 0);
  return (
    <Modal title={title} onClose={onClose}>
      {calendar.isPending ? (
        <Pulse>
          <Bone height={44} />
          <Bone height={64} style={{ marginTop: 12 }} />
        </Pulse>
      ) : (
        <>
          <div className="vbuckettotals">{stats(sum('distance_m'), sum('ascent_m'), sum('moving_s'))}</div>
          <ul className="vacts">
            {activities.map((a) => (
              <li key={a.id}>
                <Link to={`/activities/${a.id}`}>
                  <span className="vacthead">
                    <SportIcon sport={asSport(a.sport)} />
                    <span>
                      <b>{a.name ?? t(`sport.${asSport(a.sport)}`)}</b>
                      <small>{longDate(a.day)}</small>
                    </span>
                  </span>
                  {stats(a.distance_m ?? 0, a.ascent_m ?? 0, a.moving_s)}
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </Modal>
  );
}
