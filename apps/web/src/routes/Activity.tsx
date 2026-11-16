import {
  activityCharts,
  activityFigures,
  ApiError,
  activityQuery,
  availableMetrics,
  elevationProfile,
  formatActivityDate,
  formatDuration,
  formatters,
  localStart,
  metricValues,
  musclesFor,
  musclesWorked,
  quantile,
  RAMP,
  sectionStats,
  splitSpeeds,
  SPORTS,
  streamsQuery,
  trackPoints,
  usesPace,
  type ActivityChart,
  type ActivityContext,
  type ActivityDetail,
  type Metric,
  type MessageKey,
  type Series,
  type Sport,
  type ThemeName,
  themes,
  effortShown,
} from '@zankup/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useParams } from 'react-router';

import { api } from '../api';
import { ChartCard } from '../detail/ChartCard';
import { cursorStore, type CursorStore } from '../detail/cursorStore';
import {
  EffortBlock,
  ExercisesBlock,
  FitnessBlock,
  GoalsBlock,
  MatchesBlock,
  PaceZonesBlock,
  ResultsBlock,
} from '../detail/Context';
import { contextHasBlocks, powerFigures, useActivityContext } from '../detail/activityContext';
import { GoalReachedBanner } from '../detail/GoalReached';
import { MapControls } from '../detail/MapControls';
import { useReplay } from '../detail/replay';
import { HrZones, PowerCurve, PowerZones, RouteAndSegments } from '../detail/Sections';
import { SplitsTable, TrainingAnalysis } from '../detail/Splits';
import { ActivityActions, Meta, Photos } from '../detail/Tools';
import { RouteMap } from '../detail/RouteMap';
import { MuscleCover } from '../detail/BodyMap';
import { Strength } from '../detail/Strength';
import { Summary } from '../detail/Summary';
import { workoutTypeSuffix } from '../detail/workoutTypes';
import { locale, t } from '../i18n';
import { usePrefs } from '../prefs';
import { SportIcon } from '../SportIcon';
import { useResolvedTheme } from '../theme';
import { ActivitySkeleton, ChartSkeleton, Pulse } from '../Skeleton';
import { CardTitle } from '../ui';
import { MaterialIcon } from '../MaterialIcon';
import { PHONE, useMedia } from '../media';
import '../detail/activity.css';

type Format = ReturnType<typeof formatters>;

const asSport = (value: string): Sport =>
  (SPORTS as readonly string[]).includes(value) ? (value as Sport) : 'other';

/**
 * Activity detail (OS-205 to OS-208, OS-216; laid out as Strava's in OS-1006 to OS-1008, as the app's):
 * header and figures, the route on a map coloured by a metric, the goals it reached and the Coach, then
 * per sport what it did for fitness, the same route, its effort, goals and results beside the charts,
 * zones, splits and the rest, and the original file.
 */
export function Activity() {
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const activity = useQuery({ ...activityQuery(api, id), enabled: valid });
  // How it compares loads beside the activity, not after it (Detail reads it from the cache).
  useActivityContext(id, valid);

  if (!valid || (activity.error instanceof ApiError && activity.error.status === 404)) {
    return (
      <section className="page">
        <p className="state">{t('activity.notFound')}</p>
      </section>
    );
  }
  if (activity.data) return <Detail activity={activity.data} />; // kept offline, even if a refetch failed
  if (activity.isError) {
    return (
      <section className="page">
        <p className="state" role="alert">
          {t('common.error')}
        </p>
      </section>
    );
  }
  return (
    <section className="page">
      <ActivitySkeleton />
    </section>
  );
}

/** The charts drawn over the elevation, as Strava's. */
const OVER_ELEVATION = new Set(['power', 'hr', 'speed', 'pace', 'gap', 'cad']);

function Detail({ activity }: { activity: ActivityDetail }) {
  const prefs = usePrefs();
  const format = useMemo(() => formatters(prefs), [prefs]);
  const theme = useResolvedTheme();
  const sport = asSport(activity.sport);
  const pace = usesPace(sport);
  const streams = useQuery({ ...streamsQuery(api, activity.id), enabled: activity.streams != null });
  const series = streams.data as Series | undefined;
  const context = useActivityContext(activity.id);
  // The cursor and the section, shared by the map and every chart. The cursor is a store, not state:
  // moving over a chart moves the map's dot without redrawing the page (OS-1013).
  const [cursor] = useState(cursorStore);
  const [section, setSection] = useState<[number, number] | null>(null);
  const [selecting, setSelecting] = useState(false);
  const hasMap = useMemo(() => (series ? trackPoints(series).length > 1 : false), [series]);
  const charts = useMemo(
    () =>
      series ? activityCharts(series, { sport, pace, units: prefs.units, format, activity, t, locale }) : [],
    [series, sport, pace, prefs.units, format, activity],
  );
  // The altitude behind the training analysis' bars.
  const elevation = useMemo(
    () => elevationProfile(series?.dist, series?.alt, activity.distance_m ?? 0),
    [series, activity.distance_m],
  );

  const phone = useMedia(PHONE);
  const map = series && hasMap && (
    <ActivityMap
      series={series}
      sport={sport}
      pace={pace}
      format={format}
      cursor={cursor}
      section={section}
    />
  );
  // A strength workout without a route has its body map for a cover, as a route has its map.
  // The watch's exercises, else the workout type chosen for it (OS-1015).
  const muscles = useMemo(
    () => musclesFor(musclesWorked(activity.sets ?? []), activity.workout_type),
    [activity.sets, activity.workout_type],
  );
  const cover = map || (muscles.size > 0 && !activity.has_gps && <MuscleCover worked={muscles} />);
  const figures = <Figures {...activityFigures(activity, { pace, format, t, locale })} />;

  // The charts' pieces, laid out per sport below: the heading with the section switch, one card per
  // chart (its figures under it) and the selected section's figures.
  const loading = activity.streams && !series && (
    <Pulse>
      <ChartSkeleton height={280} />
    </Pulse>
  );
  const heading = charts.length > 0 && (
    <ProfileHeading
      selecting={selecting}
      section={section}
      onToggle={() => {
        // Turning "Section" off drops the band, its figures and the map's highlight.
        if (selecting) setSection(null);
        setSelecting(!selecting);
      }}
    />
  );
  const fastestSplit = Math.max(0, ...splitSpeeds(activity.splits));
  const extraFigures = (chart: ActivityChart): [string, string][] => {
    if (sport === 'run' && (chart.key === 'pace' || chart.key === 'speed'))
      return [
        [t('detail.moving'), formatDuration(activity.moving_s ?? activity.duration_s)],
        [t('detail.elapsed'), formatDuration(activity.duration_s)],
        ...(fastestSplit
          ? ([[t('detail.fastestSplit'), format.speedFor(fastestSplit, true)]] as [string, string][])
          : []),
      ];
    // A ride's power chart also gets its work, intensity and training load (the server's).
    return chart.key === 'power' ? powerFigures(context) : [];
  };
  const altitude = charts.find((c) => c.key === 'alt')?.values;
  const chartCard = (key: string) => {
    const chart = charts.find((c) => c.key === key || (key === 'speed' && c.key === 'pace'));
    if (!chart || !series) return null;
    const extra = extraFigures(chart);
    return (
      <ChartCard
        key={chart.key}
        chart={extra.length ? { ...chart, footer: [...chart.footer, ...extra] } : chart}
        series={series}
        units={prefs.units}
        theme={theme}
        section={section}
        selecting={selecting}
        onHover={cursor.set}
        onSection={setSection}
        backdrop={OVER_ELEVATION.has(chart.key) ? altitude : undefined}
      />
    );
  };
  const sectionCard = series && section && (
    <SectionFigures
      series={series}
      section={section}
      format={format}
      pace={pace}
      onClear={() => setSection(null)}
      activityId={activity.id}
    />
  );
  const hrZones = series && (
    <HrZones series={series} activity={activity} hrMax={prefs.hrMax} estimated={prefs.hrMaxEstimated} />
  );
  const photos = <Photos activityId={activity.id} photos={activity.photo_list ?? []} />;
  const laps = <Laps activity={activity} format={format} pace={pace} />;
  const sets = (activity.sets ?? []).length > 0 && <Strength sets={activity.sets} cover={!map && !!cover} />;

  // What it did around its own numbers, in the sport's order: beside the charts on a large screen.
  const ctx = (blocks: (c: ActivityContext) => ReactNode) => context && blocks(context);
  const rideOrRunBlocks = ctx((c) => (
    <>
      <FitnessBlock context={c} />
      <MatchesBlock context={c} activityId={activity.id} pace={pace} />
      <EffortBlock context={c} />
      <GoalsBlock context={c} />
      <ResultsBlock context={c} ride={sport === 'ride'} />
    </>
  ));
  let blocks: ReactNode = null;
  let body: ReactNode;
  if (sport === 'ride') {
    // A ride as Strava's: each chart with what belongs to it (the power curve and zones round power,
    // HR zones after HR). No splits on a ride, as Strava's: its laps say it.
    blocks = rideOrRunBlocks;
    body = (
      <>
        {loading}
        {heading}
        <PowerCurve activity={activity} />
        {chartCard('power')}
        <PowerZones activity={activity} />
        {chartCard('hr')}
        {hrZones}
        {chartCard('speed')}
        {chartCard('cad')}
        {chartCard('alt')}
        {sectionCard}
        {photos}
        <RouteAndSegments activity={activity} />
        {laps}
      </>
    );
  } else if (sport === 'run') {
    // A run as Strava's: the training analysis and splits, pace with its zones, heart rate with its
    // zones, cadence and elevation.
    blocks = rideOrRunBlocks;
    body = (
      <>
        <TrainingAnalysis
          activityId={activity.id}
          splits={activity.splits}
          pace={pace}
          elevation={elevation}
        />
        <SplitsTable activity={activity} format={format} pace={pace} />
        {loading}
        {heading}
        {chartCard('speed')}
        {chartCard('gap')}
        {ctx((c) => (
          <PaceZonesBlock context={c} series={series ?? null} />
        ))}
        {chartCard('hr')}
        {hrZones}
        {chartCard('cad')}
        {chartCard('alt')}
        {sectionCard}
        {photos}
        <RouteAndSegments activity={activity} />
        {laps}
      </>
    );
  } else if (sport === 'strength') {
    // A strength workout as Strava's: its exercises at a glance, effort and goals, heart rate and its
    // zones, then the full sets.
    blocks = (
      <>
        <ExercisesBlock activity={activity} />
        {ctx((c) => (
          <>
            <EffortBlock context={c} />
            <GoalsBlock context={c} />
          </>
        ))}
      </>
    );
    body = (
      <>
        {loading}
        {heading}
        {chartCard('hr')}
        {hrZones}
        {sectionCard}
        {sets}
        {photos}
        {laps}
      </>
    );
  } else {
    body = (
      <>
        {loading}
        {heading}
        {charts.length > 0 && <div className="chartgrid">{charts.map((c) => chartCard(c.key))}</div>}
        {sectionCard}
        {photos}
        {hrZones}
        {sets}
        <RouteAndSegments activity={activity} />
        <PowerZones activity={activity} />
        <PowerCurve activity={activity} />
        {laps}
        <SplitsTable activity={activity} format={format} pace={pace} />
      </>
    );
  }
  const aside =
    (sport === 'strength' && (activity.sets ?? []).length > 0) || contextHasBlocks(context, sport);

  return (
    <article className={`page detail${cover ? ' withmap' : ''}`}>
      {/* On a phone, as the app's pushed screen: back, edit, share and photos over the map. */}
      {phone && <ActivityActions activity={activity} floating />}
      {/* As on the app: the map across the top, then the title, the figures and the Coach's summary. */}
      {cover}
      <header className="dhead">
        <SportIcon sport={sport} />
        <div>
          <h1>{activity.name ?? t(`sport.${sport}`)}</h1>
          <p className="csub">
            {formatActivityDate(localStart(activity.start_utc, activity.timezone), locale)}
            {workoutTypeSuffix(activity)}
            {activity.device ? ` · ${activity.device}` : ''}
          </p>
          <Meta activity={activity} />
        </div>
        {!phone && <ActivityActions activity={activity} />}
      </header>

      {/* As on the app: the figures, the goals it reached, the Coach's summary, then the rest. */}
      {figures}
      <GoalReachedBanner goals={context?.goals_reached} />
      <Summary activityId={activity.id} name={activity.name} />
      <div className={`dlayout${aside ? ' withside' : ''}`}>
        {aside && (
          <aside className="dside" aria-label={t('activity.context')}>
            {blocks}
          </aside>
        )}
        <div className="dmain">{body}</div>
      </div>
      {activity.description && (
        <section className="panel">
          <p className="desc">{activity.description}</p>
        </section>
      )}
      <BestEfforts activity={activity} format={format} />
      <p className="note origin">
        {t('activity.origin')}: {sourceLabel(activity.source)}
        {activity.has_fit && (
          <>
            {' · '}
            <a href={`/api/activities/${activity.id}/fit`} download>
              {t('activity.download')}
              {activity.original_format && activity.original_format !== 'fit'
                ? ` (${activity.original_format.toUpperCase()})`
                : ''}
            </a>
          </>
        )}
      </p>
    </article>
  );
}

/** The charts' heading (OS-907), with the switch that turns picking a section on and off. */
function ProfileHeading({
  selecting,
  section,
  onToggle,
}: {
  selecting: boolean;
  section: [number, number] | null;
  onToggle: () => void;
}) {
  return (
    <>
      <div className="titlebar profilehead">
        <h2>{t('activity.profile')}</h2>
        <button type="button" className="chip" aria-pressed={selecting} onClick={onToggle}>
          {t('mobile.selectSection')}
        </button>
      </div>
      {selecting && !section && <p className="note">{t('activity.sectionHint')}</p>}
    </>
  );
}

function sourceLabel(source: string): string {
  const key = `activity.source.${source}`;
  return key === 'activity.source.garmin' ||
    key === 'activity.source.strava' ||
    key === 'activity.source.manual'
    ? t(key)
    : source;
}

/** The figures under the title (OS-908), as the app's: the six that matter in columns, the rest behind "More". */
function Figures({ main, more }: { main: [string, string][]; more: [string, string][] }) {
  const [open, setOpen] = useState(false);
  const list = (items: [string, string][]) => (
    <dl className="figs num">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
  return (
    <section className="figures" aria-label={t('activity.figures')}>
      {list(main)}
      {more.length > 0 && open && list(more)}
      {more.length > 0 && (
        <button type="button" className="link figmore" aria-expanded={open} onClick={() => setOpen(!open)}>
          {t(open ? 'activity.fig.less' : 'activity.fig.more')}
          <MaterialIcon name={open ? 'keyboard_arrow_up' : 'keyboard_arrow_down'} />
        </button>
      )}
    </section>
  );
}

/** The map with its controls on it (OS-908): replay, what the route is coloured by and its legend. */
function ActivityMap({
  series,
  sport,
  pace,
  format,
  cursor,
  section,
}: {
  series: Series;
  sport: Sport;
  pace: boolean;
  format: Format;
  cursor: CursorStore;
  section: [number, number] | null;
}) {
  const theme = useResolvedTheme();
  const metrics = useMemo(() => availableMetrics(series), [series]);
  const [chosen, setMetric] = useState<Metric | null>(null);
  const metric = chosen && metrics.includes(chosen) ? chosen : (metrics[0] ?? null);
  const replay = useReplay(series, cursor);

  const formatMetric = useCallback(
    (m: Metric, v: number) => {
      switch (m) {
        case 'speed':
          return format.speedFor(v, pace);
        case 'hr':
          return `${Math.round(v)} bpm`;
        case 'power':
          return `${Math.round(v)} W`;
        case 'alt':
          return format.elevation(v);
      }
    },
    [format, pace],
  );

  const legend = useMemo(() => {
    const values = metric ? metricValues(series, metric) : null;
    const lo = values ? quantile(values, 0.02) : null;
    const hi = values ? quantile(values, 0.98) : null;
    if (!metric || lo == null || hi == null || hi <= lo) return null;
    // A pace reads best first: the fast end of the ramp is the low pace.
    const inverted = pace && metric === 'speed';
    return {
      low: formatMetric(metric, inverted ? hi : lo),
      high: formatMetric(metric, inverted ? lo : hi),
      colours: RAMP,
    };
  }, [series, metric, formatMetric, pace]);

  const label = (m: Metric) => t(m === 'speed' ? (pace ? 'metric.pace' : 'metric.speed') : `metric.${m}`);

  return (
    <section className="mapblock" aria-label={t('activity.route')}>
      <RouteMap
        series={series}
        metric={metric}
        theme={theme}
        cursor={cursor}
        section={section}
        colour={sportColour(theme, sport)}
      />
      <MapControls
        replay={replay}
        metric={metric}
        metrics={metrics.map((m) => ({ value: m, label: label(m) }))}
        onMetric={setMetric}
        legend={legend}
      />
    </section>
  );
}

function sportColour(theme: ThemeName, sport: Sport): string {
  return themes[theme][sport];
}

/** The selected section's figures, with "save as segment" when it has a track (OS-216, OS-308). */
function SectionFigures({
  series,
  section,
  format,
  pace,
  onClear,
  activityId,
}: {
  series: Series;
  section: [number, number];
  format: Format;
  pace: boolean;
  onClear: () => void;
  activityId: number;
}) {
  const s = sectionStats(series, section[0], section[1]);
  const figures: [string, string][] = [
    ...(s.distance_m != null
      ? ([[t('feed.stat.distance'), format.distance(s.distance_m)]] as [string, string][])
      : []),
    [t('feed.stat.time'), formatDuration(s.time_s)],
    ...(s.speed != null
      ? ([[t(pace ? 'feed.stat.pace' : 'feed.stat.speed'), format.speedFor(s.speed, pace)]] as [
          string,
          string,
        ][])
      : []),
    ...(s.ascent_m != null
      ? ([[t('feed.stat.ascent'), format.elevation(s.ascent_m)]] as [string, string][])
      : []),
    ...(s.avg_hr != null ? ([[t('feed.stat.hr'), `${s.avg_hr} bpm`]] as [string, string][]) : []),
    ...(s.avg_power != null ? ([[t('activity.power'), `${s.avg_power} W`]] as [string, string][]) : []),
  ];
  return (
    <section className="panel sectionfigs" aria-live="polite">
      <CardTitle
        right={
          <button type="button" className="link" onClick={onClear}>
            {t('activity.clearSection')}
          </button>
        }
      >
        {t('activity.section')}
      </CardTitle>
      <dl className="chartfoot num">
        {figures.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {series.lat?.some((v) => v != null) && <SaveSegment activityId={activityId} section={section} />}
    </section>
  );
}

const EFFORT_KEYS = new Set([
  '400m',
  'half_mile',
  '1k',
  '1mile',
  '2mile',
  '5k',
  '10k',
  '15k',
  '10mile',
  'half_marathon',
  'marathon',
]);

function BestEfforts({ activity, format }: { activity: ActivityDetail; format: Format }) {
  if (!activity.best_efforts.length) return null;
  return (
    <section className="panel">
      <CardTitle>{t('activity.best')}</CardTitle>
      <table className="num">
        <thead>
          <tr>
            <th scope="col">{t('feed.stat.distance')}</th>
            <th scope="col">{t('feed.stat.time')}</th>
            <th scope="col">{t('feed.stat.pace')}</th>
          </tr>
        </thead>
        <tbody>
          {activity.best_efforts
            .filter((e) => effortShown(e.distance_key, format.unit))
            .map((e) => (
              <tr key={e.distance_key}>
                <td>
                  {EFFORT_KEYS.has(e.distance_key)
                    ? t(`effort.${e.distance_key}` as MessageKey)
                    : format.distance(e.distance_m)}
                </td>
                <td>{formatDuration(e.time_s)}</td>
                <td>{format.pace(e.distance_m / e.time_s)}</td>
              </tr>
            ))}
        </tbody>
      </table>
    </section>
  );
}

function Laps({ activity, format, pace }: { activity: ActivityDetail; format: Format; pace: boolean }) {
  if (activity.laps.length < 2) return null;
  const power = activity.laps.some((l) => l.avg_power);
  return (
    <section className="panel">
      <CardTitle>{t('activity.laps')}</CardTitle>
      <div className="scroll" tabIndex={0} role="region" aria-label={t('activity.laps')}>
        <table className="num">
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">{t('feed.stat.distance')}</th>
              <th scope="col">{t('feed.stat.time')}</th>
              <th scope="col">{t(pace ? 'feed.stat.pace' : 'feed.stat.speed')}</th>
              <th scope="col">{t('activity.elev')}</th>
              <th scope="col">{t('feed.stat.hr')}</th>
              {power && <th scope="col">{t('activity.power')}</th>}
            </tr>
          </thead>
          <tbody>
            {activity.laps.map((l) => (
              <tr key={l.idx}>
                <td>{l.idx + 1}</td>
                <td>{l.distance_m ? format.distance(l.distance_m) : '—'}</td>
                <td>{formatDuration(l.duration_s)}</td>
                <td>
                  {l.distance_m && l.duration_s ? format.speedFor(l.distance_m / l.duration_s, pace) : '—'}
                </td>
                <td>
                  {l.ascent_m != null
                    ? `+${format.elevation(l.ascent_m)} / −${format.elevation(l.descent_m ?? 0)}`
                    : '—'}
                </td>
                <td>{l.avg_hr ?? '—'}</td>
                {power && <td>{l.avg_power ? `${l.avg_power} W` : '—'}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/** Save the selected section as a personal segment (OS-325). */
function SaveSegment({ activityId, section }: { activityId: number; section: [number, number] }) {
  const [name, setName] = useState('');
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const save = useMutation({
    mutationFn: async () => {
      const { data, error } = await api.POST('/api/segments', {
        body: { activity_id: activityId, start_index: section[0], end_index: section[1], name: name.trim() },
      });
      if (!data)
        throw new Error(String((error as { detail?: unknown } | undefined)?.detail ?? t('common.error')));
      return data;
    },
    onSuccess: () => {
      setOpen(false);
      setName('');
      void queryClient.invalidateQueries({ queryKey: ['activities', 'segments'] });
    },
  });
  if (!open)
    return (
      <>
        <button type="button" className="link" onClick={() => setOpen(true)}>
          {t('segments.save')}
        </button>
        {save.isSuccess && <span className="note">{t('segments.saved')}</span>}
      </>
    );
  return (
    <form
      className="savesegment"
      onSubmit={(e) => {
        e.preventDefault();
        if (name.trim()) save.mutate();
      }}
    >
      <input
        aria-label={t('segments.name')}
        placeholder={t('segments.name')}
        value={name}
        onChange={(e) => setName(e.target.value)}
        maxLength={120}
        autoFocus
      />
      <button type="submit" className="primary" disabled={save.isPending}>
        {t('settings.save')}
      </button>
      {save.isError && <span className="note">{save.error.message}</span>}
    </form>
  );
}
