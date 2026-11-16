/**
 * A strength workout's type (OS-1015, as the app's): Rowing, Hyrox, GRIT…, chosen when the watch
 * recorded no exercises, so the body map always has muscles (`WORKOUT_MUSCLES` in core). In the latest
 * activity's panel and in Edit, for strength only; the type chosen last comes first, as it is usually
 * the same.
 */
import { isWorkoutType, WORKOUT_TYPES, type WorkoutType } from '@zankup/core';

import { t } from '../i18n';

const LAST_KEY = 'zankup.workoutType';

export const workoutTypeName = (type: WorkoutType) => t(`workoutType.${type}`);

/** " · Hyrox" after the date, unless the activity is already named after its type. */
export function workoutTypeSuffix(a: { name?: string | null; workout_type?: string | null }): string {
  if (!isWorkoutType(a.workout_type)) return '';
  const name = workoutTypeName(a.workout_type);
  return a.name === name ? '' : ` · ${name}`;
}

/**
 * The name to show once a type is chosen: the type's, while the activity keeps the name it came with
 * (not named by the person, here or before); otherwise the one typed.
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

export function ordered(): WorkoutType[] {
  let last: string | null = null;
  try {
    last = localStorage.getItem(LAST_KEY);
  } catch {
    // No storage (a private window): the usual order.
  }
  return [...WORKOUT_TYPES].sort((a, b) => Number(b === last) - Number(a === last));
}

export function rememberWorkoutType(type: WorkoutType | null) {
  if (!type) return;
  try {
    localStorage.setItem(LAST_KEY, type);
  } catch {
    // Only a convenience.
  }
}
