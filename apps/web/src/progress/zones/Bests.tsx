/**
 * Best times on Progress (OS-1004), as the app's and Strava's. The card shows the last twelve months'
 * medals, one per measure and up to three, and opens the page on running 10 km. The page has one measure
 * (a run distance, a ride's best power over a duration or its longest ride) in two tabs. Analysis: the
 * record with the second and third, a chart of one slot per best with its trend, twelve at a time, and
 * those bests by year below, each with its medal of the year; the chart (dragged or wheeled) and the
 * list (scrolled) move together. Top 10: the ten best. Each best opens its activity.
 */
import {
  bestsQuery,
  bestValue,
  CHART_SLOTS,
  chartScale,
  cardMedals,
  commonTokens,
  effortShown,
  formatters,
  lowerIsBetter,
  MEDAL_COLOURS,
  offsetForScroll,
  offsetRange,
  placeOf,
  racePace,
  ranked,
  recentBestsQuery,
  RIDE_KEYS,
  rightIndex,
  RUN_KEYS,
  RUN_METRES,
  scrollForOffset,
  SPORT_ICONS,
  themes,
  trend,
  yearMedal,
  yearsOf,
  type Best,
  type ThemeName,
} from '@zankup/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { EChartsType } from 'echarts/core';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { api } from '../../api';
import { EChart } from '../../charts/EChart';
import { resample } from '../../charts/resample';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { usePrefs } from '../../prefs';
import { Bone, CardSkeleton, Pulse } from '../../Skeleton';
import { useResolvedTheme } from '../../theme';
import { CardTitle, InfoTip } from '../../ui';
import { bestName, longDate, sportOf } from './data';

/** A medal with its place, as Strava's: the first is "PR" (also on the activity's Results). */
export function Medal({ rank, size = 40 }: { rank: number; size?: number }) {
  return (
    <span
      className={`pb-medal${rank === 1 ? ' pr' : ''}`}
      style={{ '--size': `${size}px`, color: MEDAL_COLOURS[rank - 1] ?? 'var(--muted)' } as CSSProperties}
      aria-hidden="true"
    >
      <MaterialIcon name="military_tech-fill" />
      <b>{rank === 1 ? 'PR' : rank}</b>
    </span>
  );
}

function useUnit() {
  const prefs = usePrefs();
  return { unit: formatters(prefs).unit, imperial: prefs.units === 'imperial' };
}

export function BestsCard() {
  const { unit, imperial } = useUnit();
  const medals = useQuery(recentBestsQuery(api, 365));
  if (medals.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const shown = cardMedals(medals.data ?? [], (key) => effortShown(key, unit));
  return (
    <Link to="/progress/bests" className="panel pb-card tap">
      <CardTitle right={<MaterialIcon name="chevron_right" />}>
        <MaterialIcon name="trophy" />
        {t('progress.bests')}
      </CardTitle>
      {!shown.length ? (
        <p className="note">{t('progress.noRecentBests')}</p>
      ) : (
        <ul className="pb-medals">
          {shown.map((m) => (
            <li key={`${m.key}-${m.activity_id}`}>
              <Medal rank={m.rank} size={44} />
              <span>{bestName(m.key)}</span>
              <b className="num">
                <MaterialIcon name={SPORT_ICONS[sportOf(m.key)]} />
                {bestValue(m.key, m.value, locale, imperial)}
              </b>
              <small className="note">
                {t(m.scope === 'all' ? 'progress.allTimeBest' : 'progress.yearBest')}
              </small>
            </li>
          ))}
        </ul>
      )}
    </Link>
  );
}

/** How long the list's scroll events after the chart moved it are taken for its echo. */
const ECHO_MS = 150;

export function BestsPage() {
  const { unit, imperial } = useUnit();
  const [params, setParams] = useSearchParams();
  const key = params.get('key') ?? '10k';
  const [tab, setTab] = useState<'analysis' | 'top'>('analysis');
  const sport = sportOf(key);
  const keys = (sport === 'run' ? [...RUN_KEYS] : [...RIDE_KEYS]).filter((k) => effortShown(k, unit));
  // An hour fresh: bests change only with a new activity. While another measure loads, the last one
  // stays (no skeleton), and the chart moves to the new one when it comes.
  const bests = useQuery({
    ...bestsQuery(api, key),
    staleTime: 60 * 60_000,
    placeholderData: keepPreviousData,
  });
  const [shownKey, setShownKey] = useState(key);
  if (bests.data && !bests.isPlaceholderData && shownKey !== key) setShownKey(key);
  const all = useMemo(() => (bests.data ?? []) as Best[], [bests.data]);
  const top = ranked(shownKey, all);
  const record = top[0];
  const value = useCallback((v: number) => bestValue(shownKey, v, locale, imperial), [shownKey, imperial]);
  const pace = useCallback(
    (v: number) =>
      sportOf(shownKey) === 'run'
        ? `${racePace(v, RUN_METRES[shownKey] ?? 1000, imperial)} /${imperial ? 'mi' : 'km'}`
        : null,
    [shownKey, imperial],
  );
  const pick = (k: string) => setParams({ key: k }, { replace: true });

  // Newest first, as the list shows them; oldest first, as the chart draws them.
  const ordered = useMemo(() => [...all].sort((a, b) => (a.day < b.day ? 1 : a.day > b.day ? -1 : 0)), [all]);
  const chrono = useMemo(() => [...ordered].reverse(), [ordered]);
  const medals = useMemo(
    () => new Map(all.map((b) => [b.activity_id, yearMedal(shownKey, all, b)])),
    [all, shownKey],
  );
  const n = chrono.length;
  const [low, high] = offsetRange(n);

  // The chart's left slot, shared with the list: the newest best at the right edge for a new measure.
  const [view, setView] = useState<{ for: Best[]; offset: number; scrolled: boolean }>({
    for: chrono,
    offset: high,
    scrolled: false,
  });
  if (view.for !== chrono) setView({ for: chrono, offset: high, scrolled: false });
  const offset = view.for === chrono ? view.offset : high;
  const list = useRef<HTMLDivElement>(null);
  // When the chart last scrolled the list: the list's own scroll events right after are its echo, not
  // the hand, and following them would send the chart back and forth (a trackpad sends many at once).
  const chartMoved = useRef(0);
  const frame = useRef(0);
  useEffect(() => () => cancelAnimationFrame(frame.current), []);
  useEffect(() => {
    if (list.current) list.current.scrollTop = 0;
  }, [chrono]);
  const measure = () => {
    const box = list.current;
    if (!box) return { tops: [] as number[], header: 0 };
    const rows = [...box.querySelectorAll<HTMLElement>('[data-row]')];
    const header = box.querySelector<HTMLElement>('.pb-year')?.offsetHeight ?? 0;
    return { tops: rows.map((r) => r.offsetTop), header };
  };
  const onListScroll = () => {
    if (performance.now() - chartMoved.current < ECHO_MS) return;
    // Once a frame, however many events the trackpad sends.
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      const box = list.current;
      if (!box) return;
      const { tops, header } = measure();
      const o = offsetForScroll(box.scrollTop, tops, header);
      if (o != null) setView({ for: chrono, offset: o, scrolled: true });
    });
  };
  const onChartMove = (o: number) => {
    const next = Math.min(high, Math.max(low, o));
    setView({ for: chrono, offset: next, scrolled: true });
    const box = list.current;
    if (!box) return;
    const { tops, header } = measure();
    if (tops.length < n) return;
    cancelAnimationFrame(frame.current);
    chartMoved.current = performance.now();
    box.scrollTop = scrollForOffset(next, tops, header);
  };
  const toYear = (year: string) => {
    const box = list.current;
    if (!box) return;
    chartMoved.current = 0;
    const { tops, header } = measure();
    const k = year ? ordered.findIndex((b) => b.day <= `${year}-12-31`) : 0;
    box.scrollTo({ top: k <= 0 ? 0 : Math.max(0, (tops[k] ?? 0) - header), behavior: 'smooth' });
  };

  return (
    <section className="page pb-page">
      <div className="titlebar">
        <h1>{bestName(key)}</h1>
        <InfoTip text={t('progress.bestsInfo')} />
      </div>
      <div className="tabs" role="tablist">
        {(['analysis', 'top'] as const).map((tb) => (
          <button key={tb} type="button" role="tab" aria-selected={tb === tab} onClick={() => setTab(tb)}>
            {t(tb === 'analysis' ? 'progress.analysis' : 'progress.top10')}
          </button>
        ))}
      </div>
      <div className="pb-filters">
        <div className="chips" role="radiogroup" aria-label={t('progress.filterSport')}>
          {(['run', 'ride'] as const).map((s) => (
            <button
              key={s}
              type="button"
              role="radio"
              className="chip"
              aria-checked={s === sport}
              onClick={() => s !== sport && pick(s === 'run' ? '10k' : 'longest')}
            >
              <MaterialIcon name={SPORT_ICONS[s]} />
              {t(`sport.${s}`)}
            </button>
          ))}
        </div>
        <div className="chips wrap" role="radiogroup" aria-label={t('progress.bests')}>
          {keys.map((k) => (
            <button
              key={k}
              type="button"
              role="radio"
              className="chip"
              aria-checked={k === key}
              onClick={() => pick(k)}
            >
              {bestName(k)}
            </button>
          ))}
        </div>
      </div>

      {!bests.data ? (
        bests.isError ? (
          <p className="note">{t('progress.noBests')}</p>
        ) : (
          <Pulse>
            <CardSkeleton lines={2} />
            <Bone height={260} radius={12} />
          </Pulse>
        )
      ) : !record ? (
        <p className="note">{t('progress.noBests')}</p>
      ) : tab === 'analysis' ? (
        <div className="pb-analysis">
          <div className="panel pb-chartcard">
            <h2>{t('progress.allTime')}</h2>
            <div className="pb-record">
              <Medal rank={1} size={52} />
              <div>
                <b className="num">{value(record.value)}</b>
                {pace(record.value) && <span className="note">{pace(record.value)}</span>}
                <small className="note">{longDate(record.day)}</small>
              </div>
              <ul>
                {top.slice(1, 3).map((b, i) => (
                  <li key={b.activity_id}>
                    <Medal rank={i + 2} size={24} />
                    <span className="num">{value(b.value)}</span>
                  </li>
                ))}
              </ul>
            </div>
            <BestsChart
              bestKey={shownKey}
              all={all}
              chrono={chrono}
              medals={medals}
              offset={offset}
              instant={view.scrolled}
              onMove={onChartMove}
              label={value}
              pace={pace}
            />
          </div>
          <div className="panel pb-listcard">
            <label className="pb-yearpick">
              <span>{t('progress.filterYear')}</span>
              <select defaultValue="" onChange={(e) => toYear(e.target.value)}>
                <option value="">{t('progress.last12Months')}</option>
                {yearsOf(all).map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </label>
            <div className="pb-list" ref={list} onScroll={onListScroll}>
              {yearsOf(ordered).map((y) => (
                <section key={y} className="pb-group" aria-label={y}>
                  {/* The year's header stays on top while its bests scroll under it. */}
                  <h3 className="pb-year">{y}</h3>
                  {ordered
                    .filter((b) => b.day.startsWith(y))
                    .map((b) => {
                      const medal = medals.get(b.activity_id);
                      return (
                        <Link
                          key={b.activity_id}
                          to={`/activities/${b.activity_id}`}
                          className="pb-row"
                          data-row=""
                        >
                          <span>{longDate(b.day, false)}</span>
                          {medal && (
                            <span className="pb-yearmedal">
                              <Medal rank={medal} size={22} />
                              {y}
                            </span>
                          )}
                          <b className="num">{value(b.value)}</b>
                          {pace(b.value) && <span className="note num">{pace(b.value)}</span>}
                        </Link>
                      );
                    })}
                </section>
              ))}
            </div>
          </div>
        </div>
      ) : (
        <ol className="panel pb-top" aria-label={t('progress.top10')}>
          {top.slice(0, 10).map((b) => {
            const place = placeOf(shownKey, all, b);
            return (
              <li key={b.activity_id}>
                <Link to={`/activities/${b.activity_id}`}>
                  <span
                    className={`pb-place${place <= 3 ? ' medal' : ''}`}
                    style={
                      place <= 3 ? ({ '--medal': MEDAL_COLOURS[place - 1] } as CSSProperties) : undefined
                    }
                  >
                    {place <= 3 ? <Medal rank={place} size={36} /> : <b>{place}</b>}
                  </span>
                  <span>
                    <b className="num">{value(b.value)}</b>
                    <small className="note">
                      {[pace(b.value), longDate(b.day)].filter(Boolean).join(' · ')}
                    </small>
                  </span>
                  <MaterialIcon name="chevron_right" />
                </Link>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}

const palette = (theme: ThemeName) => ({ ...commonTokens, ...themes[theme] });
const AXIS = 70;

/**
 * The bests as Strava's chart: one slot each, oldest to newest, twelve in view, the better ones higher
 * (a faster time or more watts), the year's medals in their colours, the bests of the year at the right
 * edge filled and the rest hollow, and the trend through them as a smooth line. The scale goes from the
 * record to the slow days and stays while it moves. Dragged (or wheeled) sideways it shows earlier
 * bests; hovering a point reads it out, clicking opens its activity.
 */
function BestsChart({
  bestKey,
  all,
  chrono,
  medals,
  offset,
  instant,
  onMove,
  label,
  pace,
}: {
  bestKey: string;
  all: Best[];
  /** Every best, oldest first. */
  chrono: Best[];
  medals: Map<number, number | null>;
  /** The slot at the left edge, shared with the list. */
  offset: number;
  /** Moved by a drag or a scroll: no animation, it follows the hand. */
  instant: boolean;
  onMove: (offset: number) => void;
  label: (v: number) => string;
  pace: (v: number) => string | null;
}) {
  const theme = useResolvedTheme();
  const navigate = useNavigate();
  const box = useRef<HTMLDivElement>(null);
  const dragged = useRef(false);
  const n = chrono.length;
  const lower = lowerIsBetter(bestKey);
  const { record, worst } = useMemo(() => chartScale(bestKey, all), [bestKey, all]);
  const line = useMemo(() => trend(chrono, 45, chrono), [chrono]);
  const right = rightIndex(offset, n);
  const year = chrono[right]?.day.slice(0, 4) ?? '';
  const c = palette(theme);

  const option = useMemo(() => {
    const span = Math.abs(worst - record) || Math.abs(record) * 0.1 || 1;
    const [yMin, yMax] = lower ? [record, record + span] : [record - span, record];
    const clamp = (v: number) => Math.min(yMax, Math.max(yMin, v));
    return {
      animationDurationUpdate: instant ? 0 : 500,
      grid: { left: AXIS, right: 8, top: 14, bottom: 12 },
      tooltip: {
        trigger: 'item',
        formatter: (p: { data?: { day?: string; raw?: number } }) =>
          p.data?.day && p.data.raw != null
            ? `${longDate(p.data.day)}<br/><b>${label(p.data.raw)}</b>${pace(p.data.raw) ? ` · ${pace(p.data.raw)}` : ''}`
            : '',
      },
      xAxis: {
        type: 'value',
        min: offset - 0.5,
        max: offset + CHART_SLOTS - 0.5,
        interval: 1,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        splitLine: { show: true, lineStyle: { color: c.line } },
      },
      yAxis: {
        type: 'value',
        inverse: lower,
        min: yMin,
        max: yMax,
        interval: span / 2,
        axisLabel: { formatter: (v: number) => label(v), color: c.fg },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          // The trend morphs from one distance to another (see charts/resample); the bests are drawn afresh.
          id: 'trend',
          type: 'line',
          showSymbol: false,
          silent: true,
          clip: true,
          lineStyle: { color: c.fitness, width: 2.5 },
          data:
            n > 1
              ? resample(
                  chrono.map((_, i) => clamp(line[i] ?? record)),
                  { smooth: true },
                )
              : [],
        },
        {
          id: `bests:${bestKey}`,
          type: 'scatter',
          clip: true,
          symbolSize: 12,
          cursor: 'pointer',
          data: chrono.map((b, i) => {
            const m = medals.get(b.activity_id);
            const colour = m ? MEDAL_COLOURS[m - 1] : c.muted;
            return {
              value: [i, clamp(b.value)],
              day: b.day,
              raw: b.value,
              activityId: b.activity_id,
              itemStyle: b.day.startsWith(year)
                ? { color: colour }
                : { color: c.page, borderColor: colour, borderWidth: 2 },
            };
          }),
        },
      ],
    };
  }, [bestKey, chrono, medals, offset, instant, year, record, worst, lower, line, label, pace, c, n]);

  // Clicking a point opens its activity, unless the pointer was dragging the chart.
  const onReady = useCallback(
    (chart: EChartsType) => {
      const open = (params: { data?: unknown }) => {
        const data = params.data as { activityId?: number } | undefined;
        if (data?.activityId && !dragged.current) void navigate(`/activities/${data.activityId}`);
      };
      chart.on('click', open as never);
      return () => chart.off('click');
    },
    [navigate],
  );

  // The wheel moves it sideways (and lets the page scroll at either end).
  const latest = useRef({ offset, onMove });
  useEffect(() => {
    latest.current = { offset, onMove };
  });
  useEffect(() => {
    const element = box.current;
    if (!element) return;
    // A trackpad sends many events a frame: they add up and the chart moves once a frame.
    let pending: number | null = null;
    let raf = 0;
    const wheel = (event: WheelEvent) => {
      const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY;
      const slot = Math.max(1, (element.clientWidth - AXIS - 8) / CHART_SLOTS);
      const [lo, hi] = offsetRange(n);
      const now = pending ?? latest.current.offset;
      const next = Math.min(hi, Math.max(lo, now + delta / slot));
      if (next === now) return;
      event.preventDefault();
      pending = next;
      raf ||= requestAnimationFrame(() => {
        raf = 0;
        if (pending != null) latest.current.onMove(pending);
        pending = null;
      });
    };
    element.addEventListener('wheel', wheel, { passive: false });
    return () => {
      element.removeEventListener('wheel', wheel);
      cancelAnimationFrame(raf);
    };
  }, [n]);

  const drag = useRef<{ x: number; offset: number } | null>(null);
  const left = chrono[Math.max(0, right - CHART_SLOTS + 1)];
  const last = chrono[right];
  return (
    <div
      ref={box}
      className="pb-chart"
      onPointerDown={(event) => {
        drag.current = { x: event.clientX, offset };
        dragged.current = false;
      }}
      onPointerMove={(event) => {
        const start = drag.current;
        if (!start) return;
        const dx = event.clientX - start.x;
        if (!dragged.current && Math.abs(dx) < 6) return;
        if (!dragged.current) event.currentTarget.setPointerCapture?.(event.pointerId);
        dragged.current = true;
        const slot = Math.max(1, (event.currentTarget.clientWidth - AXIS - 8) / CHART_SLOTS);
        // Dragged right shows earlier bests, as Strava's.
        onMove(start.offset - dx / slot);
      }}
      onPointerUp={() => {
        drag.current = null;
      }}
      onPointerCancel={() => {
        drag.current = null;
      }}
    >
      <EChart
        option={option}
        theme={theme}
        height={240}
        onReady={onReady}
        summary={`${bestName(bestKey)}: ${label(record)}, ${longDate(chrono.find((b) => b.value === record)?.day ?? chrono[0]?.day ?? '')}`}
      />
      {left && last && (
        <p className="note pb-span">
          {longDate(left.day)} – {longDate(last.day)}
        </p>
      )}
    </div>
  );
}
