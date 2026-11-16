/**
 * Mock handlers: the activities and what hangs off them — the feed, search and months, an activity's
 * detail, series, context and Coach texts, editing and deleting, gear, photos, share links, routes,
 * segments, the week and the engine's events.
 *
 * Everything is derived from the demo athlete's activities in `db`: an activity's series are made up
 * from its own seed and route (so they are the same every time), and its laps, splits, best efforts
 * and metrics are read from those series, so they add up to the activity. Each activity's figures
 * are worked out once and kept in this domain's slice; changes (an edit, a new gear, a share…) live
 * there too until the next reset.
 */
import { db, SEED, summary, type MockActivity } from '../db';
import { seeded } from '../random';
import { fail, json, route, type Req, type Res } from '../route';

type Detail = Res<'/api/activities/{activity_id}', 'get'>;
type Streams = Res<'/api/activities/{activity_id}/streams', 'get'>;
type Lap = Detail['laps'][number];
type Split = Detail['splits'][number];
type BestEffort = Detail['best_efforts'][number];
type SetRow = Detail['sets'][number];
type Curve = NonNullable<Detail['metrics']>['power_curve'][number];
type Context = Res<'/api/activities/{activity_id}/context', 'get'>;
type GearOut = Res<'/api/gear', 'get'>[number];
type PhotoOut = Res<'/api/activities/{activity_id}/photos', 'post'>;
type ShareOut = Res<'/api/activities/{activity_id}/shares', 'get'>[number];
type Shared = Res<'/api/public/shares/{token}', 'get'>;
type RouteActivity = Res<'/api/routes', 'get'>[number]['best'];
type SegmentOut = Res<'/api/segments', 'get'>[number];
type SummaryOut = Res<'/api/activities/{activity_id}/summary', 'get'>;
type ReportOut = Res<'/api/activities/{activity_id}/report', 'get'>;
type EventOut = Res<'/api/events', 'get'>[number];
type Week = Res<'/api/week', 'get'>;

// --- small helpers ---------------------------------------------------------------------------------

const DAY_MS = 86_400_000;
const HR_MAX = 190;
const HR_REST = 50;
const FTP = 245;
const HOME: [number, number] = [40.4168, -3.7038];

const round = (v: number, digits = 0) => {
  const f = 10 ** digits;
  return Math.round(v * f) / f;
};
const mean = (xs: number[]) => (xs.length ? xs.reduce((s, x) => s + x, 0) / xs.length : 0);
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

function offsetMs(tz: string | null): number {
  const m = /^([+-])(\d{2}):(\d{2})$/.exec(tz ?? '');
  if (!m) return 0;
  return (m[1] === '-' ? -1 : 1) * (Number(m[2]) * 60 + Number(m[3])) * 60_000;
}
/** The activity's local date (YYYY-MM-DD). */
const days = new WeakMap<MockActivity, string>();
const localDay = (a: MockActivity) => {
  let day = days.get(a);
  if (!day) {
    day = new Date(Date.parse(a.start_utc) + offsetMs(a.timezone)).toISOString().slice(0, 10);
    days.set(a, day);
  }
  return day;
};
const addDays = (iso: string, n: number) =>
  new Date(Date.parse(`${iso}T00:00:00Z`) + n * DAY_MS).toISOString().slice(0, 10);
const mondayOf = (iso: string) => addDays(iso, -((new Date(`${iso}T00:00:00Z`).getUTCDay() + 6) % 7));
const moving = (a: MockActivity) => a.moving_s ?? a.duration_s;
const at = (a: MockActivity, seconds: number) =>
  new Date(Date.parse(a.start_utc) + seconds * 1000).toISOString();

function metres([lat1, lon1]: [number, number], [lat2, lon2]: [number, number]): number {
  const rad = Math.PI / 180;
  const x = (lon2 - lon1) * rad * Math.cos(((lat1 + lat2) / 2) * rad);
  const y = (lat2 - lat1) * rad;
  return Math.sqrt(x * x + y * y) * 6_371_000;
}

/** Google's encoded polyline, at precision 5 (routes) or 6 (segments). */
function encode(points: [number, number][], precision = 5): string {
  const f = 10 ** precision;
  let last = [0, 0];
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
    const p = [Math.round(lat * f), Math.round(lon * f)];
    push((p[0] ?? 0) - (last[0] ?? 0));
    push((p[1] ?? 0) - (last[1] ?? 0));
    last = p;
  }
  return out;
}

/** The ground's shape, the same for every activity: rolling, rising towards the sierra in the north. */
const ground = ([lat, lon]: [number, number]) =>
  Math.sin(lat * 310) * Math.cos(lon * 240) + 0.5 * Math.sin((lat + lon) * 690) + (lat - HOME[0]) * 40;

/** An activity's own random numbers, so its series are the same whatever was asked before. */
const rngOf = (a: MockActivity, salt = 0) => seeded((SEED + a.id * 7919 + salt * 104_729) >>> 0);

/** Relative effort (Banister's TRIMP) from the average heart rate: cheap, so fitness can use it. */
function trimpOf(a: MockActivity): number | null {
  if (!a.avg_hr) return null;
  const r = clamp((a.avg_hr - HR_REST) / (HR_MAX - HR_REST), 0, 1);
  return round((moving(a) / 60) * r * 0.64 * Math.exp(1.92 * r), 1);
}

// --- an activity's series ----------------------------------------------------------------------------

type Series = {
  t: number[];
  /** Moving time at each sample (t without the stop). */
  mt: number[];
  dist: number[] | null;
  alt: number[] | null;
  lat: number[] | null;
  lon: number[] | null;
  speed: number[] | null;
  cad: number[] | null;
  power: number[] | null;
  temp: number[] | null;
  hr: number[];
  /** Where the laps start (sample indexes): auto-laps, or an interval session's blocks. */
  laps: number[];
};

/** A strength session's sets: what its exercises (or its class) were, active and rest in turn. */
function setsOf(a: MockActivity): SetRow[] {
  if (a.sport !== 'strength') return [];
  const r = rngOf(a, 2);
  const out: SetRow[] = [];
  const total = moving(a);
  let s = 120; // warming up
  const weights: Record<string, number> = {
    squat: 70,
    deadlift: 90,
    bench_press: 60,
    row: 50,
    pull_up: 0,
    lunge: 20,
    plank: 0,
    shoulder_press: 30,
  };
  if (a.exercises) {
    for (const [category, sets] of Object.entries(a.exercises)) {
      for (let k = 0; k < sets && s < total - 60; k++) {
        const duration = round(r.between(35, 60));
        out.push({
          start_s: s,
          duration_s: duration,
          kind: 'active',
          reps: category === 'plank' ? null : r.int(6, 12),
          weight_kg: weights[category]
            ? round(((weights[category] ?? 0) * r.between(0.9, 1.15)) / 2.5) * 2.5
            : null,
          category,
        });
        s += duration;
        const rest = round(r.between(75, 150));
        out.push({ start_s: s, duration_s: rest, kind: 'rest', reps: null, weight_kg: null, category: null });
        s += rest;
      }
    }
  } else {
    // A class: rounds of work and short rests until the end.
    while (s < total - 120) {
      const duration = r.int(40, 90);
      out.push({
        start_s: s,
        duration_s: duration,
        kind: 'active',
        reps: r.int(10, 20),
        weight_kg: null,
        category: null,
      });
      s += duration;
      const rest = r.int(15, 40);
      out.push({ start_s: s, duration_s: rest, kind: 'rest', reps: null, weight_kg: null, category: null });
      s += rest;
    }
  }
  return out;
}

/** An activity's series; `light` stops after time and distance (enough for its best efforts). */
function makeSeries(a: MockActivity, light = false): Series {
  const r = rngOf(a, 1);
  const total = moving(a);
  const step = Math.min(10, Math.max(5, Math.ceil(total / 1200)));
  const n = Math.floor(total / step) + 1;
  const gap = Math.max(0, a.duration_s - total);
  const pauseAt = Math.floor(n * r.between(0.4, 0.6));
  const mt = Array.from({ length: n }, (_, i) => i * step);
  const t = mt.map((m, i) => m + (i > pauseAt ? gap : 0));
  const avgHr = a.avg_hr ?? 120;
  const maxHr = a.max_hr ?? avgHr + 20;
  const finishHr = (raw: number[]) => {
    // Heart rate lags the effort, then the whole line is set on the activity's average.
    const k = Math.min(1, step / 25);
    let h = raw[0] ?? avgHr;
    const smooth = raw.map((x) => (h += (x - h) * k));
    const shift = avgHr - mean(smooth);
    return smooth.map((x) => Math.round(clamp(x + shift, 70, maxHr)));
  };

  if (!a.distance_m || !a.points.length) {
    const sets = setsOf(a);
    const raw = mt.map((m) => {
      const set = sets.find((s) => m >= s.start_s && m < s.start_s + s.duration_s);
      return (set?.kind === 'active' ? avgHr + 18 : avgHr - 8) + r.between(-3, 3);
    });
    return {
      t,
      mt,
      dist: null,
      alt: null,
      lat: null,
      lon: null,
      speed: null,
      cad: null,
      power: null,
      temp: null,
      hr: finishHr(raw),
      laps: [0],
    };
  }

  const L = a.distance_m;
  const v = L / total;
  const pts = a.points;
  const cum = [0];
  for (let i = 1; i < pts.length; i++)
    cum.push((cum[i - 1] ?? 0) + metres(pts[i - 1] ?? HOME, pts[i] ?? HOME));
  const G = cum.at(-1) || 1;
  // The altitude at the route's points, scaled so the climbing adds up to the activity's ascent.
  const g = pts.map(ground);
  let rawAscent = 0;
  for (let i = 1; i < g.length; i++) rawAscent += Math.max(0, (g[i] ?? 0) - (g[i - 1] ?? 0));
  const scale = rawAscent ? (a.ascent_m ?? 0) / rawAscent : 0;
  const vAlt = g.map((x) => 655 + scale * x);
  const grades = vAlt.map((x, i) =>
    i ? (x - (vAlt[i - 1] ?? 0)) / Math.max(1, (cum[i] ?? 0) - (cum[i - 1] ?? 0)) : 0,
  );

  // How hard each moment is: the session's shape plus a slow wander.
  const intervals = a.name === 'Intervals';
  const tempo = a.name === 'Tempo run';
  const period = r.between(500, 1200);
  const phase = r.between(0, Math.PI * 2);
  const blocks: number[] = [0];
  const effort = mt.map((m, i) => {
    const f = m / total;
    if (intervals) {
      if (f < 0.2 || f > 0.88) return 0.86;
      const into = (m - 0.2 * total) % 300; // 3 minutes hard, 2 easy
      if (i && into < step) blocks.push(i);
      if (i && Math.abs(into - 180) < step / 2) blocks.push(i);
      return into < 180 ? 1.16 : 0.8;
    }
    if (tempo) return f < 0.2 || f > 0.85 ? 0.9 : 1.06;
    return 1 + 0.05 * Math.sin((2 * Math.PI * m) / period + phase) - (m < 300 ? 0.06 : 0);
  });
  if (intervals) blocks.push(Math.round(n * 0.88));
  let wander = 0;
  const kGrade = a.sport === 'ride' ? 9 : a.sport === 'run' ? 3.5 : 2;
  const speed: number[] = [];
  const dist: number[] = [];
  let d = 0;
  let seg = 1;
  for (let i = 0; i < n; i++) {
    dist.push(d);
    const along = (d / L) * G;
    while (seg < cum.length - 1 && (cum[seg] ?? 0) < along) seg++;
    wander = 0.85 * wander + 0.15 * r.between(-0.12, 0.12);
    const s = Math.max(0.3 * v, v * ((effort[i] ?? 0) + wander) * (1 - kGrade * (grades[seg] ?? 0)));
    speed.push(s);
    d += s * step;
  }
  const fix = L / (dist.at(-1) || L);
  for (let i = 0; i < n; i++) {
    dist[i] = (dist[i] ?? 0) * fix;
    speed[i] = (speed[i] ?? 0) * fix;
  }
  if (light)
    return {
      t,
      mt,
      dist: dist.map((x) => round(x, 1)),
      alt: null,
      lat: null,
      lon: null,
      speed: null,
      cad: null,
      power: null,
      temp: null,
      hr: [],
      laps: [0],
    };

  // Where each sample is on the route, and its altitude there.
  const lat: number[] = [];
  const lon: number[] = [];
  const alt: number[] = [];
  seg = 1;
  for (let i = 0; i < n; i++) {
    const along = ((dist[i] ?? 0) / L) * G;
    while (seg < cum.length - 1 && (cum[seg] ?? 0) < along) seg++;
    const span = (cum[seg] ?? 0) - (cum[seg - 1] ?? 0) || 1;
    const f = clamp((along - (cum[seg - 1] ?? 0)) / span, 0, 1);
    const p0 = pts[seg - 1] ?? HOME;
    const p1 = pts[seg] ?? HOME;
    lat.push(round(p0[0] + (p1[0] - p0[0]) * f, 6));
    lon.push(round(p0[1] + (p1[1] - p0[1]) * f, 6));
    alt.push(round((vAlt[seg - 1] ?? 0) + ((vAlt[seg] ?? 0) - (vAlt[seg - 1] ?? 0)) * f, 1));
  }
  const gradeAt = (i: number) =>
    i ? ((alt[i] ?? 0) - (alt[i - 1] ?? 0)) / Math.max(1, (dist[i] ?? 0) - (dist[i - 1] ?? 0)) : 0;

  const hrRaw = mt.map(
    (m, i) =>
      avgHr +
      28 * ((speed[i] ?? 0) / v - 1) +
      120 * gradeAt(i) +
      8 * (m / total - 0.5) -
      22 * Math.exp(-m / 200),
  );
  let power: number[] | null = null;
  if (a.sport === 'ride' && a.avg_power) {
    const raw = speed.map((s, i) => {
      const grade = gradeAt(i);
      if (grade < -0.025 && r.chance(0.55)) return 0; // coasting downhill
      return Math.max(0, (s / v) ** 1.6 * (1 + 7 * grade) * 200 + r.between(-25, 25));
    });
    const k = a.avg_power / (mean(raw) || 1);
    power = raw.map((p) => Math.round(p * k));
  }
  const cad = speed.map((s, i) =>
    a.sport === 'ride'
      ? power && power[i] === 0
        ? 0
        : round(86 + 8 * (s / v - 1) + r.between(-3, 3))
      : a.sport === 'run'
        ? round(170 + 14 * (s / v - 1) + r.between(-2, 2))
        : round(108 + r.between(-4, 4)),
  );
  const month = new Date(a.start_utc).getUTCMonth();
  const base = 15 - 9 * Math.cos(((month + 0.5) / 12) * 2 * Math.PI);
  const temp = mt.map((m) => round(base + (m / 3600) * 1.5 + r.between(-0.3, 0.3), 1));

  const lapEvery = a.sport === 'ride' ? 5000 : 1000;
  const laps = intervals
    ? [...new Set(blocks)].sort((x, y) => x - y)
    : [
        0,
        ...dist.flatMap((x, i) =>
          i && Math.floor(x / lapEvery) > Math.floor((dist[i - 1] ?? 0) / lapEvery) ? [i] : [],
        ),
      ];

  return {
    t,
    mt,
    dist: dist.map((x) => round(x, 1)),
    alt,
    lat,
    lon,
    speed: speed.map((s) => round(s, 3)),
    cad,
    power,
    temp,
    hr: finishHr(hrRaw),
    laps,
  };
}

// --- what is read from the series ------------------------------------------------------------------

const RUN_DISTANCES: [string, number][] = [
  ['400m', 400],
  ['half_mile', 804.672],
  ['1k', 1000],
  ['1mile', 1609.344],
  ['2mile', 3218.688],
  ['5k', 5000],
  ['10k', 10000],
  ['15k', 15000],
  ['10mile', 16093.44],
  ['20k', 20000],
  ['half_marathon', 21097.5],
  ['marathon', 42195],
];
const CURVE_DURATIONS = [5, 15, 30, 60, 120, 300, 600, 1200, 1800, 3600];
const POWER_BESTS = [5, 15, 30, 60, 300, 600, 1200, 3600];
const HR_BOUNDS = [0, 0.6, 0.7, 0.8, 0.9].map((f) => Math.round(f * HR_MAX));
const POWER_BOUNDS = [0, 0.55, 0.75, 0.9, 1.05, 1.2, 1.5].map((f) => Math.round(f * FTP));

type Analysis = {
  laps: Lap[];
  splits: Split[];
  best_efforts: BestEffort[];
  metrics: NonNullable<Detail['metrics']>;
  max_hr: number | null;
  descent_m: number | null;
  normalized_power: number | null;
  series: string[];
  sample_count: number;
  /** Best mean power over POWER_BESTS' durations, for the medals. */
  power_bests: Record<number, number>;
};

/** The time at distance `d`, read between the samples. */
function timeAt(s: Series, d: number): number {
  const dist = s.dist;
  if (!dist) return 0;
  let lo = 0;
  let hi = dist.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if ((dist[mid] ?? 0) < d) lo = mid;
    else hi = mid;
  }
  const span = (dist[hi] ?? 0) - (dist[lo] ?? 0) || 1;
  return (s.mt[lo] ?? 0) + ((d - (dist[lo] ?? 0)) / span) * ((s.mt[hi] ?? 0) - (s.mt[lo] ?? 0));
}

function bestMeans(values: number[], step: number, durations: number[], total: number): Curve[] {
  const prefix = [0];
  for (const v of values) prefix.push((prefix.at(-1) ?? 0) + v);
  const out: Curve[] = [];
  for (const duration of durations) {
    if (duration > total) break;
    const w = Math.max(1, Math.round(duration / step));
    let best = 0;
    for (let i = 0; i + w <= values.length; i++)
      best = Math.max(best, (prefix[i + w] ?? 0) - (prefix[i] ?? 0));
    out.push({ duration_s: duration, value: best / w });
  }
  return out;
}

/** A run's fastest time over each distance, read between the samples (moving time). */
function effortsOf(s: Series, L: number): BestEffort[] {
  const dist = s.dist;
  if (!dist) return [];
  const n = s.t.length;
  const efforts: BestEffort[] = [];
  for (const [key, D] of RUN_DISTANCES) {
    if (D > L) break;
    let best = Infinity;
    let from = 0;
    let j = 1;
    for (let i = 0; i < n; i++) {
      const target = (dist[i] ?? 0) + D;
      if (target > L) break;
      while (j < n - 1 && (dist[j] ?? 0) < target) j++;
      const span = (dist[j] ?? 0) - (dist[j - 1] ?? 0) || 1;
      const reached =
        (s.mt[j - 1] ?? 0) + ((target - (dist[j - 1] ?? 0)) / span) * ((s.mt[j] ?? 0) - (s.mt[j - 1] ?? 0));
      const time = reached - (s.mt[i] ?? 0);
      if (time < best) {
        best = time;
        from = i;
      }
    }
    efforts.push({
      distance_key: key,
      distance_m: D,
      time_s: round(best, 1),
      start_s: s.t[from] ?? 0,
      start_distance_m: round(dist[from] ?? 0, 1),
    });
  }
  return efforts;
}

function analyse(a: MockActivity, s: Series): Analysis {
  const n = s.t.length;
  const step = s.mt[1] ?? 5;
  const sumBy = (xs: number[] | null, i0: number, i1: number, up: boolean) => {
    if (!xs) return null;
    let total = 0;
    for (let i = i0 + 1; i <= i1; i++) {
      const diff = (xs[i] ?? 0) - (xs[i - 1] ?? 0);
      if (up ? diff > 0 : diff < 0) total += Math.abs(diff);
    }
    return round(total);
  };
  const avgOf = (xs: number[] | null, i0: number, i1: number) => {
    if (!xs || i1 < i0) return null;
    return Math.round(mean(xs.slice(i0, i1 + 1)));
  };
  const laps: Lap[] = s.laps.map((i0, k) => {
    const i1 = k + 1 < s.laps.length ? (s.laps[k + 1] ?? 0) : n - 1;
    return {
      idx: k,
      start_utc: at(a, s.t[i0] ?? 0),
      duration_s: (s.t[i1] ?? 0) - (s.t[i0] ?? 0),
      distance_m: s.dist ? round((s.dist[i1] ?? 0) - (s.dist[i0] ?? 0), 1) : null,
      avg_hr: avgOf(s.hr, i0, i1),
      max_hr: Math.max(...s.hr.slice(i0, i1 + 1)),
      avg_power: avgOf(s.power, i0, i1),
      ascent_m: sumBy(s.alt, i0, i1, true),
      descent_m: sumBy(s.alt, i0, i1, false),
    };
  });

  const splits: Split[] = [];
  const efforts: BestEffort[] = [];
  const L = a.distance_m ?? 0;
  if (s.dist && L) {
    // One pass over the samples: each split's heart rate, and the altitude where it starts and ends.
    let i = 0;
    let altFrom = s.alt?.[0] ?? 0;
    for (let k = 0; k * 1000 < L - 50; k++) {
      const d0 = k * 1000;
      const d1 = Math.min(L, d0 + 1000);
      let hrSum = 0;
      let count = 0;
      while (i < n - 1 && (s.dist[i] ?? 0) < d1) {
        hrSum += s.hr[i] ?? 0;
        count++;
        i++;
      }
      const altTo = s.alt?.[i] ?? 0;
      splits.push({
        distance_m: round(d1 - d0, 1),
        time_s: round(timeAt(s, d1) - timeAt(s, d0), 1),
        elevation_change_m: round(altTo - altFrom, 1),
        avg_hr: count ? Math.round(hrSum / count) : null,
      });
      altFrom = altTo;
    }
    if (a.sport === 'run') efforts.push(...effortsOf(s, L));
  }

  const zoneTime = (xs: number[], bounds: number[]) => {
    const out = bounds.map(() => 0);
    for (const x of xs) {
      let z = 0;
      while (z + 1 < bounds.length && x >= (bounds[z + 1] ?? 0)) z++;
      out[z] = (out[z] ?? 0) + step;
    }
    return out;
  };
  const total = moving(a);
  let np: number | null = null;
  if (s.power) {
    const w = Math.max(1, Math.round(30 / step));
    const rolled: number[] = [];
    let acc = 0;
    for (let i = 0; i < n; i++) {
      acc += s.power[i] ?? 0;
      if (i >= w) acc -= s.power[i - w] ?? 0;
      if (i >= w - 1) rolled.push(acc / w);
    }
    np = Math.round(mean(rolled.map((x) => x ** 4)) ** 0.25);
  }
  const half = Math.floor(n / 2);
  const ef = (i0: number, i1: number) => {
    const work = s.power ?? s.speed;
    if (!work) return null;
    return mean(work.slice(i0, i1)) / (mean(s.hr.slice(i0, i1)) || 1);
  };
  const ef1 = ef(0, half);
  const ef2 = ef(half, n);
  const powerCurve = s.power ? bestMeans(s.power, step, CURVE_DURATIONS, total) : [];
  const speedCurve = s.speed && a.sport !== 'ride' ? bestMeans(s.speed, step, CURVE_DURATIONS, total) : [];
  const speedMean = s.speed ? mean(s.speed) : null;
  const series = Object.entries({
    t: s.t,
    lat: s.lat,
    lon: s.lon,
    dist: s.dist,
    alt: s.alt,
    hr: s.hr,
    cad: s.cad,
    speed: s.speed,
    power: s.power,
    temp: s.temp,
  }).flatMap(([k, v]) => (v ? [k] : []));

  return {
    laps,
    splits,
    best_efforts: efforts,
    metrics: {
      trimp: trimpOf(a),
      hr_zones: zoneTime(s.hr, HR_BOUNDS),
      hr_zone_bounds: HR_BOUNDS,
      power_zones: s.power ? zoneTime(s.power, POWER_BOUNDS) : null,
      power_zone_bounds: s.power ? POWER_BOUNDS : null,
      gap_speed:
        a.sport === 'run' && a.distance_m
          ? round((a.distance_m / total) * (1 + ((a.ascent_m ?? 0) / a.distance_m) * 3), 3)
          : null,
      calories: a.calories,
      calories_estimated: false,
      efficiency:
        np && a.avg_hr
          ? round(np / a.avg_hr, 2)
          : speedMean && a.avg_hr
            ? round((speedMean * 60) / a.avg_hr, 2)
            : null,
      decoupling: ef1 && ef2 ? round(((ef1 - ef2) / ef1) * 100, 1) : null,
      power_curve: powerCurve.map((c) => ({ ...c, value: Math.round(c.value) })),
      speed_curve: speedCurve.map((c) => ({ ...c, value: round(c.value, 3) })),
    },
    max_hr: Math.max(...s.hr),
    descent_m: sumBy(s.alt, 0, n - 1, false),
    normalized_power: np,
    series,
    sample_count: n,
    power_bests: Object.fromEntries(
      (s.power ? bestMeans(s.power, step, POWER_BESTS, total) : []).map((c) => [
        c.duration_s,
        Math.round(c.value),
      ]),
    ),
  };
}

// --- this domain's state ---------------------------------------------------------------------------

type GearRow = Omit<
  GearOut,
  'distance_m' | 'activities' | 'moving_s' | 'first_used' | 'last_used' | 'over_limit'
>;
type PhotoRow = PhotoOut;
type ShareRow = Omit<ShareOut, 'path'> & { token: string };
type RouteRow = { id: number; sport: string; name: string; members: number[] };
type Effort = { activity_id: number; elapsed_s: number; avg_hr: number | null; avg_power: number | null };
type SegmentRow = {
  id: number;
  name: string;
  points: [number, number][];
  distance_m: number;
  ascent_m: number | null;
  efforts: Effort[];
};

const CAPTIONS = ['Sunrise over the park', 'Up on the hill', 'Coffee stop', 'The view from the top', null];

function build() {
  const r = seeded(SEED + 77);
  const activities = db.activities;
  const now = Date.parse(db.todayIso);

  // Gear: the current trainers, a pair retired a year ago, race shoes for the fast days, the bike.
  const gear: GearRow[] = [
    {
      id: 1,
      kind: 'shoe',
      name: 'Daily trainers',
      brand: 'Asics',
      model: 'Novablast 4',
      sports: ['run'],
      initial_m: 0,
      limit_m: 1_000_000,
      retired: false,
      habitual: true,
    },
    {
      id: 2,
      kind: 'bike',
      name: 'Road bike',
      brand: 'Canyon',
      model: 'Endurace CF 7',
      sports: ['ride'],
      initial_m: 1_250_000,
      limit_m: null,
      retired: false,
      habitual: true,
    },
    {
      id: 3,
      kind: 'shoe',
      name: 'Old trainers',
      brand: 'Brooks',
      model: 'Ghost 15',
      sports: ['run', 'walk'],
      initial_m: 0,
      limit_m: 800_000,
      retired: true,
      habitual: false,
    },
    {
      id: 4,
      kind: 'shoe',
      name: 'Race shoes',
      brand: 'Nike',
      model: 'Vaporfly 3',
      sports: ['run'],
      initial_m: 0,
      limit_m: 500_000,
      retired: false,
      habitual: false,
    },
    {
      id: 5,
      kind: 'shoe',
      name: 'Pegasus',
      brand: 'Nike',
      model: 'Pegasus 40',
      sports: ['run'],
      initial_m: 0,
      limit_m: 800_000,
      retired: true,
      habitual: false,
    },
    {
      id: 6,
      kind: 'shoe',
      name: 'First trainers',
      brand: 'Hoka',
      model: 'Clifton 9',
      sports: ['run'],
      initial_m: 0,
      limit_m: 800_000,
      retired: true,
      habitual: false,
    },
  ];
  for (const a of activities) {
    if (a.sport !== 'run') continue;
    const age = (now - Date.parse(a.start_utc)) / DAY_MS;
    // A new pair every 250 days or so; the race shoes on the fast days of the last year.
    a.gear_id = age > 750 ? 6 : age > 500 ? 5 : age > 250 ? 3 : 1;
    if (age < 365 && (a.name === 'Tempo run' || a.name === 'Intervals')) a.gear_id = 4;
  }

  // Routes: the same loop run again and again (the first time's points, for every time after).
  const templates: [string, string, number][] = [
    ['run', 'River loop', 8000],
    ['run', 'Park 10k', 10000],
    ['run', 'Long run to the lake', 16000],
    ['ride', 'Sierra loop', 60000],
    ['walk', 'Neighbourhood walk', 4500],
  ];
  const routes: RouteRow[] = [];
  const taken = new Set<number>();
  for (const [sport, name, d0] of templates) {
    const near = activities.filter(
      (a) =>
        a.sport === sport && a.distance_m && Math.abs(a.distance_m - d0) <= d0 * 0.04 && !taken.has(a.id),
    );
    const first = near.at(-1); // the oldest
    if (!first) continue;
    const members = near.filter((a) => a === first || r.chance(0.7));
    for (const a of members) {
      taken.add(a.id);
      if (a === first) continue;
      a.points = first.points;
      a.route = first.route;
    }
    if (members.length > 1)
      routes.push({ id: first.id, sport, name, members: members.map((a) => a.id).sort((x, y) => x - y) });
  }

  // Segments: a stretch of each run and ride route, with every time through it.
  const segNames: Record<string, string> = {
    'River loop': 'Riverside sprint',
    'Park 10k': 'Park climb',
    'Long run to the lake': 'Lake drag',
    'Sierra loop': 'Sierra climb',
  };
  const segments: SegmentRow[] = [];
  for (const group of routes) {
    const name = segNames[group.name];
    if (!name) continue;
    const first = db.activity(group.id);
    if (!first) continue;
    const pts = first.points;
    const part = pts.slice(Math.floor(pts.length * 0.25), Math.floor(pts.length * 0.45) + 1);
    let length = 0;
    let up = 0;
    const alts = part.map(ground);
    for (let i = 1; i < part.length; i++) {
      length += metres(part[i - 1] ?? HOME, part[i] ?? HOME);
      up += Math.max(0, (alts[i] ?? 0) - (alts[i - 1] ?? 0));
    }
    const ascent = first.ascent_m
      ? round(up * ((first.ascent_m ?? 0) / Math.max(1, totalGround(pts))), 1)
      : null;
    segments.push({
      id: segments.length + 1,
      name,
      points: part,
      distance_m: round(length, 1),
      ascent_m: ascent,
      efforts: group.members.flatMap((id) => {
        const a = db.activity(id);
        if (!a) return [];
        const v = (a.distance_m ?? 1) / moving(a);
        return {
          activity_id: id,
          elapsed_s: round(length / (v * r.between(0.95, 1.12))),
          avg_hr: a.avg_hr ? a.avg_hr + r.int(2, 9) : null,
          avg_power: a.avg_power ? Math.round(a.avg_power * r.between(1.08, 1.25)) : null,
        };
      }),
    });
  }

  // Photos on a few outdoor activities.
  const photos = new Map<number, PhotoRow>();
  for (const a of activities) {
    if (!a.has_gps || !r.chance(0.07)) continue;
    const count = r.int(1, 3);
    for (let k = 0; k < count; k++) {
      const id = photos.size + 1;
      const tall = r.chance(0.3);
      photos.set(id, {
        id,
        activity_id: a.id,
        width: tall ? 1200 : 1600,
        height: tall ? 1600 : 1200,
        caption: r.pick(CAPTIONS),
        source: 'upload',
      });
    }
  }
  syncPhotos(photos);

  // A link already shared, so /s/demo opens in the browser.
  const latestRun = activities.find((a) => a.sport === 'run' && a.has_gps);
  const shares: ShareRow[] = latestRun
    ? [
        {
          id: 1,
          activity_id: latestRun.id,
          token: 'demo',
          show_map: true,
          expires_at: null,
          created_at: at(latestRun, latestRun.duration_s + 3600),
          views: 12,
        },
      ]
    : [];

  return {
    gear,
    photos,
    shares,
    routes,
    segments,
    summaries: new Map<number, SummaryOut>(),
    reports: new Map<number, ReportOut>(),
    /** The last few activities' series (they are made again when asked after). */
    series: new Map<number, Series>(),
    fitness: null as Map<string, number> | null,
    runBests: null as Map<string, Best[]> | null,
    rideBests: null as Map<string, Best[]> | null,
  };
}

function totalGround(pts: [number, number][]) {
  const g = pts.map(ground);
  let up = 0;
  for (let i = 1; i < g.length; i++) up += Math.max(0, (g[i] ?? 0) - (g[i - 1] ?? 0));
  return up;
}

const state = () => db.slice('activities', build);

/** Each activity's photo count and thumbnail, from the photos there are. */
function syncPhotos(photos: Map<number, PhotoRow>) {
  const by = new Map<number, number[]>();
  for (const p of photos.values()) by.set(p.activity_id, [...(by.get(p.activity_id) ?? []), p.id]);
  for (const a of db.activities) {
    const ids = by.get(a.id) ?? [];
    a.photo = ids[0] ?? null;
    a.photos = ids.length;
  }
}

function seriesOf(a: MockActivity): Series {
  const s = state();
  let found = s.series.get(a.id);
  if (!found) {
    found = makeSeries(a);
    s.series.set(a.id, found);
    const oldest = s.series.keys().next().value;
    if (s.series.size > 12 && oldest !== undefined) s.series.delete(oldest);
  }
  return found;
}

/**
 * What is read from each activity's series, kept across resets: the demo data comes back the same
 * after `resetDb` (same seed), so a test or a reload does not work it all out again. The key holds
 * what the series are made from.
 */
const analyses = new Map<string, Analysis>();
const analysisKey = (a: MockActivity) =>
  `${a.id}|${a.start_utc}|${a.sport}|${a.distance_m}|${a.moving_s}|${a.avg_hr}|${a.route ?? ''}`;

function analysisOf(a: MockActivity): Analysis {
  const s = state();
  const key = analysisKey(a);
  let found = analyses.get(key);
  if (!found) {
    found = analyse(a, s.series.get(a.id) ?? makeSeries(a));
    analyses.set(key, found);
  }
  return found;
}

/** Something about an activity changed: what was worked out from it goes. */
function forget(id: number) {
  const s = state();
  for (const key of analyses.keys()) if (key.startsWith(`${id}|`)) analyses.delete(key);
  s.series.delete(id);
  for (const key of efforts.keys()) if (key.startsWith(`${id}|`)) efforts.delete(key);
  s.fitness = null;
  s.runBests = null;
  s.rideBests = null;
}

/** Fitness (CTL, 42-day) at the end of each day, from the first activity to today. */
function fitness(): Map<string, number> {
  const s = state();
  if (s.fitness) return s.fitness;
  const load = new Map<string, number>();
  for (const a of db.activities) load.set(localDay(a), (load.get(localDay(a)) ?? 0) + (trimpOf(a) ?? 0));
  const out = new Map<string, number>();
  const first = db.activities.at(-1);
  let ctl = 0;
  if (first)
    for (let day = localDay(first); day <= db.todayIso; day = addDays(day, 1)) {
      ctl += ((load.get(day) ?? 0) - ctl) / 42;
      out.set(day, round(ctl, 1));
    }
  s.fitness = out;
  return out;
}

type Best = { activity_id: number; value: number; day: string };

/** A run's best efforts: from its analysis when there is one, else from its time and distance only. */
const efforts = new Map<string, BestEffort[]>();
function runEfforts(a: MockActivity): BestEffort[] {
  const key = analysisKey(a);
  let found = analyses.get(key)?.best_efforts ?? efforts.get(key);
  if (!found) {
    found = effortsOf(makeSeries(a, true), a.distance_m ?? 0);
    efforts.set(key, found);
  }
  return found;
}

/** Every run's best efforts, by distance (seconds). */
function runBests(): Map<string, Best[]> {
  const s = state();
  if (s.runBests) return s.runBests;
  const out = new Map<string, Best[]>();
  for (const a of db.activities)
    if (a.sport === 'run' && a.distance_m)
      for (const e of runEfforts(a))
        out.set(e.distance_key, [
          ...(out.get(e.distance_key) ?? []),
          { activity_id: a.id, value: e.time_s, day: localDay(a) },
        ]);
  s.runBests = out;
  return out;
}

/** Every ride's bests: power_… in watts, longest in metres. */
function rideBests(): Map<string, Best[]> {
  const s = state();
  if (s.rideBests) return s.rideBests;
  const out = new Map<string, Best[]>();
  const add = (key: string, a: MockActivity, value: number) =>
    out.set(key, [...(out.get(key) ?? []), { activity_id: a.id, value, day: localDay(a) }]);
  for (const a of db.activities) {
    if (a.sport !== 'ride') continue;
    for (const [d, w] of Object.entries(analysisOf(a).power_bests)) add(`power_${d}`, a, w);
    if (a.distance_m) add('longest', a, a.distance_m);
  }
  s.rideBests = out;
  return out;
}
const lowerIsBetter = (key: string) => !key.startsWith('power_') && key !== 'longest';

/** All-time run bests: the activity holding each distance. */
function records() {
  const out: { key: string; activity_id: number; value: number; day: string }[] = [];
  for (const [key, entries] of runBests()) {
    const best = entries.reduce((x, y) => (y.value < x.value ? y : x));
    out.push({ key, ...best });
  }
  return out;
}

// --- answers ---------------------------------------------------------------------------------------

function detailOf(a: MockActivity): Detail {
  const s = state();
  const x = analysisOf(a);
  const gear = s.gear.find((g) => g.id === a.gear_id);
  const photoList = [...s.photos.values()]
    .filter((p) => p.activity_id === a.id)
    .map(({ id, width, height, caption }) => ({ id, width, height, caption }));
  const group = s.routes.find((g) => g.members.includes(a.id));
  const r = rngOf(a, 3);
  const lats = a.points.map((p) => p[0]);
  const lons = a.points.map((p) => p[1]);
  const month = new Date(a.start_utc).getUTCMonth();
  const temperature = round(15 - 9 * Math.cos(((month + 0.5) / 12) * 2 * Math.PI) + r.between(-3, 3));
  return {
    ...summary(a),
    description: a.description,
    gear: gear ? { id: gear.id, name: gear.name, kind: gear.kind } : null,
    photo_list: photoList,
    weather: a.has_gps
      ? {
          condition: r.pick(['Clear', 'Partly cloudy', 'Cloudy', 'Light rain', 'Sunny']),
          temperature,
          apparent_temperature: temperature - 1,
          humidity: r.int(35, 85),
          wind_speed: round(r.between(1, 6), 1),
          wind_direction: r.int(0, 359),
        }
      : null,
    edited_fields: a.edited_fields,
    original_format: 'fit',
    strava_id: null,
    descent_m: x.descent_m,
    max_hr: x.max_hr,
    normalized_power: x.normalized_power,
    calories: a.calories,
    training_effect: x.metrics.trimp ? round(clamp(1 + x.metrics.trimp / 45, 1, 5), 1) : null,
    device: a.device,
    bbox: a.points.length
      ? [Math.min(...lats), Math.min(...lons), Math.max(...lats), Math.max(...lons)]
      : null,
    has_fit: true,
    imported_at: at(a, a.duration_s + 240),
    laps: x.laps,
    splits: x.splits,
    best_efforts: x.best_efforts,
    sets: setsOf(a),
    metrics: x.metrics,
    route_group: group
      ? {
          group: group.id,
          count: group.members.length,
          rank:
            1 +
            group.members.filter((id) => {
              const o = db.activity(id);
              return o && moving(o) < moving(a);
            }).length,
        }
      : null,
    segments: s.segments.flatMap((seg) => {
      const mine = seg.efforts.find((e) => e.activity_id === a.id);
      if (!mine) return [];
      const times = seg.efforts.map((e) => e.elapsed_s);
      return [
        {
          segment_id: seg.id,
          name: seg.name,
          elapsed_s: mine.elapsed_s,
          best_s: Math.min(...times),
          rank: 1 + times.filter((t) => t < mine.elapsed_s).length,
          count: times.length,
        },
      ];
    }),
    streams: { series: x.series, sample_count: x.sample_count },
  };
}

function streamsOf(a: MockActivity): Streams {
  const s = seriesOf(a);
  return {
    t: s.t,
    lat: s.lat,
    lon: s.lon,
    dist: s.dist,
    alt: s.alt,
    hr: s.hr,
    cad: s.cad,
    speed: s.speed,
    power: s.power,
    temp: s.temp,
  };
}

function contextOf(a: MockActivity): Context {
  const s = state();
  const day = localDay(a);
  const ctl = fitness();
  const trimp = trimpOf(a);
  const since = Date.parse(a.start_utc) - 21 * DAY_MS;
  const before = db.activities.filter(
    (o) => o.start_utc < a.start_utc && Date.parse(o.start_utc) >= since && trimpOf(o),
  );
  const average = before.length ? round(mean(before.map((o) => trimpOf(o) ?? 0)), 1) : null;
  const group = s.routes.find((g) => g.members.includes(a.id));

  // The goals of its sport, as they stood at the end of its day.
  const monthStart = `${day.slice(0, 7)}-01`;
  const yearStart = `${day.slice(0, 4)}-01-01`;
  const upTo = (from: string, sport: string | null) =>
    db.activities.filter(
      (o) => (sport === null || o.sport === sport) && localDay(o) >= from && localDay(o) <= day,
    );
  const goals: Context['goals'] = [];
  if (a.sport === 'run' || a.sport === 'ride') {
    goals.push({
      period: 'month',
      metric: 'distance',
      sport: a.sport,
      target: a.sport === 'run' ? 150_000 : 500_000,
      current: round(upTo(monthStart, a.sport).reduce((t, o) => t + (o.distance_m ?? 0), 0)),
    });
  }
  if (a.sport === 'strength')
    goals.push({
      period: 'month',
      metric: 'count',
      sport: 'strength',
      target: 8,
      current: upTo(monthStart, 'strength').length,
    });
  goals.push({
    period: 'year',
    metric: 'time',
    sport: null,
    target: 400 * 3600,
    current: upTo(yearStart, null).reduce((t, o) => t + moving(o), 0),
  });

  const medals: Context['medals'] = [];
  if (a.sport === 'run' || a.sport === 'ride') {
    for (const [key, entries] of a.sport === 'run' ? runBests() : rideBests()) {
      const mine = entries.find((e) => e.activity_id === a.id);
      if (!mine) continue;
      const lower = lowerIsBetter(key);
      const rank = (pool: typeof entries) =>
        1 + pool.filter((e) => (lower ? e.value < mine.value : e.value > mine.value)).length;
      const overall = rank(entries);
      const yearly = rank(entries.filter((e) => e.day.slice(0, 4) === mine.day.slice(0, 4)));
      if (overall <= 3) medals.push({ key, value: mine.value, rank: overall, scope: 'all' });
      else if (yearly <= 3) medals.push({ key, value: mine.value, rank: yearly, scope: 'year' });
    }
    medals.sort((x, y) => Number(x.scope !== 'all') - Number(y.scope !== 'all') || x.rank - y.rank);
  }

  const x = a.sport === 'ride' ? analysisOf(a) : null;
  const np = x?.normalized_power ?? null;
  // Threshold pace grows with the athlete's fitness over the three years.
  const age = (Date.parse(db.todayIso) - Date.parse(a.start_utc)) / (3 * 365 * DAY_MS);
  const threshold = round(3.45 - 0.25 * clamp(age, 0, 1), 3);
  return {
    fitness: ctl.has(day)
      ? {
          before: ctl.get(addDays(day, -1)) ?? 0,
          after: ctl.get(day) ?? 0,
          points: Array.from({ length: 28 }, (_, i) => addDays(day, i - 27)).flatMap((d) =>
            ctl.has(d) ? [{ day: d, ctl: ctl.get(d) ?? 0 }] : [],
          ),
        }
      : null,
    effort: trimp
      ? {
          score: trimp,
          average,
          status: !average
            ? null
            : trimp < (average * 2) / 3
              ? 'below'
              : trimp <= (average * 4) / 3
                ? 'in'
                : 'above',
        }
      : null,
    goals,
    matches: group
      ? group.members.flatMap((id) => {
          const o = db.activity(id);
          if (!o) return [];
          return [
            {
              activity_id: o.id,
              day: localDay(o),
              speed_mps: o.distance_m ? round(o.distance_m / moving(o), 3) : null,
              moving_s: moving(o),
              relative_effort: trimpOf(o),
              this: o.id === a.id,
            },
          ];
        })
      : [],
    medals,
    segments: s.segments
      .flatMap((seg) => {
        const mine = seg.efforts.find((e) => e.activity_id === a.id);
        if (!mine) return [];
        return [
          {
            segment_id: seg.id,
            name: seg.name,
            distance_m: seg.distance_m,
            elapsed_s: mine.elapsed_s,
            avg_power: mine.avg_power,
            rank: 1 + seg.efforts.filter((e) => e.elapsed_s < mine.elapsed_s).length,
          },
        ];
      })
      .sort((p, q) => p.rank - q.rank || p.name.localeCompare(q.name)),
    power:
      a.avg_power && np
        ? {
            work_kj: Math.round((a.avg_power * moving(a)) / 1000),
            intensity: Math.round((np / FTP) * 100),
            load: Math.round(((moving(a) * np * (np / FTP)) / (FTP * 3600)) * 100),
            ftp: FTP,
          }
        : null,
    pace_zones:
      a.sport === 'run'
        ? {
            threshold_mps: threshold,
            bounds: [0, 0.78, 0.88, 0.95, 1, 1.09].map((f) => round(threshold * f, 3)),
          }
        : null,
    goals_reached: a.goals_reached ?? [],
  };
}

function gearOut(g: GearRow): GearOut {
  const used = db.activities.filter((a) => a.gear_id === g.id);
  const distance = g.initial_m + used.reduce((t, a) => t + (a.distance_m ?? 0), 0);
  return {
    ...g,
    distance_m: round(distance),
    activities: used.length,
    moving_s: used.reduce((t, a) => t + moving(a), 0),
    first_used: used.at(-1)?.start_utc ?? null,
    last_used: used[0]?.start_utc ?? null,
    over_limit: g.limit_m != null && distance >= g.limit_m,
  };
}

const routeActivity = (a: MockActivity): RouteActivity => ({
  id: a.id,
  name: a.name,
  day: localDay(a),
  moving_s: moving(a),
  distance_m: a.distance_m,
  avg_hr: a.avg_hr,
});

function routeGroup(g: RouteRow) {
  const members = g.members.flatMap((id) => db.activity(id) ?? []);
  const [first] = members;
  if (!first) return null;
  const best = members.reduce((x, y) => (moving(y) < moving(x) ? y : x));
  const latest = members.reduce((x, y) => (y.start_utc > x.start_utc ? y : x));
  return {
    group: {
      id: g.id,
      sport: g.sport,
      name: g.name,
      route: first.route,
      count: members.length,
      best: routeActivity(best),
      latest: routeActivity(latest),
    },
    members,
  };
}

/** A share link as listed: the token only in the answer that creates it. */
const shareOut = (row: ShareRow, path: string | null): ShareOut => ({
  id: row.id,
  activity_id: row.activity_id,
  path,
  show_map: row.show_map,
  expires_at: row.expires_at,
  created_at: row.created_at,
  views: row.views,
});

function segmentOut(seg: SegmentRow): SegmentOut {
  const times = seg.efforts.map((e) => e.elapsed_s);
  return {
    id: seg.id,
    name: seg.name,
    track: encode(seg.points, 6),
    distance_m: seg.distance_m,
    ascent_m: seg.ascent_m,
    efforts: times.length,
    best_s: times.length ? Math.min(...times) : null,
  };
}

function sharedOf(a: MockActivity, showMap: boolean, expires: string | null): Shared {
  const s = seriesOf(a);
  const x = analysisOf(a);
  const n = s.t.length;
  const every = Math.max(1, Math.floor(n / 120));
  // The route without the first and last 400 m around home (the privacy zone).
  const stretches: [number, number][][] = [];
  if (showMap && s.lat && s.lon) {
    let current: [number, number][] = [];
    for (let i = 0; i < n; i += every) {
      const p: [number, number] = [s.lat[i] ?? 0, s.lon[i] ?? 0];
      if (metres(p, HOME) < 400) {
        if (current.length > 1) stretches.push(current);
        current = [];
      } else current.push(p);
    }
    if (current.length > 1) stretches.push(current);
  }
  const photos = [...state().photos.values()]
    .filter((p) => p.activity_id === a.id)
    .map(({ id, width, height, caption }) => ({ id, width, height, caption }));
  return {
    name: a.name,
    sport: a.sport,
    sub_sport: a.sub_sport,
    day: localDay(a),
    athlete: db.user.display_name,
    duration_s: a.duration_s,
    moving_s: a.moving_s,
    distance_m: a.distance_m,
    ascent_m: a.ascent_m,
    avg_hr: a.avg_hr,
    avg_power: a.avg_power,
    route: showMap && stretches.length ? stretches : null,
    profile: s.dist
      ? Array.from({ length: Math.ceil(n / every) }, (_, k) => k * every).map((i) => ({
          distance_m: s.dist?.[i] ?? 0,
          altitude_m: s.alt?.[i] ?? null,
          speed: s.speed?.[i] ?? null,
        }))
      : [],
    splits: x.splits.map(({ distance_m, time_s, elevation_change_m }) => ({
      distance_m,
      time_s,
      elevation_change_m,
    })),
    photos,
    expires_at: expires,
  };
}

const pace = (mps: number) => {
  const s = Math.round(1000 / mps);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} /km`;
};
const km = (m: number | null) => `${((m ?? 0) / 1000).toFixed(1)} km`;

function summaryText(a: MockActivity): SummaryOut {
  const v = a.distance_m ? a.distance_m / moving(a) : null;
  const effort = trimpOf(a);
  return {
    activity_id: a.id,
    language: 'en',
    suggested_title:
      a.sport === 'strength' ? 'Steady strength session' : `${a.name ?? 'Session'} with a strong finish`,
    summary: v
      ? `A ${km(a.distance_m)} ${a.sport} at ${a.sport === 'ride' ? `${(v * 3.6).toFixed(1)} km/h` : pace(v)}, with ${a.ascent_m ?? 0} m of climbing. Your heart rate stayed around ${a.avg_hr} bpm and the second half was as even as the first.`
      : `${Math.round(moving(a) / 60)} minutes of work at an average of ${a.avg_hr} bpm: a solid session without pushing into the red.`,
    highlights: [
      effort ? `Relative effort ${Math.round(effort)}` : 'An easy day',
      a.ascent_m ? `${a.ascent_m} m climbed` : `${a.calories} kcal`,
      'Even pacing throughout',
    ],
    model: 'mock-coach',
    created_at: at(a, a.duration_s + 600),
  };
}

function reportText(a: MockActivity): ReportOut {
  const s = summaryText(a);
  return {
    activity_id: a.id,
    language: 'en',
    headline: s.suggested_title,
    effort: `${s.highlights[0]}: about what your last three weeks have averaged.`,
    story: s.summary,
    month: 'This month is going to plan: steady volume and one harder session a week.',
    verdict: 'A good, useful session that adds to your fitness without digging a hole.',
    next: a.sport === 'run' ? 'Tomorrow, 40 minutes easy below 140 bpm.' : 'An easy spin or a rest day next.',
    model: 'mock-coach',
    created_at: at(a, a.duration_s + 900),
  };
}

/** A small landscape, as the photo (an SVG, so no binary has to live in the repository). */
function photoSvg(p: { id: number; width: number; height: number }, thumb: boolean) {
  const scale = thumb ? 0.2 : 1;
  const w = Math.round(p.width * scale);
  const h = Math.round(p.height * scale);
  const hue = (p.id * 47) % 360;
  return new Response(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 100 ${Math.round((100 * p.height) / p.width)}" preserveAspectRatio="none">` +
      `<defs><linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="hsl(${hue},70%,75%)"/><stop offset="1" stop-color="hsl(${(hue + 40) % 360},60%,88%)"/></linearGradient></defs>` +
      `<rect width="100" height="200" fill="url(#s)"/><path d="M0 60 L25 35 L45 52 L70 25 L100 50 L100 200 L0 200 Z" fill="hsl(${(hue + 160) % 360},30%,35%)"/>` +
      `<path d="M0 72 Q50 58 100 70 L100 200 L0 200 Z" fill="hsl(${(hue + 120) % 360},35%,45%)"/></svg>`,
    { headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'private, max-age=31536000' } },
  );
}

const idOf = (value: string | readonly string[] | undefined) => Number(value);
const notFound = () => fail(404, 'Activity not found');

/** The activities the search keeps, as the backend's filters. */
function search(url: URL) {
  const q = url.searchParams;
  const num = (k: string) => (q.get(k) == null || q.get(k) === '' ? null : Number(q.get(k)));
  const yes = (k: string) => q.get(k) === 'true';
  const fold = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const words = fold(q.get('q') ?? '')
    .split(/[^a-z0-9]+/)
    .filter(Boolean);
  const sports = q.getAll('sport');
  const sources = q.getAll('source');
  const from = q.get('from');
  const to = q.get('to');
  const holders = yes('with_record') ? new Set(records().map((r) => r.activity_id)) : null;
  const filters: [string, (a: MockActivity, v: number) => boolean][] = [
    ['min_distance', (a, v) => (a.distance_m ?? -1) >= v],
    ['max_distance', (a, v) => a.distance_m != null && a.distance_m <= v],
    ['min_duration', (a, v) => moving(a) >= v],
    ['max_duration', (a, v) => moving(a) <= v],
    ['min_ascent', (a, v) => (a.ascent_m ?? -1) >= v],
    ['min_hr', (a, v) => (a.avg_hr ?? -1) >= v],
    ['max_hr', (a, v) => a.avg_hr != null && a.avg_hr <= v],
  ];
  return db.activities.filter((a) => {
    if (sports.length && !sports.includes(a.sport)) return false;
    if (sources.length && !sources.includes(a.source)) return false;
    if (from && Date.parse(a.start_utc) < Date.parse(from)) return false;
    if (to && Date.parse(a.start_utc) >= Date.parse(to)) return false;
    for (const [k, keep] of filters) {
      const v = num(k);
      if (v != null && !keep(a, v)) return false;
    }
    if (yes('with_description') && !a.description?.trim()) return false;
    if (yes('with_gps') && !a.has_gps) return false;
    if (holders && !holders.has(a.id)) return false;
    if (words.length) {
      const text = fold(`${a.name ?? ''} ${a.description ?? ''}`).split(/[^a-z0-9]+/);
      if (!words.every((w) => text.some((t) => t.startsWith(w)))) return false;
    }
    return true;
  });
}

const totalsOf = (items: MockActivity[]) => ({
  count: items.length,
  distance_m: round(items.reduce((t, a) => t + (a.distance_m ?? 0), 0)),
  moving_s: items.reduce((t, a) => t + moving(a), 0),
  ascent_m: round(items.reduce((t, a) => t + (a.ascent_m ?? 0), 0)),
});

const cursorOf = (a: MockActivity) => btoa(`${a.start_utc}|${a.id}`).replace(/=+$/, '');

function weekOf(today: string): Week {
  const monday = mondayOf(today);
  const byWeek = new Map<string, MockActivity[]>();
  for (const a of db.activities) {
    const w = mondayOf(localDay(a));
    byWeek.set(w, [...(byWeek.get(w) ?? []), a]);
  }
  const inWeek = (start: string) => byWeek.get(start) ?? [];
  const weekTotals = (start: string) => ({ week_start: start, ...totalsOf(inWeek(start)) });
  let streak = 0;
  for (let w = inWeek(monday).length ? monday : addDays(monday, -7); inWeek(w).length; w = addDays(w, -7))
    streak++;
  const recent = addDays(today, -30);
  return {
    today,
    days: Array.from({ length: 7 }, (_, i) => addDays(monday, i)).map((date) => ({
      date,
      activities: inWeek(monday)
        .filter((a) => localDay(a) === date)
        .reverse()
        .map((a) => ({ id: a.id, sport: a.sport, name: a.name })),
    })),
    this_week: weekTotals(monday),
    previous: Array.from({ length: 12 }, (_, i) => weekTotals(addDays(monday, -7 * (i + 1)))),
    streak_weeks: streak,
    records: records()
      .filter((r) => r.day >= recent)
      .map((r) => ({
        distance_key: r.key,
        distance_m: RUN_DISTANCES.find(([k]) => k === r.key)?.[1] ?? 0,
        time_s: r.value,
        activity_id: r.activity_id,
        date: r.day,
      })),
  };
}

function eventsOf(limit: number): EventOut[] {
  const out: Omit<EventOut, 'id'>[] = [];
  // A record each time a run beat the best so far at a distance (after the first months).
  const runs = db.activities.filter((a) => a.sport === 'run' && a.distance_m).reverse();
  const best = new Map<string, number>();
  const startAfter = Date.parse(db.todayIso) - 2.5 * 365 * DAY_MS;
  for (const a of runs) {
    for (const e of runEfforts(a)) {
      const was = best.get(e.distance_key);
      if (was === undefined || e.time_s < was) {
        best.set(e.distance_key, e.time_s);
        if (was !== undefined && Date.parse(a.start_utc) > startAfter)
          out.push({
            kind: 'record',
            payload: { distance_key: e.distance_key, time_s: e.time_s, activity_id: a.id },
            created_at: at(a, a.duration_s + 300),
          });
      }
    }
  }
  const latest = db.activities[0];
  if (latest) {
    const ago = (days: number) => new Date(Date.parse(latest.start_utc) - days * DAY_MS).toISOString();
    out.push(
      { kind: 'estimate_up', payload: { kind: 'vdot', value: 51.8 }, created_at: ago(9) },
      { kind: 'estimate_up', payload: { kind: 'ftp', value: FTP }, created_at: ago(24) },
      { kind: 'ramp', payload: { ramp: 6 }, created_at: ago(40) },
    );
  }
  return out
    .sort((x, y) => y.created_at.localeCompare(x.created_at))
    .slice(0, Math.min(limit, 100))
    .map((e, i) => ({ id: 1000 - i, ...e }));
}

// --- handlers --------------------------------------------------------------------------------------

export const activitiesHandlers = [
  // The feed, by pages, newest first.
  route('get', '/api/activities', ({ request }) => {
    state();
    const url = new URL(request.url);
    const limit = clamp(Number(url.searchParams.get('limit') ?? 30) || 30, 1, 100);
    const sports = url.searchParams.getAll('sport');
    let items = db.activities.filter((a) => !sports.length || sports.includes(a.sport));
    const cursor = url.searchParams.get('cursor');
    if (cursor) {
      const [start, id] = atob(cursor).split('|');
      items = items.filter(
        (a) => a.start_utc < (start ?? '') || (a.start_utc === start && a.id < Number(id)),
      );
    }
    const page = items.slice(0, limit);
    const last = page.at(-1);
    return json('/api/activities', 'get', {
      items: page.map(summary),
      next_cursor: items.length > limit && last ? cursorOf(last) : null,
    });
  }),

  route('get', '/api/activities/search', ({ request }) => {
    state();
    const url = new URL(request.url);
    const found = search(url);
    const sort = url.searchParams.get('sort') ?? 'date';
    const asc = url.searchParams.get('order') === 'asc';
    const key = (a: MockActivity): number | null =>
      sort === 'distance'
        ? a.distance_m
        : sort === 'duration'
          ? moving(a)
          : sort === 'elevation'
            ? a.ascent_m
            : sort === 'speed'
              ? a.distance_m && a.distance_m / moving(a)
              : Date.parse(a.start_utc);
    const sorted = [...found].sort((x, y) => {
      const kx = key(x);
      const ky = key(y);
      if (kx == null || ky == null) return kx == null ? (ky == null ? y.id - x.id : 1) : -1;
      return (asc ? kx - ky : ky - kx) || y.id - x.id;
    });
    const offset = Number(url.searchParams.get('offset') ?? 0) || 0;
    const limit = clamp(Number(url.searchParams.get('limit') ?? 50) || 50, 1, 100);
    return json('/api/activities/search', 'get', {
      items: sorted.slice(offset, offset + limit).map(summary),
      totals: totalsOf(found),
      next_offset: sorted.length > offset + limit ? offset + limit : null,
    });
  }),

  route('get', '/api/activities/months', ({ request }) => {
    state();
    const sports = new URL(request.url).searchParams.getAll('sport');
    const months = new Map<string, MockActivity[]>();
    for (const a of db.activities) {
      if (sports.length && !sports.includes(a.sport)) continue;
      const m = localDay(a).slice(0, 7);
      months.set(m, [...(months.get(m) ?? []), a]);
    }
    return json(
      '/api/activities/months',
      'get',
      [...months].map(([month, items]) => ({ month, ...totalsOf(items) })),
    );
  }),

  route('get', '/api/activities/{activity_id}', ({ params }) => {
    state();
    const a = db.activity(idOf(params.activity_id));
    return a ? json('/api/activities/{activity_id}', 'get', detailOf(a)) : notFound();
  }),

  route('patch', '/api/activities/{activity_id}', async ({ params, request }) => {
    state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const body = (await request.json()) as Req<'/api/activities/{activity_id}', 'patch'>;
    const edited = new Set(a.edited_fields);
    if (body.name !== undefined) a.name = body.name;
    if (body.description !== undefined) a.description = body.description;
    if (body.sport != null && body.sport !== a.sport) {
      a.sport = body.sport;
      forget(a.id);
    }
    if (body.gear_id !== undefined) a.gear_id = body.gear_id === 0 ? null : body.gear_id;
    if (body.workout_type !== undefined) a.workout_type = body.workout_type;
    for (const k of Object.keys(body)) edited.add(k);
    a.edited_fields = [...edited];
    return json('/api/activities/{activity_id}', 'patch', {
      id: a.id,
      name: a.name,
      description: a.description,
      sport: a.sport,
      gear_id: a.gear_id,
      workout_type: a.workout_type ?? null,
      edited_fields: a.edited_fields,
    });
  }),

  route('delete', '/api/activities/{activity_id}', ({ params }) => {
    const s = state();
    const id = idOf(params.activity_id);
    const i = db.activities.findIndex((a) => a.id === id);
    if (i < 0) return notFound();
    db.activities.splice(i, 1);
    forget(id);
    for (const g of s.routes) g.members = g.members.filter((m) => m !== id);
    s.routes = s.routes.filter((g) => g.members.length > 1 && db.activity(g.id));
    for (const seg of s.segments) seg.efforts = seg.efforts.filter((e) => e.activity_id !== id);
    for (const p of [...s.photos.values()]) if (p.activity_id === id) s.photos.delete(p.id);
    s.shares = s.shares.filter((x) => x.activity_id !== id);
    return new Response(null, { status: 204 });
  }),

  route('get', '/api/activities/{activity_id}/streams', ({ params }) => {
    state();
    const a = db.activity(idOf(params.activity_id));
    return a ? json('/api/activities/{activity_id}/streams', 'get', streamsOf(a)) : notFound();
  }),

  route('get', '/api/activities/{activity_id}/context', ({ params }) => {
    state();
    const a = db.activity(idOf(params.activity_id));
    return a ? json('/api/activities/{activity_id}/context', 'get', contextOf(a)) : notFound();
  }),

  // The original file: a tiny stand-in with a FIT header, enough for the download to work.
  route('get', '/api/activities/{activity_id}/fit', ({ params }) => {
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const bytes = new Uint8Array([14, 0x20, 0x08, 0x08, 0, 0, 0, 0, 0x2e, 0x46, 0x49, 0x54, 0, 0]);
    return new Response(bytes, {
      headers: {
        'Content-Type': 'application/vnd.ant.fit',
        'Content-Disposition': `attachment; filename="zankup-${a.id}.fit"`,
      },
    });
  }),

  // The Coach's texts: none until asked for, except on the latest three activities.
  route('get', '/api/activities/{activity_id}/summary', ({ params }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const found = s.summaries.get(a.id) ?? (db.activities.indexOf(a) < 3 ? summaryText(a) : null);
    return found ? json('/api/activities/{activity_id}/summary', 'get', found) : fail(404, 'No summary yet');
  }),
  route('post', '/api/activities/{activity_id}/summary', ({ params }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const made = { ...summaryText(a), created_at: new Date().toISOString() };
    s.summaries.set(a.id, made);
    return json('/api/activities/{activity_id}/summary', 'post', made);
  }),
  route('get', '/api/activities/{activity_id}/report', ({ params }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const found = s.reports.get(a.id);
    return found ? json('/api/activities/{activity_id}/report', 'get', found) : fail(404, 'No report yet');
  }),
  route('post', '/api/activities/{activity_id}/report', ({ params }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const made = { ...reportText(a), created_at: new Date().toISOString() };
    s.reports.set(a.id, made);
    return json('/api/activities/{activity_id}/report', 'post', made);
  }),

  // Gear.
  route('get', '/api/gear', () => json('/api/gear', 'get', state().gear.map(gearOut))),
  route('post', '/api/gear', async ({ request }) => {
    const s = state();
    const body = (await request.json()) as Req<'/api/gear', 'post'>;
    const kind = body.kind ?? 'shoe';
    const row: GearRow = {
      id: Math.max(0, ...s.gear.map((g) => g.id)) + 1,
      kind,
      name: body.name,
      brand: body.brand ?? null,
      model: body.model ?? null,
      sports: body.sports ?? [],
      initial_m: body.initial_m ?? 0,
      limit_m: body.limit_m ?? null,
      retired: false,
      habitual: body.habitual ?? !s.gear.some((g) => g.kind === kind && !g.retired),
    };
    if (row.habitual) for (const g of s.gear) if (g.kind === kind) g.habitual = false;
    s.gear.push(row);
    return json('/api/gear', 'post', gearOut(row), { status: 201 });
  }),
  route('patch', '/api/gear/{gear_id}', async ({ params, request }) => {
    const s = state();
    const g = s.gear.find((x) => x.id === idOf(params.gear_id));
    if (!g) return fail(404, 'Gear not found');
    const body = (await request.json()) as Req<'/api/gear/{gear_id}', 'patch'>;
    for (const [k, v] of Object.entries(body))
      if (v !== null && v !== undefined) Object.assign(g, { [k]: v });
    if ('limit_m' in body) g.limit_m = body.limit_m ?? null;
    if ('brand' in body) g.brand = body.brand ?? null;
    if ('model' in body) g.model = body.model ?? null;
    if (body.habitual) for (const o of s.gear) if (o !== g && o.kind === g.kind) o.habitual = false;
    if (body.retired) g.habitual = false;
    return json('/api/gear/{gear_id}', 'patch', gearOut(g));
  }),
  route('delete', '/api/gear/{gear_id}', ({ params }) => {
    const s = state();
    const id = idOf(params.gear_id);
    s.gear = s.gear.filter((g) => g.id !== id);
    for (const a of db.activities) if (a.gear_id === id) a.gear_id = null;
    return new Response(null, { status: 204 });
  }),

  // Photos.
  route('post', '/api/activities/{activity_id}/photos', ({ params }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const id = Math.max(0, ...s.photos.keys()) + 1;
    const photo: PhotoRow = {
      id,
      activity_id: a.id,
      width: 1600,
      height: 1200,
      caption: null,
      source: 'upload',
    };
    s.photos.set(id, photo);
    syncPhotos(s.photos);
    return json('/api/activities/{activity_id}/photos', 'post', photo, { status: 201 });
  }),
  route('get', '/api/photos/{photo_id}', ({ params, request }) => {
    const p = state().photos.get(idOf(params.photo_id));
    return p
      ? photoSvg(p, new URL(request.url).searchParams.get('thumb') === 'true')
      : fail(404, 'Photo not found');
  }),
  route('delete', '/api/photos/{photo_id}', ({ params }) => {
    const s = state();
    s.photos.delete(idOf(params.photo_id));
    syncPhotos(s.photos);
    return new Response(null, { status: 204 });
  }),

  // Share links.
  route('get', '/api/activities/{activity_id}/shared', ({ params, request }) => {
    state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const showMap = new URL(request.url).searchParams.get('show_map') !== 'false';
    return json('/api/activities/{activity_id}/shared', 'get', sharedOf(a, showMap, null));
  }),
  route('get', '/api/activities/{activity_id}/shares', ({ params }) => {
    const id = idOf(params.activity_id);
    return json(
      '/api/activities/{activity_id}/shares',
      'get',
      state()
        .shares.filter((x) => x.activity_id === id)
        .map((x) => shareOut(x, null)),
    );
  }),
  route('post', '/api/activities/{activity_id}/shares', async ({ params, request }) => {
    const s = state();
    const a = db.activity(idOf(params.activity_id));
    if (!a) return notFound();
    const body = (await request.json()) as Req<'/api/activities/{activity_id}/shares', 'post'>;
    const id = Math.max(0, ...s.shares.map((x) => x.id)) + 1;
    const token = `mock${id}${Math.random().toString(36).slice(2, 10)}`;
    const row: ShareRow = {
      id,
      activity_id: a.id,
      token,
      show_map: body.show_map ?? true,
      expires_at: body.days ? new Date(Date.now() + body.days * DAY_MS).toISOString() : null,
      created_at: new Date().toISOString(),
      views: 0,
    };
    s.shares.push(row);
    return json('/api/activities/{activity_id}/shares', 'post', shareOut(row, `/s/${token}`), {
      status: 201,
    });
  }),
  route('delete', '/api/shares/{share_id}', ({ params }) => {
    const s = state();
    s.shares = s.shares.filter((x) => x.id !== idOf(params.share_id));
    return new Response(null, { status: 204 });
  }),
  route('get', '/api/public/shares/{token}', ({ params }) => {
    const row = state().shares.find((x) => x.token === params.token);
    const a = row && db.activity(row.activity_id);
    if (!row || !a || (row.expires_at && Date.parse(row.expires_at) < Date.now()))
      return fail(404, 'This link has expired or does not exist');
    row.views += 1;
    return json('/api/public/shares/{token}', 'get', sharedOf(a, row.show_map, row.expires_at));
  }),
  route('get', '/api/public/shares/{token}/photos/{photo_id}', ({ params, request }) => {
    const s = state();
    const row = s.shares.find((x) => x.token === params.token);
    const p = s.photos.get(idOf(params.photo_id));
    if (!row || !p || p.activity_id !== row.activity_id) return fail(404, 'Photo not found');
    return photoSvg(p, new URL(request.url).searchParams.get('thumb') === 'true');
  }),

  // Routes and segments.
  route('get', '/api/routes', () =>
    json(
      '/api/routes',
      'get',
      state().routes.flatMap((g) => routeGroup(g)?.group ?? []),
    ),
  ),
  route('get', '/api/routes/{group_id}', ({ params }) => {
    const g = state().routes.find((x) => x.id === idOf(params.group_id));
    const found = g && routeGroup(g);
    if (!found) return fail(404, 'Route not found');
    const { group, members } = found;
    return json('/api/routes/{group_id}', 'get', { ...group, activities: members.map(routeActivity) });
  }),
  route('get', '/api/segments', () => json('/api/segments', 'get', state().segments.map(segmentOut))),
  route('post', '/api/segments', async ({ request }) => {
    const s = state();
    const body = (await request.json()) as Req<'/api/segments', 'post'>;
    const a = db.activity(body.activity_id);
    if (!a) return notFound();
    const series = seriesOf(a);
    if (!series.lat || !series.lon || !series.dist) return fail(422, 'This activity has no route');
    const i0 = clamp(body.start_index, 0, series.t.length - 1);
    const i1 = clamp(body.end_index, i0 + 1, series.t.length - 1);
    const alt = series.alt ?? [];
    const lon = series.lon;
    let up = 0;
    for (let i = i0 + 1; i <= i1; i++) up += Math.max(0, (alt[i] ?? 0) - (alt[i - 1] ?? 0));
    const hr = series.hr.slice(i0, i1 + 1);
    const seg: SegmentRow = {
      id: Math.max(0, ...s.segments.map((x) => x.id)) + 1,
      name: body.name,
      points: series.lat.slice(i0, i1 + 1).map((lat, k) => [lat, lon[i0 + k] ?? 0]),
      distance_m: round((series.dist[i1] ?? 0) - (series.dist[i0] ?? 0), 1),
      ascent_m: round(up, 1),
      efforts: [
        {
          activity_id: a.id,
          elapsed_s: (series.t[i1] ?? 0) - (series.t[i0] ?? 0),
          avg_hr: Math.round(mean(hr)),
          avg_power: series.power ? Math.round(mean(series.power.slice(i0, i1 + 1))) : null,
        },
      ],
    };
    s.segments.push(seg);
    return json('/api/segments', 'post', segmentOut(seg), { status: 201 });
  }),
  route('get', '/api/segments/{segment_id}', ({ params }) => {
    const seg = state().segments.find((x) => x.id === idOf(params.segment_id));
    if (!seg) return fail(404, 'Segment not found');
    const history = seg.efforts
      .flatMap((e) => {
        const a = db.activity(e.activity_id);
        return a ? [{ ...e, activity_name: a.name, day: localDay(a) }] : [];
      })
      .sort((x, y) => x.day.localeCompare(y.day));
    return json('/api/segments/{segment_id}', 'get', { ...segmentOut(seg), history });
  }),
  route('delete', '/api/segments/{segment_id}', ({ params }) => {
    const s = state();
    s.segments = s.segments.filter((x) => x.id !== idOf(params.segment_id));
    return new Response(null, { status: 204 });
  }),

  // This week, and what the engine noticed.
  route('get', '/api/week', ({ request }) => {
    state();
    const today = new URL(request.url).searchParams.get('today') || db.todayIso;
    return json('/api/week', 'get', weekOf(today));
  }),
  route('get', '/api/events', ({ request }) => {
    state();
    const limit = Number(new URL(request.url).searchParams.get('limit') ?? 20) || 20;
    return json('/api/events', 'get', eventsOf(limit));
  }),
];
