import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { WORKOUT_TYPES } from '@zankup/core';
import { describe, expect, it } from 'vitest';

describe('workout types', () => {
  it('are the ones the API accepts', () => {
    const api = readFileSync(join(__dirname, '../../../backend/src/zankup/api/library.py'), 'utf8');
    const literal = /WorkoutType = Literal\[([^\]]+)\]/.exec(api)?.[1] ?? '';
    expect([...literal.matchAll(/"(\w+)"/g)].map((m) => m[1])).toEqual([...WORKOUT_TYPES]);
  });
});
