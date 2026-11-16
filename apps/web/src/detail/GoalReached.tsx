/**
 * The goals an activity completed (OS-1008, as the app's #274 and #275): the one that took its period's
 * total over the target. A capsule on its feed card, and a banner above the Coach on its page that
 * opens the goals.
 */
import type { ReachedGoal } from '@zankup/core';
import { Link } from 'react-router';

import { t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { useFormat } from '../progress/more/data';
import { goalLine } from './activityContext';
import './activity.css';

/** On the feed's card: a capsule with a trophy and the goal, so it stands out. */
export function GoalReachedCapsule({ goals }: { goals: ReachedGoal[] | undefined }) {
  const format = useFormat();
  if (!goals?.length) return null;
  const lines = goals.map((g) => goalLine(g, format)).join(' · ');
  return (
    <p className="goalcapsule" title={`${t('goal.reached')} · ${lines}`}>
      <MaterialIcon name="trophy" />
      <span>
        <b>{t('goal.reached')}</b> · {lines}
      </span>
    </p>
  );
}

/** On the activity's page: the congratulation, each goal under it; it opens the goals. */
export function GoalReachedBanner({ goals }: { goals: ReachedGoal[] | undefined }) {
  const format = useFormat();
  if (!goals?.length) return null;
  return (
    <Link to="/goals" className="panel goalbanner">
      <MaterialIcon name="trophy" />
      <span>
        <b>{t('goal.reachedTitle', { count: goals.length })}</b>
        {goals.map((g) => (
          <small key={g.goal_id}>{goalLine(g, format)}</small>
        ))}
      </span>
      <MaterialIcon name="chevron_right" />
    </Link>
  );
}
