/**
 * Relative effort on Progress (as Strava's, as the app's): the card with this week's score, whether it
 * is in the range of the three weeks before, last week's score and both weeks built up day by day; the
 * page with a week on a panel of its status's colour (its score, what it means, its days as bars), the
 * twelve weeks around it with the range each was judged against, and the week's activities.
 */
import {
  activityBand,
  addDays,
  cumulative,
  formatHoursMinutes,
  isoDay,
  monthLabels,
  SPORT_ICONS,
  weekAt,
  weekDays,
  type EffortRange,
  type EffortStatus,
  type EffortWeek,
} from '@zankup/core';
import type { EChartsType } from 'echarts/core';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';

import { EChart } from '../../charts/EChart';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, ChartSkeleton, Pulse } from '../../Skeleton';
import { useResolvedTheme } from '../../theme';
import { InfoTip } from '../../ui';
import {
  asSport,
  at,
  dayLetter,
  palette,
  statusPaint,
  useCalendarDays,
  useEffortDays,
  useEffortWeeks,
  useToday,
  type CalendarActivities,
} from './data';
import { ProgressLink } from './parts';

const WEEKS = 52;
const WINDOW = 12;
const longDate = (iso: string) =>
  at(iso).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });

export function EffortCard() {
  const { weeks, isPending } = useEffortWeeks(2);
  const days = useEffortDays();
  if (isPending || days.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={4} />
      </Pulse>
    );
  const now = weeks.at(-1);
  const before = weeks.at(-2);
  return (
    <ProgressLink
      to="/progress/effort"
      icon="speed"
      title={t('progress.effort')}
      when={t('progress.thisWeek')}
      label={`${t('progress.effort')}: ${now?.score ?? 0}`}
    >
      {!now ? (
        <p className="note">{t('progress.noEffort')}</p>
      ) : (
        <div className="fxbody">
          <div className="fxfigure">
            <span>{t('progress.score')}</span>
            <span className="fxrow">
              <b className="num fxbig">{now.score}</b>
              {now.range && (
                <span className="fxstatus" data-status={now.status}>
                  {t(`progress.effortStatus.${now.status}`)}
                </span>
              )}
            </span>
            {before && <span>{t('progress.lastWeekScore', { score: before.score })}</span>}
          </div>
          <WeekSoFar
            thisWeek={days.thisWeek}
            lastWeek={days.lastWeek}
            range={now.range}
            status={now.status}
          />
        </div>
      )}
    </ProgressLink>
  );
}

/** Both weeks added up day by day over the range's band, today marked, the days written under. */
function WeekSoFar({
  thisWeek,
  lastWeek,
  range,
  status,
}: {
  thisWeek: number[];
  lastWeek: number[];
  range: EffortRange | null;
  status: EffortStatus;
}) {
  const theme = useResolvedTheme();
  const today = useToday();
  const option = useMemo(() => {
    const c = palette(theme);
    const day = (at(today).getDay() + 6) % 7;
    const now = cumulative(thisWeek, day);
    const before = cumulative(lastWeek);
    const max = Math.max(1, range?.hi ?? 0, before.at(-1) ?? 0, now.at(-1) ?? 0) * 1.1;
    const colour = statusPaint(theme, status);
    const end = now.length - 1;
    return {
      grid: { left: range ? 56 : 8, right: 10, top: 8, bottom: 24 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: weekDays(weekAt(0, today)).map(dayLetter),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          interval: 0,
          fontSize: 12,
          color: (_: unknown, i: number) => (i === day ? c.fg : c.muted),
          fontWeight: (_: unknown, i: number) => (i === day ? 700 : 400),
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max,
        interval: max,
        axisLabel: { show: false },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          id: 'before',
          type: 'line',
          data: before,
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.muted, width: 2.5 },
          markArea: range
            ? {
                silent: true,
                itemStyle: { color: colour, opacity: 0.18 },
                label: {
                  show: true,
                  position: 'left',
                  distance: 6,
                  color: c.muted,
                  fontSize: 12,
                  formatter: `${range.lo}-${range.hi}`,
                },
                data: [[{ yAxis: range.lo }, { yAxis: range.hi }]],
              }
            : undefined,
        },
        {
          id: 'now',
          type: 'line',
          data: now,
          symbol: 'none',
          silent: true,
          lineStyle: { color: colour, width: 3 },
        },
        {
          id: 'halo',
          type: 'scatter',
          silent: true,
          data: end >= 0 ? [[end, now[end]]] : [],
          symbolSize: 24,
          itemStyle: { color: colour, opacity: 0.25 },
        },
        {
          id: 'ends',
          type: 'scatter',
          silent: true,
          data:
            end >= 0
              ? [
                  {
                    value: [0, now[0]],
                    symbolSize: 10,
                    itemStyle: { color: c.surface, borderColor: colour, borderWidth: 2.5 },
                  },
                  { value: [end, now[end]], symbolSize: 12, itemStyle: { color: colour } },
                ]
              : [],
        },
      ],
    };
  }, [theme, today, thisWeek, lastWeek, range, status]);
  return (
    <EChart
      option={option}
      theme={theme}
      height={104}
      className="fxweekline"
      summary={`${t('progress.thisWeek')} ${thisWeek.reduce((a, b) => a + b, 0).toFixed(0)}${
        range ? `, ${range.lo}-${range.hi}` : ''
      }`}
    />
  );
}

export function EffortPage() {
  const { weeks, isPending } = useEffortWeeks(WEEKS);
  // The weeks' activities at once: the week under the pointer reads its own from them.
  const calendar = useCalendarDays(
    weeks[0]?.monday ?? isoDay(new Date()),
    addDays(weeks.at(-1)?.monday ?? isoDay(new Date()), 7),
  );
  const [picked, setPicked] = useState<number | null>(null);
  // The last week the chart shows (none: the latest). Only the arrows move it.
  const [end, setEnd] = useState<number | null>(null);
  // Where the weeks stand: the week picked and the chart's window around it.
  const count = weeks.length;
  const index = Math.min(picked ?? count - 1, count - 1);
  const last = Math.min(end ?? count - 1, count - 1);
  const first = Math.max(0, last - WINDOW + 1);
  /** The arrows: the week before or after, the chart following when it leaves it. */
  const go = (to: number) => {
    const next = Math.max(0, Math.min(count - 1, to));
    if (next === index) return;
    setPicked(next);
    if (next < first) setEnd(next + WINDOW - 1);
    else if (next > last) setEnd(next);
  };
  // The keyboard's left and right arrows do what the card's arrows do.
  const keyStep = useRef(go);
  useEffect(() => {
    keyStep.current = go;
  });
  useEffect(() => {
    const keys = (event: globalThis.KeyboardEvent) => {
      const by = { ArrowLeft: -1, ArrowRight: 1 }[event.key];
      if (by == null || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
      const target = event.target as HTMLElement | null;
      if (target?.closest('input, textarea, select, [contenteditable="true"], [role="slider"]')) return;
      event.preventDefault();
      keyStep.current(index + by);
    };
    window.addEventListener('keydown', keys);
    return () => window.removeEventListener('keydown', keys);
  }, [index]);
  const body = () => {
    if (isPending)
      return (
        <Pulse className="fxeffort">
          <ChartSkeleton height={300} />
          <ChartSkeleton height={220} />
        </Pulse>
      );
    if (!weeks.length) return <p className="note">{t('progress.noEffort')}</p>;
    const week = weeks[index] as EffortWeek;
    return (
      <div className="fxeffort">
        <div className="fxweek" data-status={week.status}>
          <div className="fxweeknav">
            <button
              type="button"
              className="iconbtn"
              aria-label={t('progress.prevWeek')}
              title={t('progress.prevWeek')}
              disabled={index === 0}
              onClick={() => go(index - 1)}
            >
              <MaterialIcon name="chevron_left" />
            </button>
            <span className="num">
              {longDate(week.monday)} - {longDate(addDays(week.monday, 6))}
            </span>
            <button
              type="button"
              className="iconbtn"
              aria-label={t('progress.nextWeek')}
              title={t('progress.nextWeek')}
              disabled={index === weeks.length - 1}
              onClick={() => go(index + 1)}
            >
              <MaterialIcon name="chevron_right" />
            </button>
          </div>
          <div className="fxrow">
            <b className="num fxhuge">{week.score}</b>
            <InfoTip text={t('progress.effortInfo')} />
          </div>
          <h2>{t(`progress.effortTitle.${week.status}`)}</h2>
          <p>{t(`progress.effortText.${week.status}`)}</p>
          <DayBars monday={week.monday} calendar={calendar.days} />
        </div>
        <div className="fxweekside">
          <WeeksChart weeks={weeks} index={index} first={first} onPick={setPicked} />
          <WeekActivities monday={week.monday} calendar={calendar.days} />
        </div>
      </div>
    );
  };
  return (
    <section className="page fxpage">
      <div className="titlebar">
        <h1>{t('progress.effort')}</h1>
      </div>
      {body()}
    </section>
  );
}

/** The week's days as bars of their effort, a dash on a day without any. */
function DayBars({ monday, calendar }: { monday: string; calendar: Map<string, CalendarActivities> }) {
  const days = weekDays(monday);
  const values = days.map((d) => (calendar.get(d) ?? []).reduce((s, a) => s + (a.load ?? 0), 0));
  const max = Math.max(1, ...values);
  return (
    <ul className="fxbars">
      {days.map((d, i) => {
        const v = values[i] ?? 0;
        return (
          <li key={d} title={`${longDate(d)}: ${Math.round(v)}`}>
            <i style={{ height: v ? Math.max(8, (v / max) * 110) : 2 }} />
            <span aria-hidden="true">{dayLetter(d)}</span>
            <span className="visually-hidden">
              {longDate(d)}: {Math.round(v)}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/** Twelve weeks from `first`: their ranges as a band, their scores as rings, the picked one filled. */
function WeeksChart({
  weeks,
  index,
  first,
  onPick,
}: {
  weeks: EffortWeek[];
  index: number;
  first: number;
  onPick: (i: number) => void;
}) {
  const theme = useResolvedTheme();
  const shown = useMemo(() => weeks.slice(first, first + WINDOW), [weeks, first]);
  const sel = index - first;
  const option = useMemo(() => {
    const c = palette(theme);
    const max = Math.max(1, ...shown.map((w) => Math.max(w.score, w.range?.hi ?? 0)));
    const colour = statusPaint(theme, weeks[index]?.status ?? 'in');
    const labels = new Map(
      monthLabels(
        shown.map((w) => w.monday),
        locale,
      ).map((l) => [l.index, l.text]),
    );
    const band = { type: 'line', stack: 'band', symbol: 'none', silent: true, lineStyle: { opacity: 0 } };
    return {
      grid: { left: 8, right: 44, top: 34, bottom: 30 },
      xAxis: {
        type: 'category',
        data: shown.map((w) => w.monday),
        boundaryGap: false,
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: true, lineStyle: { color: c.line } },
        axisLabel: {
          interval: 0,
          color: c.fg,
          fontSize: 13,
          formatter: (_: string, i: number) => labels.get(i) ?? '',
        },
      },
      yAxis: {
        type: 'value',
        position: 'right',
        min: 0,
        max,
        interval: max / 4,
        splitLine: { show: false },
        axisTick: { show: true, lineStyle: { color: c.muted } },
        axisLabel: { color: c.fg, fontSize: 13, formatter: (v: number) => (v === max ? String(max) : '') },
      },
      series: [
        { ...band, id: 'bandLo', data: shown.map((w) => w.range?.lo ?? '-') },
        {
          ...band,
          id: 'band',
          data: shown.map((w) => (w.range ? w.range.hi - w.range.lo : '-')),
          areaStyle: { color: c.muted, opacity: 0.35 },
        },
        {
          id: 'picked',
          type: 'scatter',
          silent: true,
          data: [[sel, shown[sel]?.score ?? 0]],
          symbolSize: 32,
          itemStyle: { color: colour, opacity: 0.3 },
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { color: colour, type: 'solid', width: 1.5 },
            data: [{ xAxis: sel }],
          },
        },
        {
          id: 'scores',
          type: 'scatter',
          silent: true,
          data: shown.map((w, i) =>
            i === sel
              ? { value: [i, w.score], symbolSize: 16, itemStyle: { color: colour } }
              : {
                  value: [i, w.score],
                  symbolSize: 10,
                  itemStyle: { color: c.surface, borderColor: c.fg, borderWidth: 2 },
                },
          ),
        },
      ],
    };
  }, [theme, shown, sel, weeks, index]);

  const pick = useRef({ onPick, first, count: shown.length });
  useEffect(() => {
    pick.current = { onPick, first, count: shown.length };
  });
  const ready = useCallback((instance: EChartsType) => {
    const move = (event: { offsetX: number; offsetY: number }) => {
      const value = instance.convertFromPixel({ gridIndex: 0 }, [event.offsetX, event.offsetY]);
      const i = Math.round(Array.isArray(value) ? (value[0] ?? 0) : value);
      const { onPick: to, first: from, count } = pick.current;
      if (Number.isFinite(i)) to(from + Math.max(0, Math.min(count - 1, i)));
    };
    instance.getZr().on('mousemove', move);
    instance.getZr().on('click', move);
    return () => {
      instance.getZr().off('mousemove', move);
      instance.getZr().off('click', move);
    };
  }, []);
  const week = weeks[index];
  return (
    <EChart
      option={option}
      theme={theme}
      height={260}
      onReady={ready}
      className="fxweeks"
      summary={
        week
          ? `${t('progress.effort')} ${week.score}${week.range ? `, ${week.range.lo}-${week.range.hi}` : ''}, ${longDate(week.monday)}`
          : t('progress.effort')
      }
    />
  );
}

/** The week's activities, newest last: when, what, how long, and their effort in its band's colour. */
function WeekActivities({ monday, calendar }: { monday: string; calendar: Map<string, CalendarActivities> }) {
  const activities = weekDays(monday).flatMap((d) => calendar.get(d) ?? []);
  if (!activities.length) return null;
  return (
    <section className="panel fxacts">
      <h2>{t('progress.weekActivities')}</h2>
      <ul>
        {activities.map((a) => {
          const start = new Date(a.start);
          const effort = Math.round(a.load ?? 0);
          const sport = asSport(a.sport);
          return (
            <li key={a.id}>
              <Link to={`/activities/${a.id}`}>
                <MaterialIcon name={SPORT_ICONS[sport]} />
                <span>
                  <small>
                    {t('progress.activityWhen', {
                      date: start.toLocaleDateString(locale, { day: 'numeric', month: 'long' }),
                      time: start.toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' }),
                    })}
                  </small>
                  <b>{a.name ?? t(`sport.${sport}`)}</b>
                  <small>{formatHoursMinutes(a.moving_s)}</small>
                </span>
                <b className="num fxeffortval" data-band={activityBand(effort)}>
                  {effort}
                </b>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
