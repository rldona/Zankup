import type { ActivityDetail } from '@zankup/core';
import { Link } from 'react-router';

import { t } from '../i18n';
import { MaterialIcon } from '../MaterialIcon';
import { asSport } from '../progress/more/data';

/** The way back from an activity's own pages (matched activities, the analysis) to the activity. */
export function ActivityCrumb({ id, activity }: { id: number; activity: ActivityDetail | undefined }) {
  const name = activity ? (activity.name ?? t(`sport.${asSport(activity.sport)}`)) : t('common.back');
  return (
    <div className="crumbrow show-always">
      <Link to={`/activities/${id}`} className="crumb">
        <MaterialIcon name="chevron_left" />
        {name}
      </Link>
    </div>
  );
}
