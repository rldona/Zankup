/**
 * Year by year (OS-1010), from Stats' links as the app's (#318): each year's distance, cumulative
 * through it, one line per year. It compares whole years, so it is not under Stats' period. The chart
 * fills the page and the years are listed under it, fixed; a click on a year (or its line) makes it
 * stand out.
 */
import { formatters, progressQuery, themes, yearColours } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { api } from '../api';
import { locale, t } from '../i18n';
import { usePrefs } from '../prefs';
import { ChartSkeleton, Pulse } from '../Skeleton';
import { ProgressChart } from '../stats/charts';
import { useResolvedTheme } from '../theme';
import { InfoTip } from '../ui';
import '../stats/stats.css';

export function Years() {
  const lines = useQuery(progressQuery(api));
  const prefs = usePrefs();
  const unit = useMemo(() => formatters(prefs).unit, [prefs]);
  const theme = useResolvedTheme();
  const [selected, setSelected] = useState(() => new Date().getFullYear());
  const colours = useMemo(
    () =>
      yearColours(
        (lines.data ?? []).map((l) => l.year),
        selected,
        themes[theme],
      ),
    [lines.data, selected, theme],
  );
  const scale = unit === 'km' ? 1000 : 1609.344;
  return (
    <section className="page years">
      <div className="titlebar">
        <h1>{t('stats.progress')}</h1>
        <InfoTip text={t('stats.progressNote')} />
      </div>
      {lines.isPending ? (
        <Pulse className="panel yearspanel">
          <ChartSkeleton height={320} />
        </Pulse>
      ) : lines.data?.length ? (
        <div className="panel yearspanel">
          <ProgressChart
            lines={lines.data}
            selected={selected}
            theme={theme}
            distanceUnit={unit}
            height="100%"
            className="yearschart"
            onSelect={setSelected}
          />
          {/* In place of a legend, as the app: each year's colour and total, a button that picks it. */}
          <ul className="rows years" aria-label={t('stats.progress')}>
            {lines.data.map((l) => (
              <li key={l.year} className={l.year === selected ? 'on' : undefined}>
                <button type="button" aria-pressed={l.year === selected} onClick={() => setSelected(l.year)}>
                  <span>
                    <i style={{ background: colours.get(l.year), opacity: l.year === selected ? 1 : 0.5 }} />
                    {l.year}
                  </span>
                  <span className="num">
                    {Math.round((l.cumulative_m.at(-1) ?? 0) / scale).toLocaleString(locale)} {unit}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="note">{t('stats.empty')}</p>
      )}
    </section>
  );
}
