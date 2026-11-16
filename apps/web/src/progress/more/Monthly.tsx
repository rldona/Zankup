/**
 * Monthly activities (OS-1005), as the app's and Strava's: this month's moving time adding up day by
 * day over last month's, on Progress's card and, for all sports and each one, on its own page. Hovering
 * a day reads both months' totals up to it (the phone's tap).
 */
import { formatDate, hoursMinutes, months, monthSports, type Month, type MonthFigures } from '@zankup/core';
import { useMemo } from 'react';
import { Link } from 'react-router';

import { EChart } from '../../charts/EChart';
import { locale, t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, ChartSkeleton, Pulse } from '../../Skeleton';
import { useResolvedTheme } from '../../theme';
import { CardTitle } from '../../ui';
import { asSport, monthName, palette, useFormat, useMonths } from './data';
import '../more.css';

/**
 * The two lines: last month in grey and this month in the accent up to today, ending in a dot. With
 * `label` (the page), the days along the bottom and a dashed line at last month's total, with its name
 * and time.
 */
function MonthChart({
  current,
  previous,
  height,
  label = false,
}: {
  current: Month;
  previous: Month;
  height: number;
  label?: boolean;
}) {
  const theme = useResolvedTheme();
  const option = useMemo(() => {
    const c = palette(theme);
    const slots = Math.max(current.days, previous.days);
    const last = current.cumulative.length - 1;
    const width = label ? 3 : 2.5;
    const day = (m: Month, i: number) =>
      formatDate(new Date(m.start.getFullYear(), m.start.getMonth(), i + 1), locale, {
        day: 'numeric',
        month: 'short',
      });
    const reading = (m: Month, i: number) =>
      i < m.cumulative.length && i < m.days
        ? `${day(m, i)}: <b>${hoursMinutes(m.cumulative[i] ?? 0)}</b>`
        : '';
    return {
      grid: label
        ? { left: 12, right: 16, top: 34, bottom: 26 }
        : { left: 6, right: 14, top: 14, bottom: 10 },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { type: 'line', lineStyle: { color: c.line } },
        formatter: (params: { dataIndex: number }[]) => {
          const i = params[0]?.dataIndex ?? 0;
          return [reading(current, i), reading(previous, i)].filter(Boolean).join('<br>');
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: Array.from({ length: slots }, (_, i) => String(i + 1)),
        show: label,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: c.line } },
        axisLabel: { color: c.muted, hideOverlap: true },
      },
      yAxis: {
        type: 'value',
        show: false,
        min: 0,
        max: Math.max(previous.totals.time, current.totals.time, 1),
      },
      series: [
        {
          name: monthName(previous),
          type: 'line',
          symbol: 'none',
          data: previous.cumulative,
          lineStyle: { color: c.muted, width },
          itemStyle: { color: c.muted },
          ...(label && {
            markLine: {
              silent: true,
              symbol: 'none',
              lineStyle: { color: c.muted, type: 'dashed', width: 1.5 },
              label: {
                position: 'insideStartTop',
                color: c.fg,
                fontWeight: 700,
                formatter: `${monthName(previous)} · ${hoursMinutes(previous.totals.time)}`,
              },
              data: [{ yAxis: previous.totals.time }],
            },
          }),
        },
        {
          name: monthName(current),
          type: 'line',
          symbol: 'none',
          data: current.cumulative,
          lineStyle: { color: c.accent, width, join: 'round' },
          itemStyle: { color: c.accent },
          z: 3,
        },
        // Today: a dot with a soft halo, at the end of this month's line.
        ...(last >= 0
          ? [
              {
                type: 'scatter',
                silent: true,
                tooltip: { show: false },
                data: [[last, current.cumulative[last]]],
                symbolSize: label ? 26 : 32,
                itemStyle: { color: c.accent, opacity: 0.25 },
                z: 4,
              },
              {
                type: 'scatter',
                silent: true,
                tooltip: { show: false },
                data: [[last, current.cumulative[last]]],
                symbolSize: label ? 12 : 14,
                itemStyle: { color: c.accent },
                z: 5,
              },
            ]
          : []),
      ],
    };
  }, [current, previous, label, theme]);
  return (
    <EChart
      option={option}
      theme={theme}
      height={height}
      className="monthchart"
      summary={`${monthName(current)}: ${hoursMinutes(current.totals.time)} · ${monthName(previous)}: ${hoursMinutes(previous.totals.time)}`}
    />
  );
}

/** Progress's card: this month's time, last month's, and the two lines small. */
export function MonthlyCard() {
  const { calendar, today, pending } = useMonths();
  if (pending || !calendar)
    return (
      <Pulse>
        <CardSkeleton lines={3} />
      </Pulse>
    );
  const { current, previous } = months(calendar, today);
  return (
    <Link to="/progress/monthly" className="panel morecard monthlycard">
      <CardTitle
        right={
          <span className="morehint">
            {t('monthly.thisMonth')}
            <MaterialIcon name="chevron_right" />
          </span>
        }
      >
        <MaterialIcon name="calendar_month" />
        {t('monthly.title')}
      </CardTitle>
      <div className="monthlybody">
        <div className="monthlyfigures">
          <small>{t('monthly.time')}</small>
          <b className="num">{hoursMinutes(current.totals.time)}</b>
          <small>{t('monthly.lastMonth', { value: hoursMinutes(previous.totals.time) })}</small>
        </div>
        <MonthChart current={current} previous={previous} height={90} />
      </div>
    </Link>
  );
}

/** A month's figures; a sport with nothing this month shows only its count, as Strava's. */
function Figures({ totals, count }: { totals: MonthFigures; count: boolean }) {
  const format = useFormat();
  const figures: [string, string][] = totals.count
    ? [
        [t('monthly.time'), hoursMinutes(totals.time)],
        ...(totals.distance > 0
          ? ([
              [t('monthly.distance'), format.distance(totals.distance)],
              [t('monthly.ascent'), format.elevation(totals.ascent)],
            ] as [string, string][])
          : []),
        ...(count ? ([[t('monthly.count'), String(totals.count)]] as [string, string][]) : []),
      ]
    : [[t('monthly.count'), '0']];
  return (
    <dl className="monthfigures">
      {figures.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd className="num">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** This month against the last for all sports, then for each sport done in either. */
export function MonthlyPage() {
  const { calendar, today } = useMonths();
  const all = calendar ? months(calendar, today) : null;
  const sections = calendar
    ? monthSports(calendar).map((sport) => ({ sport, ...months(calendar, today, sport) }))
    : [];
  return (
    <section className="page monthly">
      <div className="titlebar">
        <h1>{t('monthly.title')}</h1>
      </div>
      {!all ? (
        <Pulse>
          <ChartSkeleton height={260} />
        </Pulse>
      ) : (
        <>
          <article className="panel monthpanel">
            <h2>{t('monthly.activities', { count: all.current.totals.count })}</h2>
            <Figures totals={all.current.totals} count={false} />
            <MonthChart {...all} height={260} label />
          </article>
          <div className="monthsports">
            {sections.map((s) => (
              <article key={s.sport} className="panel monthpanel">
                <h2>{t(`sport.${asSport(s.sport)}`)}</h2>
                <Figures totals={s.current.totals} count />
                <MonthChart current={s.current} previous={s.previous} height={220} label />
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
