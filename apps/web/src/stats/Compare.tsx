/**
 * How Zankup's metrics compare with what Strava computed, for people who imported an export (OS-323).
 * On Settings → Import, under the imports, as the app's (#318): it is about an imported export, not
 * about Stats' period.
 */
import { stravaComparisonQuery } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { api } from '../api';
import { locale, t } from '../i18n';
import { useResolvedTheme } from '../theme';
import { InfoTip } from '../ui';
import { ComparisonChart } from './charts';

const COMPARED = ['effort', 'grade_adjusted_distance', 'calories'] as const;

export function CompareWithStrava() {
  const theme = useResolvedTheme();
  const comparison = useQuery(stravaComparisonQuery(api));
  const [metric, setMetric] = useState<(typeof COMPARED)[number]>('effort');
  const data = comparison.data;
  if (!data || data.metrics.every((m) => m.count < 3)) return null;
  const current = data.metrics.find((m) => m.metric === metric);
  const number = (v: number | null | undefined, digits = 2) =>
    v == null ? '–' : v.toLocaleString(locale, { maximumFractionDigits: digits });
  return (
    <section className="panel compare-strava">
      <div className="ph">
        <h2>{t('compare.title')}</h2>
        <div className="seg" role="group" aria-label={t('compare.metric')}>
          {COMPARED.map((m) => (
            <button key={m} type="button" aria-pressed={m === metric} onClick={() => setMetric(m)}>
              {t(`compare.${m}`)}
            </button>
          ))}
        </div>
        <InfoTip text={t('compare.help')} />
      </div>
      {current && current.count >= 3 ? (
        <>
          <dl className="kpis num">
            <div className="kpi">
              <dd>{current.count}</dd>
              <dt>{t('compare.count')}</dt>
            </div>
            <div className="kpi">
              <dd>{number(current.r2, 3)}</dd>
              <dt>R²</dt>
            </div>
            <div className="kpi">
              <dd>{number(current.mae, 1)}</dd>
              <dt>{t('compare.mae')}</dt>
            </div>
            <div className="kpi">
              <dd>{number(current.ratio, 3)}</dd>
              <dt>{t('compare.ratio')}</dt>
            </div>
          </dl>
          <ComparisonChart
            points={current.points}
            theme={theme}
            label={t('compare.chart', { metric: t(`compare.${metric}`) })}
          />
          <details>
            <summary>{t('compare.largest')}</summary>
            <ul className="rows">
              {current.largest.map((p) => (
                <li key={p.activity_id}>
                  <Link to={`/activities/${p.activity_id}`}>
                    {t('compare.activity', { id: p.activity_id })}
                  </Link>
                  <span className="num">
                    Zankup {number(p.ours, 0)} · Strava {number(p.strava, 0)}
                  </span>
                </li>
              ))}
            </ul>
          </details>
        </>
      ) : (
        <p className="state">{t('compare.notEnough')}</p>
      )}
      {metric === 'effort' && data.effort_scale && (
        <details>
          <summary>{t('compare.scale')}</summary>
          <p className="note">
            {t('compare.scaleHelp', { r2: number(data.effort_scale.r2, 3), count: data.effort_scale.count })}
          </p>
          <p className="num">
            {data.effort_scale.coefficients.map((c, i) => `Z${i + 1}: ${number(c, 3)}`).join(' · ')}
          </p>
        </details>
      )}
    </section>
  );
}
