/**
 * Race predictions on Progress (as Strava's, as the app's): the card with the 5 km prediction and its
 * last month; the page with the four races today (their pace and how they changed in 30 days) or one
 * race over one, three or six months, its line being each week's prediction. The range is chosen at the
 * bottom, always in view.
 */
import {
  addDays,
  changeText,
  predictionLabelDays,
  PREDICTION_DAYS,
  PREDICTION_RANGES,
  racePace,
  RACES,
  raceTime,
  series,
  timeTicks,
  valueOn,
  type Point,
  type PredictionRange,
  type RaceKind,
} from '@zankup/core';
import { useMemo, useState } from 'react';

import { EChart } from '../../charts/EChart';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { usePrefs } from '../../prefs';
import { CardSkeleton, ChartSkeleton, Pulse } from '../../Skeleton';
import { useResolvedTheme } from '../../theme';
import { InfoTip } from '../../ui';
import { at, palette, shortDate, usePredictions, useToday } from './data';
import { CardLine, ChangePill, ProgressLink, RaceBadge } from './parts';

/** How much a prediction changed: ▼ faster (in green), ▲ slower. */
function TimeChange({ seconds }: { seconds: number }) {
  return (
    <ChangePill up={seconds >= 0} good={seconds < 0}>
      {changeText(seconds)}
    </ChangePill>
  );
}

export function PredictionsCard() {
  const today = useToday();
  const { byKind, isPending } = usePredictions();
  const history = byKind.get('race_5k');
  const line = useMemo(() => series(history ?? [], addDays(today, -30), today), [history, today]);
  const values = useMemo(() => line.map((p) => p.value), [line]);
  if (isPending)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const now = valueOn(history ?? [], today);
  const before = line[0]?.value;
  return (
    <ProgressLink
      to="/progress/predictions"
      icon="timer"
      title={t('progress.predictions')}
      when={t('progress.lastMonth')}
      label={`${t('progress.predictions')}: ${t('progress.race.race_5k')} ${now != null ? raceTime(now) : '–'}`}
    >
      {now == null || before == null ? (
        <p className="note">{t('progress.noPredictions')}</p>
      ) : (
        <div className="fxbody">
          <div className="fxfigure">
            <span>{t('progress.race.race_5k')}</span>
            <span className="fxrow">
              <b className="num fxbig">{raceTime(now)}</b>
              <TimeChange seconds={now - before} />
            </span>
          </div>
          <CardLine
            values={values}
            first={raceTime(values[0] ?? now)}
            inverse
            label={`${t('progress.race.race_5k')} ${raceTime(before)} → ${raceTime(now)}`}
          />
        </div>
      )}
    </ProgressLink>
  );
}

export function PredictionsPage() {
  const today = useToday();
  const imperial = usePrefs().units === 'imperial';
  const { byKind, isPending } = usePredictions();
  const [range, setRange] = useState<PredictionRange>('today');
  const [race, setRace] = useState<RaceKind>('race_5k');
  const unit = imperial ? '/mi' : '/km';
  const metres = (k: RaceKind) => RACES.find((r) => r.kind === k)?.metres ?? 5000;
  const any = RACES.some((r) => (byKind.get(r.kind) ?? []).length);

  const body = () => {
    if (isPending)
      return (
        <Pulse>
          <ChartSkeleton height={260} />
        </Pulse>
      );
    if (!any) return <p className="note">{t('progress.noPredictions')}</p>;
    if (range === 'today')
      return (
        <>
          <p className="fxintro">{t('progress.predictionsIntro')}</p>
          <ul className="fxraces">
            {RACES.map((r) => {
              const history = byKind.get(r.kind) ?? [];
              const now = valueOn(history, today);
              const before = valueOn(history, addDays(today, -30));
              return (
                <li key={r.kind}>
                  <button
                    type="button"
                    aria-label={`${t(`progress.race.${r.kind}`)} ${now != null ? raceTime(now) : '–'}`}
                    onClick={() => {
                      setRace(r.kind);
                      setRange('1m');
                    }}
                  >
                    <RaceBadge race={r.kind} size={60} />
                    {now != null ? (
                      <>
                        <b className="num fxtime">{raceTime(now)}</b>
                        <span className="muted num">
                          {racePace(now, r.metres, imperial)} {unit}
                        </span>
                        {before != null && <TimeChange seconds={now - before} />}
                      </>
                    ) : (
                      <span className="muted">–</span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      );
    const from = addDays(today, -PREDICTION_DAYS[range]);
    const history = byKind.get(race) ?? [];
    const points = series(history, from, today);
    const now = valueOn(history, today);
    return (
      <>
        <div className="fxchips" role="radiogroup" aria-label={t('progress.predictions')}>
          {RACES.map((r) => (
            <button
              key={r.kind}
              type="button"
              role="radio"
              aria-checked={r.kind === race}
              onClick={() => setRace(r.kind)}
            >
              {t(`progress.race.${r.kind}`)}
            </button>
          ))}
        </div>
        <h2 className="fxsub">{t('progress.currentPredictions')}</h2>
        {now == null || !points[0] ? (
          <p className="note">{t('progress.noPredictions')}</p>
        ) : (
          <>
            <div className="fxnow">
              <RaceBadge race={race} size={54} />
              <div>
                <p className="fxrow">
                  <b className="num fxtime">{raceTime(now)}</b>
                  <span className="muted num">
                    {racePace(now, metres(race), imperial)} {unit}
                  </span>
                </p>
                <small className="muted">
                  {shortDate(from, true)} - {t('progress.today')}
                </small>
              </div>
              <div className="fxnowchange">
                <TimeChange seconds={now - points[0].value} />
                <small className="muted">{t(`progress.predLast.${range}`)}</small>
              </div>
            </div>
            <PredictionChart points={points} from={from} today={today} range={range} race={race} />
          </>
        )}
      </>
    );
  };

  return (
    <section className="page fxpage fxpredictions">
      <div className="titlebar">
        <h1>{t('progress.predictions')}</h1>
        <InfoTip text={t('progress.predictionsInfo')} />
      </div>
      <div className="fxpredbody">{body()}</div>
      {/* The range at the bottom, as Strava's, kept in view whatever is above it. */}
      <div className="fxranges" role="radiogroup" aria-label={t('progress.predictions')}>
        {PREDICTION_RANGES.map((r) => (
          <button key={r} type="button" role="radio" aria-checked={r === range} onClick={() => setRange(r)}>
            {r === range && <MaterialIcon name="check" />}
            {t(`progress.predRange.${r}`)}
          </button>
        ))}
      </div>
    </section>
  );
}

/**
 * A prediction over a range: faster is higher, a ring where it changed, today filled, times on the
 * right and dates under it; the pointer on a ring reads its day and time. Another race or range slides
 * into place (the series keep their ids).
 */
function PredictionChart({
  points,
  from,
  today,
  range,
  race,
}: {
  points: Point[];
  from: string;
  today: string;
  range: Exclude<PredictionRange, 'today'>;
  race: RaceKind;
}) {
  const theme = useResolvedTheme();
  const option = useMemo(() => {
    const c = palette(theme);
    const days: string[] = [];
    for (let d = from; d <= today; d = addDays(d, 1)) days.push(d);
    const byDay = new Map(points.map((p) => [p.day, p.value]));
    const values = points.map((p) => p.value);
    const ticks = timeTicks(Math.min(...values), Math.max(...values));
    const step = (ticks[1] ?? 0) - (ticks[0] ?? 0);
    const labels = new Map(
      predictionLabelDays(range, from, today).map((d) => [
        d,
        at(d)
          .toLocaleDateString(
            locale,
            range === '1m' ? { day: 'numeric', month: 'short' } : { month: 'short' },
          )
          .replace('.', '')
          .toUpperCase(),
      ]),
    );
    const last = days.length - 1;
    const now = points.at(-1)?.value ?? 0;
    return {
      animationDurationUpdate: 600,
      grid: { left: 8, right: 72, top: 16, bottom: 30 },
      tooltip: {
        trigger: 'item',
        formatter: (p: { data: [number, number] | number | null; dataIndex: number; seriesId: string }) => {
          const value = Array.isArray(p.data) ? p.data[1] : p.data;
          const day = p.seriesId === 'pred' ? days[p.dataIndex] : today;
          if (value == null || !day) return '';
          return `${shortDate(day, true)}<br/><b>${raceTime(value)}</b>`;
        },
      },
      xAxis: {
        type: 'category',
        data: days,
        boundaryGap: false,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          interval: (i: number) => labels.has(days[i] ?? ''),
          formatter: (d: string) => labels.get(d) ?? '',
          color: c.fg,
          fontSize: 12,
        },
      },
      yAxis: {
        type: 'value',
        position: 'right',
        inverse: true,
        min: ticks[0],
        max: ticks.at(-1),
        interval: step > 0 ? step : undefined,
        axisLabel: { color: c.fg, fontSize: 13, formatter: (v: number) => raceTime(v) },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          id: 'pred',
          name: t(`progress.race.${race}`),
          type: 'line',
          data: days.map((d) => byDay.get(d) ?? null),
          connectNulls: true,
          smooth: 0.5,
          smoothMonotone: 'x',
          symbol: 'circle',
          symbolSize: 10,
          lineStyle: { color: c.accent, width: 3 },
          itemStyle: { color: c.bg, borderColor: c.accent, borderWidth: 2 },
        },
        {
          id: 'halo',
          type: 'scatter',
          silent: true,
          data: [[last, now]],
          symbolSize: 28,
          itemStyle: { color: c.accent, opacity: 0.25 },
        },
        {
          id: 'today',
          type: 'scatter',
          data: [[last, now]],
          symbolSize: 14,
          itemStyle: { color: c.accent },
        },
      ],
    };
  }, [theme, points, from, today, range, race]);
  const now = points.at(-1);
  return (
    <EChart
      option={option}
      theme={theme}
      height={320}
      className="fxpredchart"
      summary={`${t(`progress.race.${race}`)}: ${points[0] ? raceTime(points[0].value) : ''} → ${now ? raceTime(now.value) : ''}`}
    />
  );
}
