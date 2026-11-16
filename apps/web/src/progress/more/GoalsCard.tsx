/**
 * Goals on Progress (OS-1005), as the app's and Strava's: up to three goals as rings, each with what is
 * done so far and its target per period; the card opens the Goals page.
 */
import { goalsQuery, type GoalOut, type GoalPeriod } from '@zankup/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { api } from '../../api';
import { t } from '../../i18n';
import { MaterialIcon } from '../../MaterialIcon';
import { CardSkeleton, Pulse } from '../../Skeleton';
import { CardTitle } from '../../ui';
import { goalIcon, goalSport, goalValue, useFormat } from './data';
import '../more.css';

/**
 * A goal's ring: how far along it is, filling clockwise from the top, with its sport in the middle (also
 * on the activity's Goals block).
 */
export function GoalRing({
  goal,
  size = 56,
}: {
  goal: Pick<GoalOut, 'sport' | 'current' | 'target'>;
  size?: number;
}) {
  const stroke = size * 0.09;
  const r = size / 2 - stroke / 2 - 1;
  const length = 2 * Math.PI * r;
  const done = goal.target > 0 ? Math.min(1, goal.current / goal.target) : 0;
  return (
    <span className="goalring" style={{ width: size, height: size }} aria-hidden="true">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle className="track" cx={size / 2} cy={size / 2} r={r} strokeWidth={stroke} />
        {done > 0 && (
          <circle
            className="fill"
            cx={size / 2}
            cy={size / 2}
            r={r}
            strokeWidth={stroke}
            strokeDasharray={`${done * length} ${length}`}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
          />
        )}
      </svg>
      <MaterialIcon name={goalIcon(goal.sport)} />
    </span>
  );
}

export function GoalsCard() {
  const format = useFormat();
  const goals = useQuery(goalsQuery(api));
  if (goals.isPending)
    return (
      <Pulse>
        <CardSkeleton lines={4} />
      </Pulse>
    );
  const list = goals.data ?? [];
  return (
    <Link to="/goals" className="panel morecard goalscard">
      <CardTitle right={<MaterialIcon name="chevron_right" />}>
        <MaterialIcon name="flag" />
        {t('progress.goals')}
      </CardTitle>
      {!list.length ? (
        <p className="note">{t('progress.noGoals')}</p>
      ) : (
        <ul className="goalrings">
          {list.slice(0, 3).map((g) => (
            <li key={g.id}>
              <GoalRing goal={g} />
              <b className="num">{goalValue(g.metric, g.current, format)}</b>
              <small>
                {t(`progress.goalPer.${g.period as GoalPeriod}`, {
                  value: goalValue(g.metric, g.target, format),
                })}
                <span className="visually-hidden"> · {goalSport(g.sport)}</span>
              </small>
            </li>
          ))}
        </ul>
      )}
    </Link>
  );
}
