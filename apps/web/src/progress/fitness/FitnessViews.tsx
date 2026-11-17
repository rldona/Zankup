/**
 * Fitness on Progress (as Strava's, as the app's): the card with today's score, its change over the
 * last month and the month's line; the page with the score over a month to two years (the range
 * remembered in this browser), how much it changed from the range's first day to the day picked
 * (today, or the one under the pointer) and that day's activities.
 */
import {
  calendarQuery,
  fitnessChange,
  fitnessQuery,
  fitnessScale,
  FITNESS_RANGES,
  addDays,
  loadRadius,
  rangePeriod,
  spreadDays,
  untilToday,
  type CalendarDay,
  type FitnessRange,
} from '@zankup/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { EChartsType } from 'echarts/core';
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
} from 'react';
import { Link } from 'react-router';

import { api } from '../../api';
import { EChart } from '../../charts/EChart';
import { t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, ChartSkeleton, Pulse } from '../../Skeleton';
import { SportIcon } from '../../SportIcon';
import { useResolvedTheme } from '../../theme';
import { InfoTip } from '../../ui';
import {
  asSport,
  monthName,
  palette,
  savedFitnessRange,
  saveFitnessRange,
  shortDate,
  useToday,
} from './data';
import { CardLine, ChangePill, ProgressLink } from './parts';

export function FitnessCard() {
  const today = useToday();
  const fitness = useQuery(fitnessQuery(api, rangePeriod('1m', new Date(`${today}T12:00:00`))));
  const values = useMemo(
    () => untilToday(fitness.data ?? [], new Date(`${today}T12:00:00`)).map((d) => d.ctl),
    [fitness.data, today],
  );
  if (fitness.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const now = values.at(-1);
  const change = now != null ? fitnessChange(values[0] ?? now, now) : null;
  return (
    <ProgressLink
      to="/progress/fitness"
      icon="monitoring"
      title={t('progress.fitness')}
      when={t('progress.lastMonth')}
      label={`${t('progress.fitness')}, ${t('progress.score')} ${now != null ? Math.round(now) : '–'}`}
    >
      {now == null || !change ? (
        <p className="note">{t('progress.noFitness')}</p>
      ) : (
        <div className="fxbody">
          <div className="fxfigure">
            <span>{t('progress.score')}</span>
            <span className="fxrow">
              <b className="num fxbig">{Math.round(now)}</b>
              {change.direction !== 'none' && (
                <ChangePill up={change.direction === 'up'} good={change.direction === 'up'}>
                  {Math.abs(change.points)}
                </ChangePill>
              )}
            </span>
          </div>
          {values.length > 1 && (
            <CardLine
              values={values}
              first={String(Math.round(values[0] ?? 0))}
              area
              level
              label={`${t('progress.fitness')} ${Math.round(values[0] ?? 0)} → ${Math.round(now)}`}
            />
          )}
        </div>
      )}
    </ProgressLink>
  );
}

/** Room kept under the chart for the day's activities, so the chart fills the rest of the window. */
const BELOW = 150;

/** The chart as tall as the window leaves it below its top, as Strava's (never under 280 px). */
function useFillHeight() {
  const box = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState(360);
  useLayoutEffect(() => {
    const measure = () => {
      // From the page's top, so a scrolled window measures the same.
      const top = (box.current?.getBoundingClientRect().top ?? 0) + window.scrollY;
      setHeight(Math.round(Math.min(760, Math.max(280, window.innerHeight - top - BELOW))));
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, []);
  return [box, height] as const;
}

export function FitnessPage() {
  const today = useToday();
  const [range, setRange] = useState<FitnessRange>(savedFitnessRange);
  // The last range stays on screen while another loads, so its line turns into the new one.
  const fitness = useQuery({
    ...fitnessQuery(api, rangePeriod(range, new Date(`${today}T12:00:00`))),
    placeholderData: keepPreviousData,
  });
  const days = useMemo(
    () => untilToday(fitness.data ?? [], new Date(`${today}T12:00:00`)),
    [fitness.data, today],
  );
  const last = days.length - 1;
  // The day under the pointer; none is the last one (today). It stays when the pointer leaves the
  // chart, so its activities below can be opened.
  const [picked, setPicked] = useState<number | null>(null);
  const index = Math.max(0, Math.min(picked ?? last, last));
  const day = days[index];
  const first = days[0];
  const change = day && first ? fitnessChange(first.ctl, day.ctl) : null;
  const [box, height] = useFillHeight();

  const choose = (r: FitnessRange) => {
    setRange(r);
    saveFitnessRange(r);
    setPicked(null);
  };

  return (
    <section className="page fxpage">
      <div className="titlebar">
        <h1>{t('progress.fitness')}</h1>
        <InfoTip text={t('progress.fitnessInfo')} />
      </div>
      <p className="fxintro">{t('progress.fitnessIntro')}</p>
      <div className="fxtabs" role="tablist" aria-label={t('progress.fitness')}>
        {FITNESS_RANGES.map((r) => (
          <button key={r} type="button" role="tab" aria-selected={r === range} onClick={() => choose(r)}>
            {t(`progress.range.${r}`)}
          </button>
        ))}
      </div>
      <div ref={box}>
        {fitness.isPending ? (
          <Pulse>
            <ChartSkeleton height={height - 40} />
          </Pulse>
        ) : !day || !first || !change ? (
          <p className="note">{t('progress.noFitness')}</p>
        ) : (
          <>
            <div className="fxchange" aria-live="polite">
              <b className="num">
                {change.direction === 'up' ? '▴' : change.direction === 'down' ? '▾' : ''}
                {change.percent} %
              </b>
              <span className={change.direction === 'up' ? 'num good' : 'num'}>
                {change.direction === 'none'
                  ? t('progress.noChange')
                  : t('progress.points', { points: change.points > 0 ? `+${change.points}` : change.points })}
              </span>
              <p>
                {index === last
                  ? t(`progress.last.${range}`)
                  : t('progress.between', { from: shortDate(first.day), to: shortDate(day.day) })}
              </p>
            </div>
            <FitnessChart
              days={days}
              today={today}
              range={range}
              index={index}
              onPick={setPicked}
              height={height}
            />
            <DayActivities key={day.day} day={day.day} />
          </>
        )}
      </div>
    </section>
  );
}

type Day = { day: string; ctl: number; load: number };

/**
 * The score as a filled line over the range, a ring on each day with load (bigger, harder) over a
 * month, and the picked day marked with its score in a badge. The pointer (or a tap) picks a day; so
 * do the arrow keys. Another range turns the line into the new one (the series keep their ids).
 */
function FitnessChart({
  days,
  today,
  range,
  index,
  onPick,
  height,
}: {
  days: Day[];
  today: string;
  range: FitnessRange;
  index: number;
  onPick: (index: number) => void;
  height: number;
}) {
  const theme = useResolvedTheme();
  const [chart, setChart] = useState<EChartsType | null>(null);
  // Bumped when the chart is drawn again or resized, so the marker is placed on what is on screen.
  const [, redraw] = useState(0);
  const wrap = useRef<HTMLDivElement>(null);
  const count = days.length;
  const short = range === '1m' || range === '3m';
  const showLoads = range === '1m';

  const option = useMemo(() => {
    const c = palette(theme);
    const { max, ticks } = fitnessScale(days.map((d) => d.ctl));
    const marks = new Set(spreadDays(days.length, 5));
    const maxLoad = Math.max(0, ...days.map((d) => d.load));
    const label = (d: string) => (d === today ? t('home.today') : short ? shortDate(d) : monthName(d));
    return {
      animationDurationUpdate: 600,
      grid: { left: 36, right: 24, top: 46, bottom: 26 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: days.map((d) => d.day),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          interval: (i: number) => marks.has(i),
          formatter: (d: string) => label(d),
          color: c.muted,
          fontSize: 11,
          alignMinLabel: 'left',
          alignMaxLabel: 'right',
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max,
        interval: ticks[1] ?? max,
        axisLabel: { color: c.muted, fontSize: 11 },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          id: 'ctl',
          type: 'line',
          data: days.map((d) => d.ctl),
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.accent, width: 2.5, join: 'round', cap: 'round' },
          areaStyle: { color: c.accent, opacity: 0.22 },
        },
        {
          // Each day's load as a ring over a month (none on a day without), sliding with the line.
          id: 'loads',
          type: 'scatter',
          silent: true,
          data: showLoads ? days.map((d, i) => [i, d.ctl, d.load]) : [],
          symbolSize: (v: number[]) => 2 * loadRadius(v[2] ?? 0, maxLoad),
          itemStyle: { color: c.bg, borderColor: c.accent, borderWidth: 2 },
        },
      ],
    };
  }, [days, theme, today, short, showLoads]);

  // The pointer picks the day under it (on a phone, a tap or a slide does).
  const pick = useRef(onPick);
  useEffect(() => {
    pick.current = onPick;
  });
  const ready = useCallback(
    (instance: EChartsType) => {
      setChart(instance);
      redraw((n) => n + 1);
      const move = (event: { offsetX: number; offsetY: number }) => {
        const at = instance.convertFromPixel({ gridIndex: 0 }, [event.offsetX, event.offsetY]);
        const i = Math.round(Array.isArray(at) ? (at[0] ?? 0) : at);
        if (Number.isFinite(i)) pick.current(Math.max(0, Math.min(count - 1, i)));
      };
      instance.getZr().on('mousemove', move);
      instance.getZr().on('click', move);
      return () => {
        instance.getZr().off('mousemove', move);
        instance.getZr().off('click', move);
      };
    },
    [count],
  );

  // The marker follows the chart's size.
  useEffect(() => {
    const element = wrap.current;
    if (!element || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(() => redraw((n) => n + 1));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const keys = (event: KeyboardEvent) => {
    const step = { ArrowLeft: -1, ArrowRight: 1 }[event.key];
    const to =
      event.key === 'Home' ? 0 : event.key === 'End' ? count - 1 : step != null ? index + step : null;
    if (to == null) return;
    event.preventDefault();
    onPick(Math.max(0, Math.min(count - 1, to)));
  };

  const day = days[index];
  const point = ((): number[] | null => {
    try {
      return chart && !chart.isDisposed() && day
        ? chart.convertToPixel({ seriesId: 'ctl' }, [index, day.ctl])
        : null;
    } catch {
      return null; // Not drawn yet.
    }
  })();
  const summary = day ? `${t('progress.fitness')} ${Math.round(day.ctl)}, ${shortDate(day.day, true)}` : '';

  return (
    <div
      ref={wrap}
      className="fxchart"
      tabIndex={0}
      role="slider"
      aria-label={t('progress.fitness')}
      aria-valuemin={0}
      aria-valuemax={count - 1}
      aria-valuenow={index}
      aria-valuetext={summary}
      onKeyDown={keys}
    >
      <EChart option={option} theme={theme} height={height} summary={summary} onReady={ready} />
      {point && day && (
        <div className="fxmark" style={{ left: point[0], top: point[1] }} aria-hidden="true">
          <i className="fxstem" style={{ height: height - 26 - (point[1] ?? 0) + 24 }} />
          <i className="fxhalo" />
          <i className="fxdot" />
          <b className="fxscore num">{Math.round(day.ctl)}</b>
        </div>
      )}
    </div>
  );
}

/** The picked day's activities, each opening its page; more than one, how many until clicked. */
function DayActivities({ day }: { day: string }) {
  const [expanded, setExpanded] = useState(false);
  const calendar = useQuery(calendarQuery(api, { from: day, to: addDays(day, 1) }));
  const activities =
    (calendar.data as CalendarDay[] | undefined)?.find((d) => d.day === day)?.activities ?? [];
  const when = shortDate(day, true);
  if (calendar.isPending) return <div className="fxday" aria-busy="true" />;
  if (!activities.length)
    return (
      <div className="fxday">
        <span className="fxdayicon muted">
          <MaterialIcon name="timeline" />
        </span>
        <span>
          <b className="muted">{t('progress.noActivities')}</b>
          <small>{when}</small>
        </span>
      </div>
    );
  if (activities.length > 1 && !expanded)
    return (
      <button type="button" className="fxday" aria-expanded={false} onClick={() => setExpanded(true)}>
        <span className="fxdayicon">
          <MaterialIcon name="timeline" />
        </span>
        <span>
          <b>{t('calendar.activities', { count: activities.length })}</b>
          <small>{when}</small>
        </span>
        <MaterialIcon name="keyboard_arrow_down" />
      </button>
    );
  return (
    <ul className="fxdaylist">
      {activities.map((a) => (
        <li key={a.id}>
          <Link to={`/activities/${a.id}`} className="fxday">
            <SportIcon sport={asSport(a.sport)} />
            <span>
              <b>{a.name ?? t(`sport.${asSport(a.sport)}`)}</b>
              <small>{when}</small>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
