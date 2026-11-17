/**
 * Training zones on Progress (OS-1004), as the app's: the card says where most of the last month's
 * time went (heart rate, every sport) beside a bar per zone; the page has the zones in full for 7 days
 * to a year, every sport or one, heart rate in reds, pace in blues and power in purples, the zone with
 * most time, how its share moved and a sentence on what moved most. The pencil opens the zones'
 * settings.
 */
import {
  changeText,
  defaultZoneKind,
  glance,
  paceRange,
  PROGRESS_RANGES,
  shareChange,
  SPORTS,
  topZone,
  volumeRange,
  zoneKinds,
  zoneRange,
  zoneScale,
  zonesQuery,
  type ProgressRange,
  type ZoneKind,
} from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { Link } from 'react-router';

import { api } from '../../api';
import { t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { usePrefs } from '../../prefs';
import { Bone, CardSkeleton, Pulse } from '../../Skeleton';
import { SportGlyph } from '../../SportIcon';
import { CardTitle, InfoTip } from '../../ui';
import { asSport, longDate, useToday } from './data';

const KIND_LABEL = {
  hr: 'progress.heartRate',
  power: 'progress.power',
  pace: 'progress.pace',
} as const;

/** A change in points of share: ▲ 4 % or ▼ 3 %. */
function ShareBadge({ points }: { points: number }) {
  return (
    <span className="pz-badge num">
      <i aria-hidden="true">{points >= 0 ? '▲' : '▼'}</i>
      {Math.abs(points)} %
    </span>
  );
}

export function ZonesCard() {
  const today = useToday();
  const { current } = volumeRange('1m', today);
  const zones = useQuery(zonesQuery(api, 'hr', current));
  if (zones.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const seconds = zones.data?.seconds ?? [];
  const top = topZone(seconds);
  const change = top ? shareChange(seconds, zones.data?.previous ?? [], top.index) : null;
  const most = Math.max(1, ...seconds);
  const colours = zoneScale('hr', seconds.length);
  return (
    <Link to="/progress/zones" className="panel pz-card tap">
      <CardTitle
        right={
          <span className="pz-more">
            {t('progress.lastMonth')}
            <MaterialIcon name="chevron_right" />
          </span>
        }
      >
        <MaterialIcon name="monitor_heart" />
        {t('progress.zones')}
      </CardTitle>
      {!top ? (
        <p className="note">{t('progress.noZones')}</p>
      ) : (
        <div className="pz-cardbody">
          <div>
            <span>{t('progress.mostTime')}</span>
            <div className="pz-figure">
              <b className="num">{t('progress.zonesTop', { share: top.share, zone: top.index + 1 })}</b>
              {change != null && change !== 0 && <ShareBadge points={change} />}
            </div>
          </div>
          {/* A bar per zone, the highest on top: the one with the most time in its colour. */}
          <ol className="pz-mini" aria-hidden="true">
            {seconds
              .map((s, i) => ({ s, i }))
              .reverse()
              .map(({ s, i }) => (
                <li key={i}>
                  <small>{i === top.index ? `Z${i + 1}` : ''}</small>
                  <i
                    style={{
                      width: `${Math.max(12, (s / most) * 100) * 0.8}%`,
                      background: i === top.index ? colours[i] : undefined,
                    }}
                  />
                </li>
              ))}
          </ol>
        </div>
      )}
    </Link>
  );
}

/** The zones in full, the highest on top: each one's time as a bar (it slides to a new time), the range. */
function ZoneRows({ kind, seconds, bounds }: { kind: ZoneKind; seconds: number[]; bounds: number[] }) {
  const imperial = usePrefs().units === 'imperial';
  const colours = zoneScale(kind, seconds.length);
  const most = Math.max(1, ...seconds);
  const unit = kind === 'hr' ? t('progress.bpm') : 'W';
  // Pace zones come as speeds (m/s): written as paces, the fast ones on top.
  const range = (i: number) =>
    !bounds.length
      ? ''
      : kind === 'pace'
        ? `${paceRange(bounds, i, imperial ? 1609.344 : 1000).replace(/ /g, '')} /${imperial ? 'mi' : 'km'}`
        : `${zoneRange(bounds, i)} ${unit}`;
  return (
    <ol className="pz-rows">
      {seconds
        .map((s, i) => ({ s, i }))
        .reverse()
        .map(({ s, i }) => (
          <li key={i} title={`Z${i + 1} · ${changeText(s)}${range(i) ? ` · ${range(i)}` : ''}`}>
            <b>Z{i + 1}</b>
            <span className="pz-track">
              <i
                style={{ '--w': `${Math.max(2, (s / most) * 70)}%`, background: colours[i] } as CSSProperties}
              />
              <b className="num">{changeText(s)}</b>
            </span>
            <span className="num pz-range">{range(i)}</span>
          </li>
        ))}
    </ol>
  );
}

export function ZonesPage() {
  const today = useToday();
  const [sport, setSport] = useState<string | null>(null);
  const [chosen, setKind] = useState<ZoneKind>('hr');
  // Only the zones the sport has (a run: HR and pace; a ride: HR and power; the rest: HR).
  const kinds = zoneKinds(sport);
  const kind = kinds.includes(chosen) ? chosen : 'hr';
  const [range, setRange] = useState<ProgressRange>('1m');
  const { current } = volumeRange(range, today);
  // While a new range or sport loads, the zones shown stay (of the same kind) and the bars slide to
  // the new times, with no skeleton in between.
  const zones = useQuery({
    ...zonesQuery(api, kind, current, sport ? [sport] : undefined),
    placeholderData: (before) => (before?.kind === kind ? before : undefined),
  });
  const loaded = zones.data != null && !zones.isPlaceholderData;
  const seconds = zones.data?.seconds ?? Array<number>(kind === 'power' ? 7 : 5).fill(0);
  const previous = zones.data?.previous ?? [];
  const top = topZone(seconds);
  const change = top ? shareChange(seconds, previous, top.index) : null;
  const note = glance(seconds, previous);
  const pick = (s: string | null) => {
    setSport(s);
    // Each sport opens on its own zones: a run on pace, a ride on power.
    setKind(defaultZoneKind(s));
  };

  return (
    <section className="page pz-page">
      <div className="titlebar">
        <h1>{t('progress.zones')}</h1>
        <Link
          to="/settings/heart"
          className="iconbtn"
          aria-label={t('progress.editZones')}
          title={t('progress.editZones')}
        >
          <MaterialIcon name="edit" />
        </Link>
        <InfoTip text={t('progress.zonesInfo')} />
      </div>
      <div className="panel pz-main">
        <div className="chips wrap" role="radiogroup" aria-label={t('progress.filterSport')}>
          {[null, ...SPORTS].map((s) => (
            <button
              key={s ?? 'all'}
              type="button"
              role="radio"
              className="chip"
              aria-checked={s === sport}
              onClick={() => pick(s)}
            >
              {s && <SportGlyph sport={asSport(s)} />}
              {s ? t(`sport.${asSport(s)}`) : t('progress.allSports')}
            </button>
          ))}
        </div>
        {kinds.length > 1 && (
          <div className="seg" role="group" aria-label={t('progress.zones')}>
            {kinds.map((k) => (
              <button key={k} type="button" aria-pressed={kind === k} onClick={() => setKind(k)}>
                {t(KIND_LABEL[k])}
              </button>
            ))}
          </div>
        )}
        <div className="pz-head">
          <div>
            <h2>
              {top ? (
                t('progress.zonesTopLong', { share: top.share, zone: top.index + 1 })
              ) : zones.data || zones.isError ? (
                t('progress.noZones')
              ) : (
                <Bone width={220} height={24} />
              )}
            </h2>
            <p className="note">
              {longDate(current.from)} – {t('progress.today')}
            </p>
          </div>
          {change != null && change !== 0 && (
            <div className="pz-change">
              <ShareBadge points={change} />
              <small className="note">{t('progress.vsBefore')}</small>
            </div>
          )}
        </div>
        <ZoneRows kind={kind} seconds={seconds} bounds={zones.data?.bounds ?? []} />
        {loaded && kind === 'power' && !zones.data?.bounds.length && (
          <p className="note">{t('progress.noFtp')}</p>
        )}
        {loaded && kind === 'pace' && !zones.data?.bounds.length && (
          <p className="note">{t('progress.noThreshold')}</p>
        )}
        <div className="seg pz-ranges" role="group" aria-label={t('progress.filters')}>
          {PROGRESS_RANGES.map((r) => (
            <button key={r} type="button" aria-pressed={r === range} onClick={() => setRange(r)}>
              {t(`progress.range.${r}`)}
            </button>
          ))}
        </div>
      </div>
      {note && (
        <div className="panel pz-glance">
          <CardTitle>
            {sport ? <SportGlyph sport={asSport(sport)} /> : <MaterialIcon name="monitor_heart" />}
            {t('progress.zonesGlance')}
          </CardTitle>
          <p>
            {t(note.up ? 'progress.zoneUp' : 'progress.zoneDown', {
              zone: note.zone + 1,
              percent: note.percent,
              since: t(`progress.zoneSince.${range}`),
            })}{' '}
            {t(`progress.zoneAdvice.${note.band}.${note.up ? 'up' : 'down'}`)}
          </p>
        </div>
      )}
    </section>
  );
}
