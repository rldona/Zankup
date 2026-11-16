import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import en from '../i18n/en.json';
import es from '../i18n/es.json';
import { FRONT, BACK, musclesFor, WORKOUT_MUSCLES, WORKOUT_TYPES, type Muscle } from './strength';

describe('workout types', () => {
  it('are the ones the API accepts, each named in both languages', () => {
    const api = readFileSync(
      new URL('../../../../backend/src/zankup/api/library.py', import.meta.url),
      'utf8',
    );
    const literal = /WorkoutType = Literal\[([^\]]+)\]/.exec(api)?.[1] ?? '';
    expect([...literal.matchAll(/"(\w+)"/g)].map((m) => m[1])).toEqual([...WORKOUT_TYPES]);
    for (const type of WORKOUT_TYPES) {
      expect((en as Record<string, string>)[`workoutType.${type}`]).toBeTruthy();
      expect((es as Record<string, string>)[`workoutType.${type}`]).toBeTruthy();
    }
  });

  it('each works muscles the body map draws', () => {
    const drawn = new Set<Muscle>([...FRONT, ...BACK]);
    for (const type of WORKOUT_TYPES) {
      const muscles = Object.keys(WORKOUT_MUSCLES[type]) as Muscle[];
      expect(muscles.length).toBeGreaterThan(2);
      for (const m of muscles) expect(drawn.has(m)).toBe(true);
    }
  });

  it("draws the watch's exercises first, else the type's muscles", () => {
    const recorded = new Map<Muscle, number>([['biceps', 2]]);
    expect(musclesFor(recorded, 'lower_body')).toBe(recorded);
    expect([...musclesFor(new Map(), 'lower_body').keys()]).toEqual([
      'quads',
      'glutes',
      'hamstrings',
      'calves',
      'lower_back',
    ]);
    expect(musclesFor(new Map(), 'yoga').size).toBe(0);
    expect(musclesFor(new Map(), null).size).toBe(0);
  });
});
