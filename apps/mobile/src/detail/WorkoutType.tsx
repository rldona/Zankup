/**
 * A strength workout's kind (rowing, Hyrox, GRIT…), chosen when the watch recorded no exercises, so the
 * body map always has muscles (`WORKOUT_MUSCLES` in core). In the latest activity's sheet and in Edit,
 * for strength only; the kind chosen last comes first, as it is usually the same.
 */
import { isWorkoutType, WORKOUT_TYPES, type WorkoutType } from '@zankup/core';
import { useState } from 'react';
import { Text } from 'react-native';

import { t } from '../i18n';
import { keys, readSync, storage } from '../storage';
import { useTheme } from '../theme';
import { Choice } from '../ui';

export const workoutTypeName = (type: WorkoutType) => t(`workoutType.${type}`);

/** " · Hyrox" after the date, unless the activity is already named after its kind. */
export function workoutTypeSuffix(a: { name?: string | null; workout_type?: string | null }): string {
  if (!isWorkoutType(a.workout_type)) return '';
  const name = workoutTypeName(a.workout_type);
  return a.name === name ? '' : ` · ${name}`;
}

/** The kinds, the one chosen last first. */
function ordered(): WorkoutType[] {
  const last = readSync(keys.workoutType);
  return [...WORKOUT_TYPES].sort((a, b) => Number(b === last) - Number(a === last));
}

export function rememberWorkoutType(type: WorkoutType | null) {
  if (type) void storage.setItem(keys.workoutType, type);
}

/**
 * The name to show once a kind is chosen: the kind's, while the activity keeps the name it came with
 * (the person has not named it, here or before); otherwise the one typed.
 */
export function nameWithType(
  type: WorkoutType | null,
  name: string,
  original: string,
  editedFields: readonly string[],
): string {
  const ownName = editedFields.includes('name') || name.trim() !== original.trim();
  return type && !ownName ? workoutTypeName(type) : name;
}

/** The kind's chips; a tap on the chosen one clears it. */
export function WorkoutTypePicker({
  value,
  onChange,
}: {
  value: WorkoutType | null;
  onChange: (type: WorkoutType | null) => void;
}) {
  const c = useTheme();
  const [order] = useState(ordered);
  return (
    <>
      <Text style={{ color: c.fg, fontWeight: '600' }}>{t('workoutType.title')}</Text>
      <Choice<WorkoutType | ''>
        label={t('workoutType.title')}
        value={value ?? ''}
        onChange={(v) => onChange(v === '' || v === value ? null : v)}
        options={order.map((type) => ({ value: type, label: workoutTypeName(type) }))}
      />
    </>
  );
}
