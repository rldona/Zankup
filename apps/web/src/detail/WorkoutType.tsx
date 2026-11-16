/** The workout type's chips (OS-1015), in the latest activity's panel and in Edit. */
import { type WorkoutType } from '@zankup/core';
import { useState } from 'react';

import { t } from '../i18n';
import { ordered, workoutTypeName } from './workoutTypes';

/** The type's chips; a click on the chosen one clears it. */
export function WorkoutTypePicker({
  value,
  onChange,
}: {
  value: WorkoutType | null;
  onChange: (type: WorkoutType | null) => void;
}) {
  const [order] = useState(ordered);
  const label = t('workoutType.title');
  return (
    <div className="field">
      <b>{label}</b>
      <div className="chips" role="radiogroup" aria-label={label}>
        {order.map((type) => (
          <button
            key={type}
            type="button"
            className="chip"
            role="radio"
            aria-checked={value === type}
            onClick={() => onChange(value === type ? null : type)}
          >
            {workoutTypeName(type)}
          </button>
        ))}
      </div>
    </div>
  );
}
