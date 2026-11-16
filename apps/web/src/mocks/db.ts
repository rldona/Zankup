/**
 * The mock API's data: a demo athlete's last three years, made up from a fixed seed so every start
 * and every test sees the same thing, and dated from today so this week always has days to come.
 * Nothing here is anyone's real data (the repository is public).
 *
 * The activities are the base every domain builds on (feed, detail, stats, Progress, goals…); each
 * domain keeps any state of its own in `slice`, made the first time it is asked for and dropped by
 * `resetDb` (after every test, and on reload in the browser).
 */
import { isoDay, type ApiPaths } from '@zankup/core';

import { seeded, type Random } from './random';
import type { Res } from './route';

export const SEED = 20261116;

type Summary = Res<'/api/activities', 'get'>['items'][number];
export type Me = NonNullable<Res<'/api/auth/me', 'get'>>;

/** One activity as the mocks know it: the feed's summary plus what the detail and streams derive from. */
export type MockActivity = Summary & {
  description: string | null;
  calories: number;
  max_hr: number | null;
  device: string;
  gear_id: number | null;
  edited_fields: string[];
  /** The route's points [lat, lon], for the detail's streams and map; empty without GPS. */
  points: [number, number][];
};

export type Sport = 'run' | 'ride' | 'strength' | 'walk';

const HOME: [number, number] = [40.4168, -3.7038]; // a made-up start in central Madrid

function encode(points: [number, number][]): string {
  let lastLat = 0;
  let lastLon = 0;
  let out = '';
  const push = (v: number) => {
    let n = v < 0 ? ~(v << 1) : v << 1;
    while (n >= 0x20) {
      out += String.fromCharCode((0x20 | (n & 0x1f)) + 63);
      n >>= 5;
    }
    out += String.fromCharCode(n + 63);
  };
  for (const [lat, lon] of points) {
    const a = Math.round(lat * 1e5);
    const b = Math.round(lon * 1e5);
    push(a - lastLat);
    push(b - lastLon);
    lastLat = a;
    lastLon = b;
  }
  return out;
}

/** A loop of about `km` from home: a wobbly ellipse in a direction of its own. */
function loop(rng: Random, km: number): [number, number][] {
  const n = Math.max(24, Math.round(km * 8));
  const heading = rng.between(0, Math.PI * 2);
  const r = km / (2 * Math.PI) / 111; // degrees for a loop of that length
  const stretch = rng.between(1.2, 2.2);
  const points: [number, number][] = [];
  for (let i = 0; i <= n; i++) {
    const t = (i / n) * Math.PI * 2;
    const wobble = 1 + 0.12 * Math.sin(t * rng.int(3, 6));
    const x = Math.cos(t) * r * stretch * wobble - r * stretch;
    const y = Math.sin(t) * r * wobble;
    const lat = HOME[0] + x * Math.cos(heading) - y * Math.sin(heading);
    const lon =
      HOME[1] + (x * Math.sin(heading) + y * Math.cos(heading)) / Math.cos((HOME[0] * Math.PI) / 180);
    points.push([lat, lon]);
  }
  return points;
}

const NAMES: Record<Sport, string[]> = {
  run: ['Morning run', 'Easy run', 'Tempo run', 'Intervals', 'Long run', 'Recovery run'],
  ride: ['Morning ride', 'Hill ride', 'Endurance ride', 'Coffee ride', 'Sweet spot'],
  strength: ['Evening strength', 'Gym session', 'Strength training'],
  walk: ['Afternoon walk', 'Evening walk', 'Hike'],
};
const WORKOUT_TYPES = ['hyrox', 'grit', 'bodypump', 'functional', 'upper_body', 'lower_body', 'core'];
const EXERCISES = ['squat', 'deadlift', 'bench_press', 'row', 'pull_up', 'lunge', 'plank', 'shoulder_press'];

function makeActivities(rng: Random, today: Date): MockActivity[] {
  const out: MockActivity[] = [];
  let id = 1;
  const start = new Date(today.getFullYear() - 3, today.getMonth(), today.getDate());
  // A weekly plan: two runs, a long ride at the weekend, two strength sessions, a walk now and then.
  const plan: Record<number, Sport[]> = {
    1: ['run'],
    2: ['strength'],
    3: ['run'],
    4: ['strength'],
    6: ['ride'],
    0: ['run', 'walk'],
  };
  for (let d = new Date(start); d <= today; d.setDate(d.getDate() + 1)) {
    const fitness = 0.85 + 0.15 * ((d.getTime() - start.getTime()) / (today.getTime() - start.getTime())); // gets fitter
    for (const sport of plan[d.getDay()] ?? []) {
      if (!rng.chance(sport === 'walk' ? 0.35 : 0.85)) continue;
      const hour = sport === 'strength' ? rng.int(18, 20) : rng.int(7, 10);
      const when = new Date(d.getFullYear(), d.getMonth(), d.getDate(), hour, rng.int(0, 59));
      if (when > new Date()) continue;
      const a = make(rng, id++, sport, when, fitness);
      out.push(a);
    }
  }
  return out.sort((x, y) => y.start_utc.localeCompare(x.start_utc));
}

function make(rng: Random, id: number, sport: Sport, when: Date, fitness: number): MockActivity {
  const long = sport === 'run' && when.getDay() === 0;
  const km =
    sport === 'run'
      ? long
        ? rng.between(14, 21)
        : rng.between(5, 11)
      : sport === 'ride'
        ? rng.between(40, 95)
        : sport === 'walk'
          ? rng.between(3, 8)
          : 0;
  const speed = // m/s
    sport === 'run'
      ? rng.between(2.7, 3.2) * fitness
      : sport === 'ride'
        ? rng.between(7, 8.6) * fitness
        : 1.4;
  const moving = sport === 'strength' ? rng.between(2400, 3600) : (km * 1000) / speed;
  const points = km ? loop(rng, km) : [];
  const ascent = km ? Math.round(km * (sport === 'ride' ? rng.between(8, 18) : rng.between(3, 9))) : null;
  const avgHr = Math.round(
    sport === 'strength'
      ? rng.between(110, 130)
      : sport === 'walk'
        ? rng.between(95, 110)
        : rng.between(138, 156),
  );
  const strengthKind = sport === 'strength' ? (rng.chance(0.5) ? rng.pick(WORKOUT_TYPES) : null) : null;
  const exercises =
    sport === 'strength' && !strengthKind
      ? Object.fromEntries(Array.from({ length: rng.int(3, 5) }, () => [rng.pick(EXERCISES), rng.int(3, 4)]))
      : null;
  return {
    id,
    name: rng.pick(NAMES[sport]),
    sport,
    sub_sport: sport === 'strength' ? 'strength_training' : null,
    workout_type: strengthKind,
    source: 'garmin',
    start_utc: when.toISOString(),
    timezone: '+01:00',
    duration_s: Math.round(moving * rng.between(1.02, 1.12)),
    moving_s: Math.round(moving),
    distance_m: km ? Math.round(km * 1000) : null,
    ascent_m: ascent,
    avg_hr: avgHr,
    avg_power: sport === 'ride' ? Math.round(rng.between(170, 215) * fitness) : null,
    has_gps: points.length > 0,
    route: points.length ? encode(points) : null,
    photo: null,
    photos: 0,
    exercises,
    goals_reached: [],
    description: rng.chance(0.2) ? 'Felt good today.' : null,
    calories: Math.round((moving / 60) * (sport === 'ride' ? 11 : sport === 'run' ? 12 : 7)),
    max_hr: avgHr + rng.int(12, 28),
    device: 'Garmin fenix 7',
    gear_id: sport === 'run' ? 1 : sport === 'ride' ? 2 : null,
    edited_fields: [],
    points,
  };
}

const USER: Me = {
  id: 1,
  email: 'demo@zankup.test',
  display_name: 'Demo Athlete',
  totp_enabled: true,
  google_linked: false,
  avatar_url: null,
  location: 'Madrid',
  birth_date: '1988-04-12',
  sex: 'male',
  weight_kg: 72,
  height_cm: 178,
};

function build() {
  const rng = seeded(SEED);
  const today = new Date();
  return {
    today,
    todayIso: isoDay(today),
    rng,
    /** Signed in from the start: the mock API is for working on the screens, not on sign-in. */
    signedIn: true,
    user: { ...USER },
    activities: makeActivities(rng, today),
    slices: new Map<string, unknown>(),
  };
}

let state = build();

/** The data, as the handlers read and change it. */
export const db = {
  get today() {
    return state.today;
  },
  get todayIso() {
    return state.todayIso;
  },
  get rng() {
    return state.rng;
  },
  get user() {
    return state.user;
  },
  get signedIn() {
    return state.signedIn;
  },
  set signedIn(value: boolean) {
    state.signedIn = value;
  },
  get activities() {
    return state.activities;
  },
  activity(id: number) {
    return state.activities.find((a) => a.id === id);
  },
  /** A domain's own data, made from `make` the first time and kept until the next reset. */
  slice<T>(name: string, make: () => T): T {
    if (!state.slices.has(name)) state.slices.set(name, make());
    return state.slices.get(name) as T;
  },
};

/** Back to the data as it starts (after every test; a reload does it in the browser). */
export function resetDb() {
  state = build();
}

/** The feed's summary of an activity: the detail's extras left out. */
export function summary(a: MockActivity): Summary {
  return {
    id: a.id,
    name: a.name,
    sport: a.sport,
    sub_sport: a.sub_sport,
    workout_type: a.workout_type,
    source: a.source,
    start_utc: a.start_utc,
    timezone: a.timezone,
    duration_s: a.duration_s,
    moving_s: a.moving_s,
    distance_m: a.distance_m,
    ascent_m: a.ascent_m,
    avg_hr: a.avg_hr,
    avg_power: a.avg_power,
    has_gps: a.has_gps,
    route: a.route,
    photo: a.photo,
    photos: a.photos,
    exercises: a.exercises,
    goals_reached: a.goals_reached,
  };
}

export type { ApiPaths };
