/**
 * What the engine would have precomputed for the demo athlete, derived from `db.activities` the way
 * the backend does (packages/metrics): each activity's load (Banister TRIMP), its
 * time in zones, its best power, speed and HR by duration and its best run efforts; then the daily
 * fitness (CTL/ATL/TSB) and the estimates (FTP, critical speed, VDOT, LTHR, max HR, race times) as
 * they stood week by week. Built once per `db` (a slice) and memoised per query.
 */
import { isoDay } from '@zankup/core';

import { db, SEED, type MockActivity } from '../db';
import { seeded } from '../random';
import type { Res } from '../route';

export type Totals = Res<'/api/stats/summary', 'get'>['current'];
export type FitnessDay = Res<'/api/stats/fitness', 'get'>[number];
export type EstimateOut = Res<'/api/stats/estimates', 'get'>[number];

// --- days, as ISO strings ------------------------------------------------------------------------

const DAY_MS = 86_400_000;
export const dayNumber = (iso: string) =>
  Date.UTC(Number(iso.slice(0, 4)), Number(iso.slice(5, 7)) - 1, Number(iso.slice(8, 10))) / DAY_MS;
export const isoOf = (n: number) => new Date(n * DAY_MS).toISOString().slice(0, 10);
export const plusDays = (iso: string, n: number) => isoOf(dayNumber(iso) + n);
export const mondayOf = (iso: string) => {
  const n = dayNumber(iso);
  return isoOf(n - ((n + 3) % 7)); // 1 Jan 1970 was a Thursday
};
export const monthOf = (iso: string) => `${iso.slice(0, 7)}-01`;
export const nextMonth = (iso: string) => {
  const y = Number(iso.slice(0, 4));
  const m = Number(iso.slice(5, 7));
  return m === 12 ? `${y + 1}-01-01` : `${y}-${String(m + 1).padStart(2, '0')}-01`;
};

export type Unit = 'day' | 'week' | 'month' | 'year';
export function bucketOf(iso: string, unit: Unit) {
  if (unit === 'week') return mondayOf(iso);
  if (unit === 'month') return monthOf(iso);
  if (unit === 'year') return `${iso.slice(0, 4)}-01-01`;
  return iso;
}
export function nextBucket(iso: string, unit: Unit) {
  if (unit === 'week') return plusDays(iso, 7);
  if (unit === 'month') return nextMonth(iso);
  if (unit === 'year') return `${Number(iso.slice(0, 4)) + 1}-01-01`;
  return plusDays(iso, 1);
}

// --- the athlete -----------------------------------------------------------------------------------

export const HR_MAX = 189;
export const HR_REST = 50;
const HR_SHARES = [0.5, 0.6, 0.7, 0.8, 0.9];
const POWER_SHARES = [0, 0.55, 0.75, 0.9, 1.05, 1.2, 1.5];
const PACE_SHARES = [0, 0.78, 0.88, 0.95, 1];

export const DURATIONS = [5, 10, 15, 30, 60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600, 5400, 7200];
/** Best power over a duration against the ride's average power. */
const POWER_SHAPE = [3.5, 3.1, 2.8, 2.2, 1.8, 1.5, 1.4, 1.3, 1.21, 1.17, 1.14, 1.11, 1.08, 1.06, 1.03, 1.01];
/** Best speed over a duration against the run's average speed. */
const SPEED_SHAPE = [1.75, 1.65, 1.55, 1.4, 1.28, 1.18, 1.14, 1.1, 1.07, 1.05, 1.04, 1.02, 1.01, 1, 1, 1];

const MILE = 1609.344;
export const RUN_DISTANCES: Record<string, number> = {
  '400m': 400,
  half_mile: MILE / 2,
  '1k': 1000,
  '1mile': MILE,
  '2mile': 2 * MILE,
  '5k': 5000,
  '10k': 10000,
  '15k': 15000,
  '10mile': 10 * MILE,
  half_marathon: 21097.5,
  marathon: 42195,
};
const RACES: Record<string, number> = { '5k': 5000, '10k': 10000, half_marathon: 21097.5, marathon: 42195 };

// --- one activity ----------------------------------------------------------------------------------

export type Computed = {
  a: MockActivity;
  day: string;
  moving: number;
  load: number;
  hrZones: number[];
  powerZones: number[] | null;
  paceZones: number[] | null;
  power: Map<number, number> | null;
  speed: Map<number, number> | null;
  hr: Map<number, number> | null;
  /** Run distance key → best time (s). */
  efforts: Map<string, number>;
  efficiency: number | null;
  decoupling: number | null;
};

const erf = (x: number) => {
  // Abramowitz and Stegun 7.1.26
  const t = 1 / (1 + 0.3275911 * Math.abs(x));
  const y =
    1 -
    ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
      t *
      Math.exp(-x * x);
  return x >= 0 ? y : -y;
};
const cdf = (x: number, mean: number, sd: number) => 0.5 * (1 + erf((x - mean) / (sd * Math.SQRT2)));

/** Seconds in each zone for values spread normally around `mean`; below a non-zero first bound counts nothing. */
function spread(total: number, mean: number, sd: number, bounds: number[]): number[] {
  return bounds.map((lower, i) => {
    const upper = bounds[i + 1] ?? Infinity;
    const from = i === 0 && lower === 0 ? 0 : cdf(lower, mean, sd);
    return Math.round(total * (cdf(upper, mean, sd) - from) * 10) / 10;
  });
}

function trimp(minutes: number, avgHr: number) {
  const x = Math.min(1, Math.max(0, (avgHr - HR_REST) / (HR_MAX - HR_REST)));
  return minutes * x * 0.64 * Math.exp(1.92 * x);
}

const hard = (a: MockActivity) => /Tempo|Intervals|Sweet spot|Hill/.test(a.name ?? '');

function compute(a: MockActivity): Computed {
  const rng = seeded(SEED + a.id * 7919);
  const moving = a.moving_s ?? a.duration_s;
  const avgHr = a.avg_hr ?? 120;
  const maxHr = a.max_hr ?? avgHr + 20;
  const within = DURATIONS.map((d, i) => [d, i] as const).filter(([d]) => d <= moving);
  let power: Map<number, number> | null = null;
  const avgPower = a.avg_power;
  if (a.sport === 'ride' && avgPower) {
    const sprint = rng.between(0.85, 1.15);
    const steady = rng.between(0.96, 1.04) * (hard(a) ? 1.04 : 1);
    power = new Map(
      within.map(([d, i]) => [d, Math.round(avgPower * (POWER_SHAPE[i] ?? 1) * (d <= 60 ? sprint : steady))]),
    );
  }
  let speed: Map<number, number> | null = null;
  const efforts = new Map<string, number>();
  const avgSpeed = a.distance_m ? a.distance_m / moving : 0;
  if ((a.sport === 'run' || a.sport === 'walk') && a.distance_m) {
    const boost = hard(a) ? 1.05 : 1;
    speed = new Map(
      within.map(([d, i]) => {
        const v = avgSpeed * (SPEED_SHAPE[i] ?? 1) * (d >= 120 && d <= 1200 ? boost : 1);
        return [d, Math.round(Math.min(6, v) * 1000) / 1000];
      }),
    );
    if (a.sport === 'run')
      for (const [key, metres] of Object.entries(RUN_DISTANCES)) {
        if (metres > a.distance_m) continue;
        const v = Math.min(
          6,
          avgSpeed * (1 + 0.035 * Math.log(a.distance_m / metres)) * boost * rng.between(0.99, 1.02),
        );
        efforts.set(key, Math.round((metres / v) * 10) / 10);
      }
  }
  const hr = new Map(
    within.map(([d]) => [d, Math.round(avgHr + (maxHr - avgHr) * Math.exp(-d / 600) * 0.95)]),
  );
  const hrZones = spread(
    moving,
    avgHr,
    hard(a) ? 13 : 8,
    HR_SHARES.map((s) => s * HR_MAX),
  );
  return {
    a,
    day: isoDay(new Date(a.start_utc)),
    moving,
    load: Math.round(trimp(moving / 60, avgHr) * 10) / 10,
    hrZones,
    powerZones: null,
    paceZones: null,
    power,
    speed,
    hr,
    efforts,
    efficiency: a.sport === 'run' && avgSpeed ? Math.round(((avgSpeed * 60) / avgHr) * 1000) / 1000 : null,
    decoupling: a.sport === 'run' ? Math.round(rng.between(1, 9) * 10) / 10 : null,
  };
}

// --- estimates -------------------------------------------------------------------------------------

/** Daniels' VDOT of a run of `d` metres in `t` seconds. */
function vdot(d: number, t: number) {
  const min = t / 60;
  const v = d / min;
  const vo2 = -4.6 + 0.182258 * v + 0.000104 * v * v;
  const pct = 0.8 + 0.1894393 * Math.exp(-0.012778 * min) + 0.2989558 * Math.exp(-0.1932605 * min);
  return vo2 / pct;
}
const riegel = (d: number, t: number, target: number) => t * (target / d) ** 1.06;

type Snapshot = {
  ftp: { value: number; source: Computed } | null;
  critical_speed: { value: number; source: Computed } | null;
  vdot: { value: number; key: string; d: number; t: number; source: Computed } | null;
  lthr: { value: number; source: Computed } | null;
  hr_max: { value: number; source: Computed } | null;
};

function snapshot(list: Computed[], day: string): Snapshot {
  const n = dayNumber(day);
  const recent = (days: number) => list.filter((c) => c.day <= day && dayNumber(c.day) > n - days);
  const out: Snapshot = { ftp: null, critical_speed: null, vdot: null, lthr: null, hr_max: null };
  for (const c of recent(42)) {
    const p = c.power?.get(1200);
    if (p && (!out.ftp || p * 0.95 > out.ftp.value))
      out.ftp = { value: Math.round(p * 0.95 * 10) / 10, source: c };
  }
  for (const c of recent(84)) {
    if (c.a.sport !== 'run') continue;
    const s = c.speed?.get(1800) ?? (c.speed?.get(1200) ?? 0) * 0.97;
    if (s && (!out.critical_speed || s > out.critical_speed.value))
      out.critical_speed = { value: Math.round(s * 1000) / 1000, source: c };
    for (const [key, t] of c.efforts) {
      const d = RUN_DISTANCES[key] ?? 0;
      if (d < 3000) continue;
      const v = vdot(d, t);
      if (!out.vdot || v > out.vdot.value)
        out.vdot = { value: Math.round(v * 10) / 10, key, d, t, source: c };
    }
    const h = c.hr?.get(3600);
    if (h && (!out.lthr || h > out.lthr.value)) out.lthr = { value: h, source: c };
  }
  for (const c of recent(365)) {
    const h = c.a.max_hr;
    if (h && (!out.hr_max || h > out.hr_max.value)) out.hr_max = { value: h, source: c };
  }
  return out;
}

const METHODS: Record<Exclude<keyof Snapshot, 'vdot'>, string> = {
  ftp: 'ftp_20min',
  critical_speed: 'critical_speed',
  lthr: 'lthr_60min',
  hr_max: 'hr_max_year',
};

const source = (c: Computed) => ({ id: c.a.id, name: c.a.name, sport: c.a.sport, day: c.day });
const kmOf = (key: string) => String(Math.round(((RUN_DISTANCES[key] ?? 0) / 1000) * 10) / 10);

function estimates(list: Computed[], today: string): EstimateOut[] {
  const first = list[0]?.day;
  if (!first) return [];
  // As they stood each Monday since there was enough to go on, and today.
  const days: string[] = [];
  for (let d = plusDays(mondayOf(first), 56); d < today; d = plusDays(d, 7)) days.push(d);
  days.push(today);
  const shots = days.map((d) => [d, snapshot(list, d)] as const);
  const now = shots[shots.length - 1]?.[1];
  if (!now) return [];
  const out: EstimateOut[] = [];
  for (const kind of ['ftp', 'critical_speed', 'vdot', 'lthr', 'hr_max'] as const) {
    const latest = now[kind];
    if (!latest) continue;
    const history: { day: string; value: number }[] = [];
    for (const [day, s] of shots) {
      const v = s[kind]?.value;
      if (v !== undefined && history[history.length - 1]?.value !== v) history.push({ day, value: v });
    }
    out.push({
      kind,
      value: latest.value,
      method: kind === 'vdot' ? `vdot:${kmOf(now.vdot?.key ?? '')}` : METHODS[kind],
      day: history[history.length - 1]?.day ?? today,
      sources: [source(latest.source)],
      history,
    });
  }
  // The race times from the best VDOT, as they stood each week of the last six months.
  const best = now.vdot;
  if (best) {
    const half = shots.slice(-27);
    for (const [race, metres] of Object.entries(RACES)) {
      out.push({
        kind: `race_${race}`,
        value: Math.round(riegel(best.d, best.t, metres)),
        method: `riegel:${kmOf(best.key)}`,
        day: today,
        sources: [source(best.source)],
        history: half.flatMap(([day, s]) =>
          s.vdot ? [{ day, value: Math.round(riegel(s.vdot.d, s.vdot.t, metres) * 1000) / 1000 }] : [],
        ),
      });
    }
  }
  return out;
}

// --- the whole model -------------------------------------------------------------------------------

function fitness(list: Computed[], today: string): FitnessDay[] {
  const loads = new Map<string, number>();
  for (const c of list) loads.set(c.day, (loads.get(c.day) ?? 0) + c.load);
  const first = list[0]?.day;
  if (!first) return [];
  const out: FitnessDay[] = [];
  let ctl = 0;
  let atl = 0;
  const r = (v: number) => Math.round(v * 10) / 10;
  for (let day = first; day <= today; day = plusDays(day, 1)) {
    const load = loads.get(day) ?? 0;
    const tsb = ctl - atl;
    ctl += (load - ctl) / 42;
    atl += (load - atl) / 7;
    out.push({ day, load: r(load), ctl: r(ctl), atl: r(atl), tsb: r(tsb) });
  }
  return out;
}

function build() {
  const today = db.todayIso;
  const list = db.activities.map(compute).sort((x, y) => x.a.start_utc.localeCompare(y.a.start_utc));
  const est = estimates(list, today);
  const ftp = est.find((e) => e.kind === 'ftp')?.value ?? null;
  const thresholdSpeed = est.find((e) => e.kind === 'critical_speed')?.value ?? null;
  const powerBounds = ftp ? POWER_SHARES.map((s) => s * ftp) : [];
  const paceBounds = thresholdSpeed ? PACE_SHARES.map((s) => s * thresholdSpeed) : [];
  for (const c of list) {
    if (c.a.sport === 'ride' && c.a.avg_power && powerBounds.length)
      c.powerZones = spread(c.moving, c.a.avg_power, c.a.avg_power * 0.35, powerBounds);
    if (c.speed && paceBounds.length) {
      const v = (c.a.distance_m ?? 0) / c.moving;
      c.paceZones = spread(c.moving, v, v * (hard(c.a) ? 0.1 : 0.06), paceBounds);
    }
  }
  return {
    today,
    list,
    byId: new Map(list.map((c) => [c.a.id, c])),
    fitness: fitness(list, today),
    estimates: est,
    hrBounds: HR_SHARES.map((s) => s * HR_MAX),
    powerBounds,
    paceBounds,
    memo: new Map<string, unknown>(),
  };
}

export type Model = ReturnType<typeof build>;

export const model = (): Model => db.slice('progress:model', build);

/** An answer worked out once per query (the URL), until the data is reset. */
export function memo<T>(request: Request, make: (m: Model, q: URLSearchParams) => T): T {
  const m = model();
  const url = new URL(request.url);
  const key = `${url.pathname}?${[...url.searchParams].sort().join('&')}`;
  if (!m.memo.has(key)) m.memo.set(key, make(m, url.searchParams));
  return m.memo.get(key) as T;
}

/** The activities with a local day in [from, to), of these sports when given. */
export function within(m: Model, from: string, to: string, sports?: string[]) {
  return m.list.filter((c) => c.day >= from && c.day < to && (!sports?.length || sports.includes(c.a.sport)));
}

export const emptyTotals = (): Totals => ({ count: 0, distance_m: 0, moving_s: 0, ascent_m: 0, load: 0 });
export function add(t: Totals, c: Computed) {
  t.count += 1;
  t.distance_m += c.a.distance_m ?? 0;
  t.moving_s += c.moving;
  t.ascent_m += c.a.ascent_m ?? 0;
  t.load = Math.round((t.load + c.load) * 10) / 10;
}
