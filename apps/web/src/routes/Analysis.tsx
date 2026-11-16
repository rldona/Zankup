/**
 * Workout analysis, from a run's "Training analysis" card (OS-1007, as the app's and Strava's): the
 * splits' chart, large, over the elevation, with the pace on the left and each split's number under it.
 * Hovering picks the split under the pointer (a click keeps it, the arrow keys step), shown in the box
 * on top ("Split 1 · 5:29 /km · Zone 3 · Tempo") and highlighted in the list of splits below.
 */
import {
  activityQuery,
  elevationProfile,
  formatDuration,
  formatters,
  paceZoneOf,
  streamsQuery,
  usesPace,
  type Series,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { api } from '../api';
import { ActivityCrumb } from '../detail/ActivityCrumb';
import { speedText, useActivityContext } from '../detail/activityContext';
import { SplitsChart } from '../detail/Splits';
import { t } from '../i18n';
import { usePrefs } from '../prefs';
import { asSport } from '../progress/more/data';
import { ChartSkeleton, Pulse } from '../Skeleton';
import '../detail/activity.css';

export function Analysis() {
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const prefs = usePrefs();
  const format = useMemo(() => formatters(prefs), [prefs]);
  const imperial = prefs.units === 'imperial';
  const activity = useQuery({ ...activityQuery(api, id), enabled: valid });
  const a = activity.data;
  const streams = useQuery({ ...streamsQuery(api, id), enabled: a?.streams != null });
  const series = streams.data as Series | undefined;
  const elevation = useMemo(
    () => elevationProfile(series?.dist, series?.alt, a?.distance_m ?? 0, 200),
    [series, a?.distance_m],
  );
  const context = useActivityContext(id, valid);
  // The first split is kept from the start, as Strava's; the pointer reads another, a click keeps it.
  const [pinned, setPinned] = useState<number | null>(0);
  const [hovered, setHovered] = useState<number | null>(null);
  const selected = hovered ?? pinned;
  const rows = useRef<(HTMLTableRowElement | null)[]>([]);
  useEffect(() => {
    if (selected != null) rows.current[selected]?.scrollIntoView?.({ block: 'nearest' });
  }, [selected]);

  if (!a) {
    return (
      <>
        <ActivityCrumb id={id} activity={undefined} />
        <section className="page">
          {activity.isError ? (
            <p className="state">{t('activity.notFound')}</p>
          ) : (
            <Pulse>
              <ChartSkeleton height={300} />
            </Pulse>
          )}
        </section>
      </>
    );
  }
  const pace = usesPace(asSport(a.sport));
  const splits = a.splits;
  const picked = selected != null ? splits[selected] : undefined;
  const pickedSpeed = picked?.time_s ? picked.distance_m / picked.time_s : 0;
  // Its pace zone: the highest whose lower bound it reaches (on a run with a threshold pace).
  const zone = pace ? paceZoneOf(pickedSpeed, context?.pace_zones?.bounds ?? []) : null;
  const pin = (i: number) => setPinned((now) => (now === i ? null : i));

  return (
    <>
      <ActivityCrumb id={id} activity={a} />
      <section className="page analysispage">
        <h1>{t('detail.analysis')}</h1>
        {splits.length < 2 ? (
          <p className="state">{t('detail.analysisNone')}</p>
        ) : (
          <>
            {/* What the picked split was: its number, pace and pace zone (as Strava's black box). */}
            <p className="splitbox" aria-live="polite">
              {picked && selected != null ? (
                [
                  <b key="n">{t('detail.splitN', { n: selected + 1 })}</b>,
                  speedText(pickedSpeed, pace, imperial),
                  ...(zone != null
                    ? [t('detail.zoneN', { zone: zone + 1 }), t(`detail.paceZone.${zone + 1}` as never)]
                    : []),
                ].flatMap((part, i) => (i ? [' · ', part] : [part]))
              ) : (
                <span>{t('detail.analysisHintHover')}</span>
              )}
            </p>
            <section className="panel">
              <SplitsChart
                splits={splits}
                pace={pace}
                elevation={elevation}
                height={300}
                axis
                numbers
                selected={selected}
                onPick={setHovered}
                onPin={pin}
              />
            </section>
            <section className="panel">
              <h2 className="splitshead">{t('activity.splits')}</h2>
              <table className="num analysislist">
                <tbody>
                  {splits.map((s, i) => {
                    const on = i === selected;
                    return (
                      <tr
                        key={i}
                        ref={(row) => {
                          rows.current[i] = row;
                        }}
                        className={on ? 'on' : undefined}
                        onMouseEnter={() => setHovered(i)}
                        onMouseLeave={() => setHovered(null)}
                      >
                        <th scope="row">
                          <button
                            type="button"
                            className="link"
                            aria-pressed={pinned === i}
                            onClick={() => pin(i)}
                          >
                            {i + 1}
                          </button>
                        </th>
                        <td>{format.distance(s.distance_m)}</td>
                        <td>{s.time_s ? formatDuration(s.time_s) : '–'}</td>
                        <td>{s.time_s ? format.speedFor(s.distance_m / s.time_s, pace) : '–'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </section>
          </>
        )}
      </section>
    </>
  );
}
