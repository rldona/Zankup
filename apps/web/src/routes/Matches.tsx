/**
 * The efforts on the same route as an activity (OS-1006, as the app's "Matched activities"): a line of
 * their pace or speed over time, the one opened marked, and the list by year (date, pace or speed,
 * time and relative effort), each opening its activity. Hovering a point reads it; a click opens it.
 */
import { activityQuery, commonTokens, themes, usesPace } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import type { EChartsType } from 'echarts/core';
import { useCallback, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { api } from '../api';
import { EChart } from '../charts/EChart';
import { clock, speedText, useActivityContext } from '../detail/activityContext';
import { locale, t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { usePrefs } from '../prefs';
import { ChartSkeleton, Pulse } from '../Skeleton';
import { asSport } from '../progress/more/data';
import { useResolvedTheme } from '../theme';
import { ActivityCrumb } from '../detail/ActivityCrumb';
import '../detail/activity.css';

const longDay = (day: string, year = false) =>
  new Date(`${day}T12:00:00`).toLocaleDateString(locale, {
    day: 'numeric',
    month: 'long',
    ...(year ? { year: 'numeric' } : {}),
  });

export function Matches() {
  const id = Number(useParams().id);
  const valid = Number.isInteger(id) && id > 0;
  const activity = useQuery({ ...activityQuery(api, id), enabled: valid }).data;
  const context = useActivityContext(id, valid);
  const imperial = usePrefs().units === 'imperial';
  const theme = useResolvedTheme();
  const navigate = useNavigate();
  const pace = activity ? usesPace(asSport(activity.sport)) : false;
  const matches = useMemo(() => context?.matches ?? [], [context]);
  const years = [...new Set(matches.map((m) => m.day.slice(0, 4)))].sort().reverse();

  const option = useMemo(() => {
    const c = { ...commonTokens, ...themes[theme] };
    return {
      grid: { left: 64, right: 20, top: 16, bottom: 28 },
      tooltip: {
        trigger: 'item',
        confine: true,
        backgroundColor: c.surface2,
        borderColor: c.line,
        textStyle: { color: c.fg },
        formatter: (p: { dataIndex: number }) => {
          const m = matches[p.dataIndex];
          return m
            ? `<b>${speedText(m.speed_mps, pace, imperial)}</b><br/><span style="opacity:.7">${longDay(m.day, true)} · ${clock(m.moving_s)}</span>`
            : '';
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: matches.map((m) => m.day),
        axisLabel: {
          hideOverlap: true,
          formatter: (day: string) =>
            new Date(`${day}T12:00:00`).toLocaleDateString(locale, { month: 'short', year: '2-digit' }),
        },
      },
      // Faster is higher, for a pace too.
      yAxis: {
        type: 'value',
        scale: true,
        splitNumber: 3,
        axisLabel: { formatter: (v: number) => speedText(v, pace, imperial).split(' ')[0] },
        splitLine: { lineStyle: { color: c.line } },
      },
      series: [
        {
          id: 'matches',
          type: 'line',
          smooth: 0.3,
          data: matches.map((m) => ({
            value: m.speed_mps ?? null,
            symbolSize: m.this ? 14 : 8,
            itemStyle: { color: m.this ? c.accent : c.muted },
          })),
          lineStyle: { color: c.fitness, width: 2.5 },
          symbol: 'circle',
          showAllSymbol: true,
        },
      ],
    };
  }, [matches, theme, pace, imperial]);

  const ready = useCallback(
    (chart: EChartsType) => {
      const open = (p: { dataIndex: number }) => {
        const m = matches[p.dataIndex];
        if (m && !m.this) void navigate(`/activities/${m.activity_id}`);
      };
      chart.on('click', open as never);
      return () => chart.off('click', open as never);
    },
    [matches, navigate],
  );

  const title = t(pace ? 'detail.matchingRuns' : 'detail.matching');
  return (
    <>
      <ActivityCrumb id={id} activity={activity} />
      <section className="page matchespage">
        <h1>{title}</h1>
        {!context ? (
          <Pulse>
            <ChartSkeleton height={240} />
          </Pulse>
        ) : (
          <section className="panel">
            <EChart
              option={option}
              theme={theme}
              height={240}
              onReady={ready}
              summary={`${title}: ${matches.map((m) => `${longDay(m.day, true)} ${speedText(m.speed_mps, pace, imperial)}`).join(', ')}`}
            />
          </section>
        )}
        {years.map((year) => {
          const rows = matches.filter((m) => m.day.startsWith(year)).reverse();
          return (
            <section key={year} className="panel matchyear">
              <h2>
                {year}
                <span className="note num">{rows.length}</span>
              </h2>
              <ul>
                {rows.map((m) => {
                  const line = (
                    <>
                      <span>{longDay(m.day)}</span>
                      <span className="note num">
                        {speedText(m.speed_mps, pace, imperial)} · {clock(m.moving_s)}
                        {m.relative_effort ? ` · ${Math.round(m.relative_effort)} ${t('detail.er')}` : ''}
                      </span>
                    </>
                  );
                  return (
                    <li key={m.activity_id} className={m.this ? 'on' : undefined}>
                      {m.this ? (
                        <div className="matchrow" aria-current="page">
                          {line}
                        </div>
                      ) : (
                        <Link to={`/activities/${m.activity_id}`} className="matchrow">
                          {line}
                          <MaterialIcon name="chevron_right" />
                        </Link>
                      )}
                    </li>
                  );
                })}
              </ul>
            </section>
          );
        })}
      </section>
    </>
  );
}
