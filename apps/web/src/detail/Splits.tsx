/**
 * A run's splits as Strava's (OS-1007, the app's #308 to #310): the training analysis chart (a bar per
 * split, as wide as its distance and taller the faster, in pace blue, the two fastest darker, under the
 * elevation as a see-through grey area, the average dashed across), its card on the activity's page and
 * the splits' table (Km · Pace · bar · Elev. · HR).
 */
import {
  fastestSplits,
  formatters,
  splitAt,
  splitSpeeds,
  ZONE_SCALES,
  type ActivityDetail,
} from '@zankup/core';
import { useRef, type CSSProperties, type KeyboardEvent } from 'react';
import { Link } from 'react-router';

import { locale, t } from '../i18n';
import { usePrefs } from '../prefs';
import { CardTitle } from '../ui';
import { speedText } from './activityContext';
import './activity.css';

type Format = ReturnType<typeof formatters>;
type Split = { distance_m: number; time_s: number | null };

/** The bars' blues, as Strava's: the lighter pace blue, and a darker one for the two fastest splits. */
const SPLIT_BLUE = ZONE_SCALES.pace[2];
const SPLIT_FAST = ZONE_SCALES.pace[3];

/**
 * The training analysis chart. With `axis`, the pace on the left; with `numbers`, each split's number
 * under it. With `onPick`, the pointer across it picks the split under it (`onPick(null)` when it
 * leaves), a click pins it (`onPin`) and the arrow keys step through the splits.
 */
export function SplitsChart({
  splits,
  pace,
  elevation,
  height = 150,
  axis = false,
  numbers = false,
  selected = null,
  onPick,
  onPin,
}: {
  splits: Split[];
  pace: boolean;
  /** The altitude in equal steps over the distance (elevationProfile), or none. */
  elevation: (number | null)[];
  height?: number;
  axis?: boolean;
  numbers?: boolean;
  selected?: number | null;
  onPick?: (index: number | null) => void;
  onPin?: (index: number) => void;
}) {
  const imperial = usePrefs().units === 'imperial';
  const plot = useRef<HTMLDivElement>(null);
  const speeds = splitSpeeds(splits);
  const usable = speeds.filter((v) => v > 0);
  const lo = Math.min(...usable) * 0.9;
  const hi = Math.max(...usable);
  const total = splits.reduce((a, s) => a + s.distance_m, 0);
  const time = splits.reduce((a, s) => a + (s.time_s ?? 0), 0);
  const mean = time ? total / time : 0;
  const fast = fastestSplits(speeds);
  const h = (v: number) => (v > 0 ? 12 + ((v - lo) / (hi - lo || 1)) * (height - 12) : 4);
  const width = (s: Split) => Math.max(0.2, s.distance_m / (total / splits.length || 1));
  // The elevation fills the chart's height from its lowest to its highest point (a 1000-wide box,
  // stretched to the chart's width).
  const alts = elevation.filter((v): v is number => v != null);
  const aLo = alts.length ? Math.min(...alts) : 0;
  const aHi = alts.length ? Math.max(...alts) : 1;
  const ay = (v: number) => height - 6 - ((v - aLo) / (aHi - aLo || 1)) * (height * 0.85);
  const area =
    alts.length > 1
      ? elevation
          .map(
            (v, k) => `${k ? 'L' : 'M'}${((k / (elevation.length - 1)) * 1000).toFixed(1)} ${ay(v ?? aLo)}`,
          )
          .join('') + `L1000 ${height}L0 ${height}Z`
      : null;
  const at = (clientX: number) => {
    const box = plot.current?.getBoundingClientRect();
    if (!box || box.width <= 0) return null;
    const share = Math.min(1, Math.max(0, (clientX - box.left) / box.width));
    return splitAt(
      share,
      splits.map((s) => s.distance_m),
    );
  };
  const paceOnly = (v: number) => speedText(v, pace, imperial).split(' ')[0];
  const label = (i: number) => `${t('detail.splitN', { n: i + 1 })}: ${speedText(speeds[i], pace, imperial)}`;
  const interactive = onPick != null;
  const keys = (event: KeyboardEvent) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!step) return;
    event.preventDefault();
    const next = Math.min(splits.length - 1, Math.max(0, (selected ?? -1) + step));
    onPin?.(next);
  };
  return (
    <div className="splitschart">
      {axis && (
        <div className="saxis num" style={{ height }} aria-hidden="true">
          {[0.25, 0.5, 0.75, 1].map((f) => {
            const v = lo + f * (hi - lo);
            return (
              <span key={f} style={{ bottom: h(v) - 8 }}>
                {paceOnly(v)}
              </span>
            );
          })}
          <span style={{ bottom: 0 }}>{pace ? `/${imperial ? 'mi' : 'km'}` : imperial ? 'mph' : 'km/h'}</span>
        </div>
      )}
      <div className="splotwrap">
        <div
          ref={plot}
          className={`splot${interactive ? ' picking' : ''}`}
          style={{ height }}
          role={interactive ? 'slider' : 'img'}
          aria-label={t('detail.analysis')}
          aria-valuemin={interactive ? 1 : undefined}
          aria-valuemax={interactive ? splits.length : undefined}
          aria-valuenow={interactive && selected != null ? selected + 1 : undefined}
          aria-valuetext={interactive && selected != null ? label(selected) : undefined}
          tabIndex={interactive ? 0 : undefined}
          onKeyDown={interactive ? keys : undefined}
          onMouseMove={interactive ? (e) => onPick(at(e.clientX)) : undefined}
          onTouchMove={
            interactive ? (e) => e.touches[0] && onPin?.(at(e.touches[0].clientX) ?? 0) : undefined
          }
          onMouseLeave={interactive ? () => onPick(null) : undefined}
          onClick={
            interactive
              ? (e) => {
                  const i = at(e.clientX);
                  if (i != null) onPin?.(i);
                }
              : undefined
          }
        >
          {splits.map((s, i) => (
            <span
              key={i}
              className="sbar"
              title={label(i)}
              style={
                {
                  flexGrow: width(s),
                  height: h(speeds[i] ?? 0),
                  background: fast.has(i) ? SPLIT_FAST : SPLIT_BLUE,
                  opacity: selected == null || selected === i ? 1 : 0.5,
                } as CSSProperties
              }
            />
          ))}
          {/* The elevation over the bars, see-through, as Strava's: it greys the bars where it rises. */}
          {area && (
            <svg
              className="selev"
              viewBox={`0 0 1000 ${height}`}
              preserveAspectRatio="none"
              aria-hidden="true"
            >
              <path d={area} />
            </svg>
          )}
          {mean > 0 && <span className="smean" style={{ bottom: h(mean) }} aria-hidden="true" />}
        </div>
        {numbers && (
          <div className="snums num" aria-hidden="true">
            {splits.map((s, i) => (
              <span key={i} style={{ flexGrow: width(s) }} className={selected === i ? 'on' : undefined}>
                {i + 1}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * "Training analysis" on a run's page, as Strava's: the splits' chart over the elevation, the average
 * and the fastest split, and a link to the whole analysis (/activities/:id/analysis).
 */
export function TrainingAnalysis({
  activityId,
  splits,
  pace,
  elevation,
}: {
  activityId: number;
  splits: Split[];
  pace: boolean;
  elevation: (number | null)[];
}) {
  const imperial = usePrefs().units === 'imperial';
  if (splits.length < 2) return null;
  const speeds = splitSpeeds(splits);
  const total = splits.reduce((a, s) => a + s.distance_m, 0);
  const time = splits.reduce((a, s) => a + (s.time_s ?? 0), 0);
  return (
    <section className="panel analysiscard">
      <CardTitle>{t('detail.analysis')}</CardTitle>
      <SplitsChart splits={splits} pace={pace} elevation={elevation} height={180} axis />
      <p className="sfoot note num">
        <span>
          {t('detail.analysisAverage', { pace: speedText(time ? total / time : 0, pace, imperial) })}
        </span>
        <span>{t('detail.analysisFastest', { pace: speedText(Math.max(...speeds), pace, imperial) })}</span>
      </p>
      <Link to={`/activities/${activityId}/analysis`} className="ctxlink">
        {t('detail.analysisOpen')}
      </Link>
    </section>
  );
}

/**
 * The splits as Strava's table: Km (or Mi) · Pace · a thick pace-blue bar that starts a quarter below
 * the slowest split, so the differences show · Elev. · HR. A last, shorter split shows how much of a
 * kilometre (or mile) it was: 0.6.
 */
export function SplitsTable({
  activity,
  format,
  pace,
}: {
  activity: Pick<ActivityDetail, 'splits'>;
  format: Format;
  pace: boolean;
}) {
  const imperial = format.unit === 'mi';
  const splits = activity.splits;
  if (splits.length < 2) return null;
  const speeds = splitSpeeds(splits);
  const usable = speeds.filter((v) => v > 0);
  const lo = Math.min(...usable) * 0.75;
  const hi = Math.max(...usable) || 1;
  const unit = imperial ? 1609.344 : 1000;
  return (
    <section className="panel">
      <CardTitle>{t('activity.splits')}</CardTitle>
      <table className="num splitstable">
        <thead>
          <tr>
            <th scope="col">{t(imperial ? 'detail.splitMi' : 'detail.splitKm')}</th>
            <th scope="col">{t(pace ? 'detail.splitPace' : 'detail.splitSpeed')}</th>
            <td aria-hidden="true" />
            <th scope="col">{t('detail.splitElev')}</th>
            <th scope="col">{t('detail.splitHr')}</th>
          </tr>
        </thead>
        <tbody>
          {splits.map((s, i) => {
            const v = speeds[i] ?? 0;
            const short = s.distance_m < unit * 0.95;
            return (
              <tr key={i}>
                <th scope="row">
                  {short ? (s.distance_m / unit).toLocaleString(locale, { maximumFractionDigits: 1 }) : i + 1}
                </th>
                <td>{v ? format.speedFor(v, pace).split(' ')[0] : '–'}</td>
                <td className="sbarcell" aria-hidden="true">
                  <i
                    style={{
                      width: `${Math.max(4, ((v - lo) / (hi - lo || 1)) * 100)}%`,
                      background: SPLIT_FAST,
                    }}
                  />
                </td>
                <td>
                  {s.elevation_change_m != null
                    ? Math.round(s.elevation_change_m * (imperial ? 3.28084 : 1)).toLocaleString(locale)
                    : ''}
                </td>
                <td>{s.avg_hr ?? ''}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
