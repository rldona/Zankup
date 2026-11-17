/**
 * Pieces shared by Fitness, Relative effort and race predictions (OS-1003): the card that opens a page,
 * its little line, the change badge and a race's distance badge.
 */
import { RACES, type MaterialIcon as MaterialIconName, type RaceKind } from '@zankup/core';
import { useMemo, type ReactNode } from 'react';
import { Link } from 'react-router';

import { EChart } from '../../charts/EChart';
import { MaterialIcon } from '../../MaterialIcon';
import { useResolvedTheme } from '../../theme';
import { CardTitle } from '../../ui';
import { palette, scallop } from './data';

/** A Progress card that opens its page: the icon, the title, what it covers and ›, then the content. */
export function ProgressLink({
  to,
  icon,
  title,
  when,
  label,
  children,
}: {
  to: string;
  icon: MaterialIconName;
  title: string;
  /** What the card covers, on the right: "Last month", "This week". */
  when: string;
  /** The card's name for screen readers: its title and its figure. */
  label: string;
  children: ReactNode;
}) {
  return (
    <Link to={to} className="panel fxcard tap" aria-label={label}>
      <CardTitle
        right={
          <span className="fxwhen">
            {when}
            <MaterialIcon name="chevron_right" />
          </span>
        }
      >
        <MaterialIcon name={icon} />
        {title}
      </CardTitle>
      {children}
    </Link>
  );
}

/** A change on a soft badge: ▲ or ▼, green when it is good news. */
export function ChangePill({ up, good, children }: { up: boolean; good: boolean; children: ReactNode }) {
  return (
    <span className="fxpill num">
      <i className={good ? 'good' : undefined} aria-hidden="true">
        {up ? '▲' : '▼'}
      </i>
      {children}
    </span>
  );
}

/**
 * A card's line (ECharts, as every chart with data): where it started, written before it, and today,
 * marked. `inverse` puts the lower values on top (a faster time); `level` draws today's level across.
 */
export function CardLine({
  values,
  label,
  first,
  inverse = false,
  area = false,
  level = false,
}: {
  values: number[];
  label: string;
  /** The first value as written before the line. */
  first: string;
  inverse?: boolean;
  area?: boolean;
  level?: boolean;
}) {
  const theme = useResolvedTheme();
  const option = useMemo(() => {
    const c = palette(theme);
    const last = values.length - 1;
    return {
      grid: { left: first.length * 8 + 14, right: 14, top: 14, bottom: 14 },
      xAxis: { type: 'category', show: false, boundaryGap: false, data: values.map((_, i) => i) },
      yAxis: { type: 'value', show: false, scale: true, inverse },
      series: [
        {
          id: 'line',
          type: 'line',
          data: values,
          symbol: 'none',
          silent: true,
          lineStyle: { color: c.accent, width: 2.5 },
          areaStyle: area ? { color: c.accent, opacity: 0.15 } : undefined,
          markLine: level
            ? {
                silent: true,
                symbol: 'none',
                label: { show: false },
                lineStyle: { color: c.muted, type: 'solid', width: 1, opacity: 0.6 },
                data: [{ yAxis: values[last] }],
              }
            : undefined,
        },
        {
          id: 'halo',
          type: 'scatter',
          silent: true,
          data: [[last, values[last]]],
          symbolSize: 22,
          itemStyle: { color: c.accent, opacity: 0.25 },
        },
        {
          id: 'ends',
          type: 'scatter',
          silent: true,
          data: [
            {
              value: [0, values[0]],
              symbolSize: 10,
              itemStyle: { color: c.surface, borderColor: c.accent, borderWidth: 2.5 },
              label: {
                show: true,
                position: 'left',
                distance: 8,
                formatter: first,
                color: c.muted,
                fontSize: 12,
              },
            },
            { value: [last, values[last]], symbolSize: 11, itemStyle: { color: c.accent } },
          ],
        },
      ],
    };
  }, [values, theme, first, inverse, area, level]);
  return <EChart option={option} theme={theme} height={84} summary={label} className="fxspark" />;
}

/** A race's distance on a scalloped badge in the accent, as Strava's: 5 · 10 · 21,1 · 42,2 KM. */
export function RaceBadge({ race, size = 64 }: { race: RaceKind; size?: number }) {
  const badge = RACES.find((x) => x.kind === race)?.badge ?? '';
  return (
    <span className="fxbadge" style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size}>
        <path d={scallop(size)} />
      </svg>
      <b style={{ fontSize: size * (badge.length > 2 ? 0.26 : 0.32) }}>{badge}</b>
      <small style={{ fontSize: size * 0.14 }}>KM</small>
    </span>
  );
}
