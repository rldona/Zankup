import { formatDuration } from '@zankup/core';
import { useState } from 'react';

import { t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { useCursor } from './cursorStore';
import { SPEEDS, type Replay } from './replay';

/**
 * What drives the activity map, on the map itself (OS-908), as the app's: play and pause with the seek
 * bar at the bottom, and an options button at the top right that opens what the route is coloured by
 * (with its legend) and the replay speed. Nothing else sits around the map.
 */
export function MapControls<M extends string>({
  replay,
  metric,
  metrics,
  onMetric,
  legend,
}: {
  replay: Replay;
  metric: M | null;
  metrics: { value: M; label: string }[];
  onMetric: (m: M) => void;
  legend: { low: string; high: string; colours: readonly string[] } | null;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        className="mapbtn mapopts"
        aria-label={t('activity.mapOptions')}
        aria-expanded={open}
        title={t('activity.mapOptions')}
        onClick={() => setOpen(!open)}
      >
        <MaterialIcon name="tune" />
      </button>
      {open && (
        <div className="mappanel" role="dialog" aria-label={t('activity.mapOptions')}>
          <div className="ph">
            <h2>{t('activity.mapOptions')}</h2>
            <button
              type="button"
              className="mapbtn flat"
              aria-label={t('common.close')}
              onClick={() => setOpen(false)}
            >
              <MaterialIcon name="close" />
            </button>
          </div>
          {metrics.length > 1 && metric && (
            <div className="field">
              <span className="note">{t('activity.colourBy')}</span>
              <div className="chips" role="group" aria-label={t('activity.colourBy')}>
                {metrics.map((m) => (
                  <button
                    key={m.value}
                    type="button"
                    className="chip"
                    aria-pressed={m.value === metric}
                    onClick={() => onMetric(m.value)}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
              {legend && (
                <div className="legend num" aria-hidden="true">
                  <span>{legend.low}</span>
                  <i style={{ background: `linear-gradient(90deg, ${legend.colours.join(', ')})` }} />
                  <span>{legend.high}</span>
                </div>
              )}
            </div>
          )}
          <div className="field">
            <span className="note">{t('replay.speed')}</span>
            <div className="chips" role="group" aria-label={t('replay.speed')}>
              {SPEEDS.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="chip"
                  aria-pressed={s === replay.speed}
                  onClick={() => replay.setSpeed(s)}
                >
                  {s}×
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
      {replay.usable && (
        <div className="mapbar" role="group" aria-label={t('replay.title')}>
          <button
            type="button"
            className="mapbtn"
            aria-label={replay.playing ? t('replay.pause') : t('replay.play')}
            aria-pressed={replay.playing}
            onClick={replay.toggle}
          >
            <MaterialIcon name={replay.playing ? 'pause' : 'play_arrow'} />
          </button>
          <Seek replay={replay} />
        </div>
      )}
    </>
  );
}

/** The seek bar and the time: where the cursor is, the only part of the controls that follows it. */
function Seek({ replay }: { replay: Replay }) {
  const current = replay.timeAt(useCursor(replay.cursor));
  return (
    <>
      <input
        type="range"
        min={0}
        max={Math.round(replay.total)}
        step={1}
        value={Math.round(current)}
        aria-label={t('replay.seek')}
        aria-valuetext={formatDuration(current)}
        onChange={(e) => replay.seek(Number(e.target.value))}
      />
      <span className="num">{formatDuration(current)}</span>
    </>
  );
}
