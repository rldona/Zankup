import {
  curvesQuery,
  efficiencyQuery,
  engineQuery,
  monthsRange,
  fitnessQuery,
  formatHoursMinutes,
  formatters,
  periodOf,
  recordsQuery,
  summaryQuery,
  volumeQuery,
  yearsQuery,
  formatDuration,
  type FitnessDay,
  effortShown,
  compare,
  comparisonLabels,
} from '@zankup/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { api } from '../api';
import { locale, t } from '../i18n';
import { usePrefs } from '../prefs';
import { CurveChart, EfficiencyChart, FitnessChart, VolumeChart } from '../stats/charts';
import { effortName } from '../stats/format';
import { MaterialIcon } from '../MaterialIcon';
import { CardTitle, Menu } from '../ui';
import { useResolvedTheme } from '../theme';
import '../stats/stats.css';

const MODES = ['last12', 'year', 'month', 'all'] as const;
type Mode = (typeof MODES)[number];

/** A month counted from year 0 (so one step is always one month), and back. */
const monthIndex = (ym: string) => {
  const [y = 0, m = 1] = ym.split('-').map(Number);
  return y * 12 + m - 1;
};
const monthKey = (n: number) => `${Math.floor(n / 12)}-${String((n % 12) + 1).padStart(2, '0')}`;
const monthLabel = (n: number) =>
  new Date(Math.floor(n / 12), n % 12, 15)
    .toLocaleDateString(locale, { month: 'short', year: 'numeric' })
    .replace(' de ', ' ')
    .replace('.', '');

/**
 * The period in the URL (`?period=`): `last12`, `all`, a year (`2025`) or a run of months
 * (`2025-03..2025-06`, or one month `2025-03`). Without one, as the app (#285): months, from last month
 * to this one.
 */
function readPeriod(
  value: string | null,
  now: number,
): { mode: Mode; year: number; from: number; to: number } {
  const thisYear = Math.floor(now / 12);
  const months = /^(\d{4}-\d{2})(?:\.\.(\d{4}-\d{2}))?$/.exec(value ?? '');
  if (months) {
    const from = monthIndex(months[1] ?? '');
    const to = months[2] ? monthIndex(months[2]) : from;
    return { mode: 'month', year: Math.floor(to / 12), from: Math.min(from, to), to: Math.max(from, to) };
  }
  if (/^\d{4}$/.test(value ?? '')) return { mode: 'year', year: Number(value), from: now, to: now };
  if (value === 'all' || value === 'last12') return { mode: value, year: thisYear, from: now, to: now };
  return { mode: 'month', year: thisYear, from: now - 1, to: now };
}

/**
 * Stats (OS-311, OS-321, OS-320, OS-912, OS-1010), as the app's: the period right above everything (the
 * last 12 months, a year moved with arrows, a run of months from one to another, or all time), the
 * reviews and the heatmap in the header, then only what follows the period: the summary against last
 * year, its charts and its bests; and the way to routes, segments and Year by year. On a large screen
 * the cards sit two by two.
 */
export function Stats() {
  const [params, setParams] = useSearchParams();
  const [today] = useState(() => new Date());
  const now = today.getFullYear() * 12 + today.getMonth();
  const years = useQuery(yearsQuery(api));
  const first = years.data?.at(-1) ?? today.getFullYear();
  const shown = readPeriod(params.get('period'), now);
  const period = useMemo(
    () =>
      shown.mode === 'month'
        ? monthsRange(shown.from, shown.to)
        : periodOf(shown.mode === 'year' ? String(shown.year) : shown.mode, today, first),
    [shown.mode, shown.from, shown.to, shown.year, today, first],
  );
  const range = { from: period.from, to: period.to };
  const prefs = usePrefs();
  const format = useMemo(() => formatters(prefs), [prefs]);
  const theme = useResolvedTheme();
  const engine = useQuery({
    ...engineQuery(api),
    refetchInterval: (q) => (q.state.data?.running ? 3000 : false),
  });
  const go = (value: string) => setParams({ period: value }, { replace: true });
  const months = (from: number, to: number) =>
    go(from === to ? monthKey(from) : `${monthKey(from)}..${monthKey(to)}`);
  const choose = (mode: Mode) =>
    mode === 'year' ? go(String(shown.year)) : mode === 'month' ? months(shown.to, shown.to) : go(mode);
  const [reviews, setReviews] = useState(false);

  return (
    <section className="page stats">
      <div className="titlebar">
        <h1>{t('nav.stats')}</h1>
        <div className="menuwrap">
          <button
            type="button"
            className="iconbtn"
            aria-label={t('stats.reviews')}
            title={t('stats.reviews')}
            aria-expanded={reviews}
            onClick={() => setReviews(!reviews)}
          >
            <MaterialIcon name="summarize" />
          </button>
          {reviews && (
            <nav className="popmenu" aria-label={t('stats.reviews')}>
              <Link to="/review/month">{t('review.monthLink')}</Link>
              <Link to="/review/year">{t('review.yearLink')}</Link>
            </nav>
          )}
        </div>
        {/* The calendar and the heatmap: icons on a phone, labelled buttons from 600 px
            (docs/prototypes/web). */}
        <Link
          to="/stats/calendar"
          className="iconbtn labelled"
          aria-label={t('stats.calendar')}
          title={t('stats.calendar')}
        >
          <MaterialIcon name="calendar_month" />
          <span aria-hidden="true">{t('stats.calendar')}</span>
        </Link>
        <Link
          to="/stats/heatmap"
          className="iconbtn labelled"
          aria-label={t('stats.heatmap')}
          title={t('stats.heatmap')}
        >
          <MaterialIcon name="layers" />
          <span aria-hidden="true">{t('stats.heatmap')}</span>
        </Link>
      </div>
      <div className="periodbar">
        <div className="chips" role="radiogroup" aria-label={t('stats.period')}>
          {MODES.map((m) => (
            <button
              key={m}
              type="button"
              className="chip"
              role="radio"
              aria-checked={shown.mode === m}
              onClick={() => choose(m)}
            >
              {t(`stats.mode.${m}`)}
            </button>
          ))}
        </div>
        {shown.mode === 'year' && (
          <Stepper
            label={String(shown.year)}
            back={shown.year > first ? () => go(String(shown.year - 1)) : null}
            forward={shown.year < today.getFullYear() ? () => go(String(shown.year + 1)) : null}
          />
        )}
        {shown.mode === 'month' && (
          <div className="steppers">
            <Stepper
              label={monthLabel(shown.from)}
              back={shown.from > first * 12 ? () => months(shown.from - 1, shown.to) : null}
              forward={shown.from < shown.to ? () => months(shown.from + 1, shown.to) : null}
            />
            <span className="note">–</span>
            <Stepper
              label={monthLabel(shown.to)}
              back={shown.to > shown.from ? () => months(shown.from, shown.to - 1) : null}
              forward={shown.to < now ? () => months(shown.from, shown.to + 1) : null}
            />
          </div>
        )}
      </div>
      {engine.data?.running && (
        <p className="offline" role="status">
          {t('stats.recomputing', { done: engine.data.done, total: engine.data.total })}
        </p>
      )}

      {/* Only what follows the period (#318), the summary first (#314): the period's figures against the
          year before, then its charts, aerobic efficiency right under volume (#287), and its bests. Each
          keeps the last period on screen while the next one loads, so its chart slides (OS-1012). */}
      <div className="statgrid">
        <Summary range={range} format={format} />
        <FitnessSection range={range} theme={theme} />
        <Volume range={range} unit={period.unit} theme={theme} format={format} />
        <Efficiency range={range} theme={theme} />
        <Curves range={range} theme={theme} format={format} />
        <PeriodBests range={range} unit={format.unit} />
      </div>

      <h2>{t('stats.links')}</h2>
      <Menu
        label={t('stats.links')}
        items={[
          { to: '/routes', icon: 'route', label: t('stats.routes') },
          { to: '/segments', icon: 'timeline', label: t('stats.segments') },
          // Every year against the others: whole years, so not under the period's filter.
          { to: '/stats/years', icon: 'show_chart', label: t('stats.progress') },
        ]}
      />
    </section>
  );
}

/** The year or month shown, with an arrow on each side; an arrow at the end of the history is off. */
function Stepper({
  label,
  back,
  forward,
}: {
  label: string;
  back: (() => void) | null;
  forward: (() => void) | null;
}) {
  return (
    <div className="stepper">
      <button
        type="button"
        className="iconbtn"
        aria-label={t('stats.previous')}
        disabled={!back}
        onClick={back ?? undefined}
      >
        <MaterialIcon name="chevron_left" />
      </button>
      <b>{label}</b>
      <button
        type="button"
        className="iconbtn"
        aria-label={t('stats.next')}
        disabled={!forward}
        onClick={forward ?? undefined}
      >
        <MaterialIcon name="chevron_right" />
      </button>
    </div>
  );
}

function Summary({
  range,
  format,
}: {
  range: { from: string; to: string };
  format: ReturnType<typeof formatters>;
}) {
  const summary = useQuery({ ...summaryQuery(api, range), placeholderData: keepPreviousData });
  if (!summary.data) return null;
  const { current, previous } = summary.data;
  const labels = comparisonLabels(range, locale, t); // null for All: nothing to compare with
  const rows: [string, string, number, number][] = [
    [t('feed.title'), current.count.toLocaleString(locale), current.count, previous.count],
    [t('feed.stat.distance'), format.distance(current.distance_m), current.distance_m, previous.distance_m],
    [t('feed.stat.time'), formatHoursMinutes(current.moving_s), current.moving_s, previous.moving_s],
    [t('feed.stat.ascent'), format.elevation(current.ascent_m), current.ascent_m, previous.ascent_m],
    [t('home.load'), Math.round(current.load).toLocaleString(locale), current.load, previous.load],
  ];
  return (
    <section className="panel">
      <CardTitle info={t('stats.summaryNote')}>{t('stats.summaryTitle')}</CardTitle>
      {/* Each figure against the same period a year before, as the app: last year's bar, then this period's. */}
      <ul className="compare num">
        {rows.map(([label, value, now, before]) => {
          const { percent, now: mine, before: theirs } = compare(now, before);
          const tone = percent == null || percent === 0 ? '' : percent > 0 ? ' up' : ' down';
          return (
            <li key={label}>
              <span className="clabel">{label}</span>
              <b>{value}</b>
              {labels && percent ? ( // no pill when nothing changed
                <span className={`pill${tone}`}>
                  {t('stats.change', { sign: percent > 0 ? '+' : '', percent })}
                </span>
              ) : null}
              {labels && (
                <>
                  <i className="bar before" style={{ width: `${theirs * 100}%` }} />
                  <i className="bar now" style={{ width: `${mine * 100}%` }} />
                </>
              )}
            </li>
          );
        })}
      </ul>
      {labels && (
        <p className="comparekey note">
          <span>
            <i className="bar before" /> {labels[0]}
          </span>
          <span>
            <i className="bar now" /> {labels[1]}
          </span>
        </p>
      )}
    </section>
  );
}

function FitnessSection({
  range,
  theme,
}: {
  range: { from: string; to: string };
  theme: ReturnType<typeof useResolvedTheme>;
}) {
  const fitness = useQuery({ ...fitnessQuery(api, range), placeholderData: keepPreviousData });
  const days: FitnessDay[] = fitness.data ?? [];
  return (
    <section className="panel">
      <CardTitle info={t('stats.fitnessNote')}>{t('stats.fitness')}</CardTitle>
      {days.length ? <FitnessChart days={days} theme={theme} /> : <p className="note">{t('stats.empty')}</p>}
    </section>
  );
}

function Volume({
  range,
  unit,
  theme,
  format,
}: {
  range: { from: string; to: string };
  unit: 'day' | 'week' | 'month' | 'year';
  theme: ReturnType<typeof useResolvedTheme>;
  format: ReturnType<typeof formatters>;
}) {
  const [metric, setMetric] = useState<'time' | 'distance'>('time');
  const volume = useQuery({ ...volumeQuery(api, range, unit), placeholderData: keepPreviousData });
  const total = (volume.data ?? []).reduce(
    (s, b) => s + (metric === 'time' ? b.totals.moving_s : b.totals.distance_m),
    0,
  );
  return (
    <section className="panel">
      {/* As on the app: the choice on the title's line, the period's total on the right above the chart. */}
      <CardTitle
        right={
          <div className="seg" role="group" aria-label={t('stats.volume')}>
            <button type="button" aria-pressed={metric === 'time'} onClick={() => setMetric('time')}>
              {t('stats.hours')}
            </button>
            <button type="button" aria-pressed={metric === 'distance'} onClick={() => setMetric('distance')}>
              {t('stats.distanceUnit')}
            </button>
          </div>
        }
      >
        {t('stats.volume')}
      </CardTitle>
      {volume.data && volume.data.length > 0 && (
        <>
          <p className="cardtotal num">
            {metric === 'time'
              ? formatHoursMinutes(total)
              : `${Math.round(total / (format.unit === 'km' ? 1000 : 1609.344)).toLocaleString(locale)} ${format.unit}`}
          </p>
          <VolumeChart
            buckets={volume.data}
            metric={metric}
            unit={unit}
            theme={theme}
            distanceUnit={format.unit}
          />
        </>
      )}
    </section>
  );
}

function Curves({
  range,
  theme,
  format,
}: {
  range: { from: string; to: string };
  theme: ReturnType<typeof useResolvedTheme>;
  format: ReturnType<typeof formatters>;
}) {
  const [kind, setKind] = useState<'speed' | 'power'>('power'); // power first, as the app (#286)
  const sport = kind === 'speed' ? ['run'] : undefined;
  // Another period keeps the curve on screen while it loads; another kind (power, pace) is another
  // chart, so it waits for its own.
  const sameKind = <T,>(previous: T | undefined, query?: { queryKey: readonly unknown[] }) =>
    (query?.queryKey[3] as { kind?: string } | undefined)?.kind === kind ? previous : undefined;
  const period = useQuery({ ...curvesQuery(api, kind, range, sport), placeholderData: sameKind });
  const all = useQuery({
    ...curvesQuery(api, kind, { from: '2000-01-01' }, sport),
    placeholderData: sameKind,
  });
  const show = (v: number) => (kind === 'power' ? `${Math.round(v)} W` : format.pace(v));
  return (
    <section className="panel">
      <div className="ph">
        <h2>{t('stats.curves')}</h2>
        <div className="seg" role="group" aria-label={t('stats.curves')}>
          <button type="button" aria-pressed={kind === 'power'} onClick={() => setKind('power')}>
            {t('stats.power')}
          </button>
          <button type="button" aria-pressed={kind === 'speed'} onClick={() => setKind('speed')}>
            {t('stats.pace')}
          </button>
        </div>
      </div>
      {period.data && all.data && (
        <CurveChart
          period={period.data}
          allTime={all.data}
          theme={theme}
          format={show}
          colour={kind === 'power' ? 'var(--ride)' : 'var(--run)'}
        />
      )}
    </section>
  );
}

/**
 * The period's bests (running), as the app's (#318): each distance's fastest time in the period, the
 * distance opening its best times and the time its activity; then the way to all of them. The records
 * of all time, their progression and the top ten live on the best times' page.
 */
function PeriodBests({ range, unit }: { range: { from: string; to: string }; unit: 'km' | 'mi' }) {
  const records = useQuery({ ...recordsQuery(api, range), placeholderData: keepPreviousData });
  if (!records.data?.all_time.length) return null;
  const bests = records.data.period.filter((r) => effortShown(r.distance_key, unit));
  const day = (iso: string) =>
    new Date(`${iso}T12:00:00`).toLocaleDateString(locale, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  return (
    <section className="panel periodbests">
      <CardTitle>{t('stats.periodBests')}</CardTitle>
      {bests.length ? (
        <ul className="rows">
          {bests.map((r) => (
            <li key={r.distance_key}>
              <Link to={`/progress/bests?key=${encodeURIComponent(r.distance_key)}`}>
                {effortName(r.distance_key)}
              </Link>
              <span className="num">
                <Link to={`/activities/${r.activity_id}`}>{formatDuration(r.time_s)}</Link>{' '}
                <span className="note">{day(r.day)}</span>
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="note">{t('stats.noPeriodBests')}</p>
      )}
      <Link to="/progress/bests" className="allbests">
        {t('stats.allBests')}
        <MaterialIcon name="chevron_right" />
      </Link>
    </section>
  );
}

function Efficiency({
  range,
  theme,
}: {
  range: { from: string; to: string };
  theme: ReturnType<typeof useResolvedTheme>;
}) {
  const points = useQuery({ ...efficiencyQuery(api, range), placeholderData: keepPreviousData });
  if (!points.data || points.data.length < 3) return null;
  const values = points.data.map((p) => p.efficiency);
  const first = values.slice(0, Math.ceil(values.length / 3));
  const last = values.slice(-Math.ceil(values.length / 3));
  const mean = (v: number[]) => v.reduce((a, b) => a + b, 0) / v.length;
  const change = ((mean(last) - mean(first)) / mean(first)) * 100;
  return (
    <section className="panel">
      <div className="ph">
        <h2>{t('stats.efficiency')}</h2>
        <span className="num">{`${change > 0 ? '+' : ''}${change.toFixed(1)} %`}</span>
      </div>
      <EfficiencyChart points={points.data} theme={theme} />
      <p className="note">{t('stats.efficiencyNote')}</p>
    </section>
  );
}
