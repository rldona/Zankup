/**
 * Mock handlers: progress and stats (`/api/stats*`), goals, the engine and the data sources. Every
 * figure is derived from the demo athlete's activities (progress-model.ts), so the screens agree with
 * the feed: totals, volume, fitness, zones, curves, bests and medals, estimates, the review…
 */
import { db } from '../db';
import { fail, json, route, type Req, type Res } from '../route';
import {
  add,
  bucketOf,
  DURATIONS,
  emptyTotals,
  memo,
  model,
  monthOf,
  nextBucket,
  nextMonth,
  plusDays,
  RUN_DISTANCES,
  mondayOf,
  within,
  type Computed,
  type Model,
  type Totals,
  type Unit,
} from './progress-model';

type Best = Res<'/api/stats/bests', 'get'>[number];
type Medal = Res<'/api/stats/bests/recent', 'get'>[number];
type RecordOut = Res<'/api/stats/records', 'get'>['all_time'][number];
type Goal = {
  id: number;
  period: 'week' | 'month' | 'year';
  metric: Req<'/api/goals', 'post'>['metric'];
  sport: string | null;
  target: number;
};

/** `from` (inclusive) and `to` (exclusive); without them, the last 365 days. */
function range(q: URLSearchParams) {
  const to = q.get('to') ?? plusDays(db.todayIso, 1);
  return { from: q.get('from') ?? plusDays(to, -365), to };
}
const sports = (q: URLSearchParams) => q.getAll('sport');
const startOf = (c: Computed) => c.a.start_utc;

// --- bests and medals ------------------------------------------------------------------------------

const POWER_BESTS = [5, 15, 30, 60, 300, 600, 1200, 3600];
const BEST_KEYS = [...Object.keys(RUN_DISTANCES), ...POWER_BESTS.map((d) => `power_${d}`), 'longest'];
const lowerIsBetter = (key: string) => key in RUN_DISTANCES;

function bests(m: Model, key: string): Best[] {
  const out: Best[] = [];
  for (const c of m.list) {
    let value: number | undefined;
    if (key in RUN_DISTANCES) value = c.a.sport === 'run' ? c.efforts.get(key) : undefined;
    else if (key === 'longest') value = c.a.sport === 'ride' ? (c.a.distance_m ?? undefined) : undefined;
    else value = c.a.sport === 'ride' ? c.power?.get(Number(key.slice(6))) : undefined;
    if (value)
      out.push({ activity_id: c.a.id, name: c.a.name, day: c.day, value: Math.round(value * 100) / 100 });
  }
  return out;
}

function medalOf(entries: Best[], entry: Best, lower: boolean): [number, 'all' | 'year'] | null {
  const rank = (pool: Best[]) =>
    1 + pool.filter((b) => (lower ? b.value < entry.value : b.value > entry.value)).length;
  const overall = rank(entries);
  if (overall <= 3) return [overall, 'all'];
  const yearly = rank(entries.filter((b) => b.day.slice(0, 4) === entry.day.slice(0, 4)));
  return yearly <= 3 ? [yearly, 'year'] : null;
}

/** Each run record of all time, in order, each time it was beaten. */
function progression(m: Model) {
  const all = new Map<string, RecordOut>();
  const steps = new Map<string, RecordOut[]>();
  for (const c of m.list)
    for (const [key, time_s] of c.efforts) {
      const prev = all.get(key);
      if (prev && prev.time_s <= time_s) continue;
      const rec = {
        distance_key: key,
        distance_m: RUN_DISTANCES[key] ?? 0,
        time_s,
        activity_id: c.a.id,
        day: c.day,
      };
      all.set(key, rec);
      steps.set(key, [...(steps.get(key) ?? []), rec]);
    }
  return { all, steps };
}

// --- goals -----------------------------------------------------------------------------------------

const goals = () =>
  db.slice('progress:goals', () => ({
    next: 5,
    items: [
      { id: 1, period: 'week', metric: 'distance', sport: 'run', target: 30_000 },
      { id: 2, period: 'week', metric: 'count', sport: null, target: 5 },
      { id: 3, period: 'month', metric: 'time', sport: null, target: 20 * 3600 },
      { id: 4, period: 'year', metric: 'distance', sport: 'ride', target: 3_000_000 },
    ] as Goal[],
  }));

function goalOut(goal: Goal): Res<'/api/goals', 'get'>[number] {
  const today = db.todayIso;
  const start = bucketOf(today, goal.period);
  const end = nextBucket(start, goal.period);
  const amount = (c: Computed) =>
    goal.metric === 'distance'
      ? (c.a.distance_m ?? 0)
      : goal.metric === 'time'
        ? c.moving
        : goal.metric === 'ascent'
          ? (c.a.ascent_m ?? 0)
          : 1;
  const current = within(model(), start, end, goal.sport ? [goal.sport] : undefined).reduce(
    (s, c) => s + amount(c),
    0,
  );
  const days = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);
  return {
    ...goal,
    start,
    end,
    current,
    projection: (current * days(start, end)) / (days(start, today) + 1),
  };
}

function goalBody(body: Req<'/api/goals', 'post'>): Omit<Goal, 'id'> | null {
  const ok =
    ['week', 'month', 'year'].includes(body.period) &&
    ['distance', 'time', 'ascent', 'count'].includes(body.metric) &&
    typeof body.target === 'number' &&
    body.target > 0;
  return ok
    ? { period: body.period, metric: body.metric, sport: body.sport ?? null, target: body.target }
    : null;
}

// --- the engine ------------------------------------------------------------------------------------

const engine = () =>
  db.slice('progress:engine', () => ({ running: false, done: 0, total: 0, reason: null as string | null }));

function engineStatus(): Res<'/api/engine', 'get'> {
  const e = engine();
  const activities = db.activities.length;
  if (e.running) {
    // Each look finds it further along, as a recomputation in the background would be.
    e.done = Math.min(e.total, e.done + Math.ceil(e.total / 4));
    if (e.done >= e.total) e.running = false;
  }
  return {
    running: e.running,
    reason: e.reason,
    done: e.done,
    total: e.total,
    computed: e.running ? e.done : activities,
    activities,
    metrics_version: 5,
  };
}

// --- the comparison with Strava --------------------------------------------------------------------

function rSquared(ours: number[], theirs: number[]) {
  if (theirs.length < 3) return null;
  const mean = theirs.reduce((s, v) => s + v, 0) / theirs.length;
  const total = theirs.reduce((s, v) => s + (v - mean) ** 2, 0);
  if (!total) return null;
  return 1 - ours.reduce((s, o, i) => s + (o - (theirs[i] ?? 0)) ** 2, 0) / total;
}

function leastSquares(rows: number[][], targets: number[]): number[] | null {
  const n = rows[0]?.length ?? 0;
  if (rows.length <= n || !n) return null;
  // The normal equations as an n × (n + 1) matrix, solved by Gauss-Jordan elimination.
  const w = n + 1;
  const a = new Array<number>(n * w).fill(0);
  const at = (r: number, c: number) => a[r * w + c] ?? 0;
  rows.forEach((row, k) => {
    for (let i = 0; i < n; i++) {
      for (let j = 0; j < n; j++) a[i * w + j] = at(i, j) + (row[i] ?? 0) * (row[j] ?? 0);
      a[i * w + n] = at(i, n) + (row[i] ?? 0) * (targets[k] ?? 0);
    }
  });
  for (let col = 0; col < n; col++) {
    let pivot = col;
    for (let r = col + 1; r < n; r++) if (Math.abs(at(r, col)) > Math.abs(at(pivot, col))) pivot = r;
    if (Math.abs(at(pivot, col)) < 1e-12) return null;
    for (let c = 0; c < w; c++) {
      const t = at(col, c);
      a[col * w + c] = at(pivot, c);
      a[pivot * w + c] = t;
    }
    for (let r = 0; r < n; r++) {
      if (r === col) continue;
      const f = at(r, col) / at(col, col);
      for (let c = 0; c < w; c++) a[r * w + c] = at(r, c) - f * at(col, c);
    }
  }
  return Array.from({ length: n }, (_, i) => at(i, n) / at(i, i));
}

function comparison(m: Model): Res<'/api/stats/strava-comparison', 'get'> {
  // Every third activity came with a Strava export, its figures a little off ours.
  const wobble = (id: number, k: number) => 0.88 + ((id * 37 + k * 11) % 25) / 100;
  const imported = m.list.filter((c) => c.a.id % 3 === 0 && c.a.avg_hr);
  const pairs = {
    effort: imported.map((c) => [c.a.id, c.load, Math.round(c.load * wobble(c.a.id, 1))] as const),
    calories: imported.map(
      (c) => [c.a.id, c.a.calories, Math.round(c.a.calories * wobble(c.a.id, 2))] as const,
    ),
    grade_adjusted_distance: imported
      .filter((c) => c.a.sport === 'run' && c.a.distance_m)
      .map((c) => {
        const ours = (c.a.distance_m ?? 0) + (c.a.ascent_m ?? 0) * 6;
        return [c.a.id, ours, Math.round(ours * (0.98 + (c.a.id % 5) / 100))] as const;
      }),
  };
  const metrics = (Object.keys(pairs) as (keyof typeof pairs)[]).map((metric) => {
    const rows = pairs[metric];
    const ours = rows.map((r) => r[1]);
    const theirs = rows.map((r) => r[2]);
    const sum = (v: number[]) => v.reduce((s, x) => s + x, 0);
    const points = rows.map(([activity_id, o, s]) => ({
      activity_id,
      ours: Math.round(o * 100) / 100,
      strava: s,
    }));
    return {
      metric,
      count: rows.length,
      r2: rSquared(ours, theirs),
      mae: rows.length ? sum(rows.map((r) => Math.abs(r[1] - r[2]))) / rows.length : null,
      ratio: rows.length && sum(theirs) ? sum(ours) / sum(theirs) : null,
      points: points.slice(0, 1000),
      largest: [...points]
        .sort((x, y) => Math.abs(y.ours - y.strava) - Math.abs(x.ours - x.strava))
        .slice(0, 10),
    };
  });
  const zoneRows = imported.map((c) => c.hrZones.map((s) => s / 60));
  const targets = pairs.effort.map((r) => r[2]);
  const coefficients = zoneRows.length >= 20 ? leastSquares(zoneRows, targets) : null;
  return {
    metrics,
    effort_scale: coefficients
      ? {
          coefficients: coefficients.map((c) => Math.round(c * 10_000) / 10_000),
          r2: rSquared(
            zoneRows.map((r) => r.reduce((s, x, i) => s + x * (coefficients[i] ?? 0), 0)),
            targets,
          ),
          count: zoneRows.length,
        }
      : null,
  };
}

// --- the review ------------------------------------------------------------------------------------

const GEAR: Record<number, { name: string; kind: string }> = {
  1: { name: 'Running shoes', kind: 'shoe' },
  2: { name: 'Road bike', kind: 'bike' },
};

function review(m: Model, period: 'month' | 'year', value: string): Res<'/api/stats/review', 'get'> | null {
  if (!value) value = period === 'year' ? m.today.slice(0, 4) : m.today.slice(0, 7);
  const ok = period === 'year' ? /^\d{4}$/.test(value) : /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
  if (!ok) return null;
  const start = period === 'year' ? `${value}-01-01` : `${value}-01`;
  const unit: Unit = period === 'year' ? 'year' : 'month';
  const end = nextBucket(start, unit);
  const before = period === 'year' ? `${Number(value) - 1}-01-01` : monthOf(plusDays(start, -1));
  const rows = within(m, start, end);
  const totals = emptyTotals();
  const previous = emptyTotals();
  const bySport: Record<string, Totals> = {};
  const days = new Set<string>();
  const step: Unit = period === 'month' ? 'day' : 'month';
  const buckets = new Map<string, Res<'/api/stats/review', 'get'>['buckets'][number]>();
  for (let d = start; d < end; d = nextBucket(d, step))
    buckets.set(d, { start: d, distance_m: 0, moving_s: 0, count: 0 });
  for (const c of rows) {
    add(totals, c);
    add((bySport[c.a.sport] ??= emptyTotals()), c);
    days.add(c.day);
    const b = buckets.get(period === 'month' ? c.day : monthOf(c.day));
    if (!b) continue;
    b.distance_m += c.a.distance_m ?? 0;
    b.moving_s += c.moving;
    b.count += 1;
  }
  for (const c of within(m, before, start)) add(previous, c);
  let streak = 0;
  let best = 0;
  let last: string | null = null;
  for (const d of [...days].sort()) {
    streak = last && plusDays(last, 1) === d ? streak + 1 : 1;
    best = Math.max(best, streak);
    last = d;
  }
  const top = (key: (c: Computed) => number) => {
    const found = rows.filter(key).sort((x, y) => key(y) - key(x))[0];
    return found
      ? {
          id: found.a.id,
          name: found.a.name,
          sport: found.a.sport,
          day: found.day,
          distance_m: found.a.distance_m,
          moving_s: found.moving,
          ascent_m: found.a.ascent_m,
          load: found.load,
        }
      : null;
  };
  // All-time records set in the period, each with the one it beat.
  const records = new Map<string, Res<'/api/stats/review', 'get'>['records'][number]>();
  for (const [key, steps] of progression(m).steps)
    steps.forEach((rec, i) => {
      const beaten = steps[i - 1];
      if (rec.day >= start && rec.day < end && beaten)
        records.set(key, {
          distance_key: key,
          time_s: rec.time_s,
          activity_id: rec.activity_id,
          day: rec.day,
          previous_s: records.get(key)?.previous_s ?? beaten.time_s,
        });
    });
  const gear = new Map<number, { distance_m: number; count: number }>();
  for (const c of rows)
    if (c.a.gear_id) {
      const g = gear.get(c.a.gear_id) ?? { distance_m: 0, count: 0 };
      g.distance_m += c.a.distance_m ?? 0;
      g.count += 1;
      gear.set(c.a.gear_id, g);
    }
  return {
    period,
    start,
    end,
    totals,
    previous,
    by_sport: bySport,
    active_days: days.size,
    longest_streak: best,
    longest: top((c) => c.a.distance_m ?? 0),
    hardest: top((c) => c.load),
    hilliest: top((c) => c.a.ascent_m ?? 0),
    records: [...records.values()].sort((x, y) => x.time_s - y.time_s),
    gear: [...gear]
      .sort((x, y) => y[1].distance_m - x[1].distance_m)
      .slice(0, 3)
      .flatMap(([id, use]) => (GEAR[id] ? [{ id, ...GEAR[id], ...use }] : [])),
    buckets: [...buckets.values()],
    photos: rows.reduce((s, c) => s + c.a.photos, 0),
  };
}

// --- the handlers ----------------------------------------------------------------------------------

export const progressHandlers = [
  route('get', '/api/stats/fitness', ({ request }) =>
    json(
      '/api/stats/fitness',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        return m.fitness.filter((d) => d.day >= from && d.day < to);
      }),
    ),
  ),

  route('get', '/api/stats/summary', ({ request }) =>
    json(
      '/api/stats/summary',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        const current = emptyTotals();
        const previous = emptyTotals();
        const bySport: Record<string, Totals> = {};
        for (const c of within(m, from, to, sports(q))) {
          add(current, c);
          add((bySport[c.a.sport] ??= emptyTotals()), c);
        }
        for (const c of within(m, plusDays(from, -365), plusDays(to, -365), sports(q))) add(previous, c);
        return { current, previous, by_sport: bySport };
      }),
    ),
  ),

  route('get', '/api/stats/volume', ({ request }) => {
    const unit = (new URL(request.url).searchParams.get('unit') ?? 'week') as Unit;
    if (!['day', 'week', 'month', 'year'].includes(unit)) return fail(422, 'Unknown unit');
    return json(
      '/api/stats/volume',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        const buckets = new Map<string, Res<'/api/stats/volume', 'get'>[number]>();
        for (let d = bucketOf(from, unit); d < to; d = nextBucket(d, unit))
          buckets.set(d, { start: d, totals: emptyTotals(), by_sport: {} });
        for (const c of within(m, from, to, sports(q))) {
          const b = buckets.get(bucketOf(c.day, unit));
          if (!b) continue;
          add(b.totals, c);
          add((b.by_sport[c.a.sport] ??= emptyTotals()), c);
        }
        return [...buckets.values()];
      }),
    );
  }),

  route('get', '/api/stats/curves', ({ request }) =>
    json(
      '/api/stats/curves',
      'get',
      memo(request, (m, q) => {
        const kind = q.get('kind') ?? 'power';
        const { from, to } = range(q);
        const best = new Map<number, Res<'/api/stats/curves', 'get'>[number]>();
        for (const c of within(m, from, to, sports(q))) {
          const curve = kind === 'speed' ? c.speed : kind === 'hr' ? c.hr : c.power;
          for (const [duration_s, value] of curve ?? [])
            if (value > (best.get(duration_s)?.value ?? -Infinity))
              best.set(duration_s, { duration_s, value, activity_id: c.a.id, day: c.day });
        }
        return DURATIONS.flatMap((d) => best.get(d) ?? []);
      }),
    ),
  ),

  route('get', '/api/stats/records', ({ request }) =>
    json(
      '/api/stats/records',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        const { all, steps } = progression(m);
        const period = new Map<string, RecordOut>();
        for (const c of within(m, from, to, ['run']))
          for (const [key, time_s] of c.efforts)
            if ((period.get(key)?.time_s ?? Infinity) > time_s)
              period.set(key, {
                distance_key: key,
                distance_m: RUN_DISTANCES[key] ?? 0,
                time_s,
                activity_id: c.a.id,
                day: c.day,
              });
        const order = (x: RecordOut, y: RecordOut) => x.distance_m - y.distance_m;
        return {
          all_time: [...all.values()].sort(order),
          period: [...period.values()].sort(order),
          progression: Object.fromEntries(steps),
        };
      }),
    ),
  ),

  route('get', '/api/stats/estimates', () => json('/api/stats/estimates', 'get', model().estimates)),

  route('get', '/api/stats/progress', ({ request }) =>
    json(
      '/api/stats/progress',
      'get',
      memo(request, (m, q) => {
        const years = Math.min(20, Math.max(1, Number(q.get('years') ?? 5)));
        const thisYear = Number(m.today.slice(0, 4));
        const out: Res<'/api/stats/progress', 'get'> = [];
        for (let year = thisYear - years + 1; year <= thisYear; year++) {
          const first = `${year}-01-01`;
          const end = year === thisYear ? plusDays(m.today, 1) : `${year + 1}-01-01`;
          const perDay = new Map<string, number>();
          for (const c of within(m, first, end, sports(q)))
            perDay.set(c.day, (perDay.get(c.day) ?? 0) + (c.a.distance_m ?? 0));
          let total = 0;
          const cumulative: number[] = [];
          for (let d = first; d < end; d = plusDays(d, 1)) {
            total += perDay.get(d) ?? 0;
            cumulative.push(Math.round(total * 10) / 10);
          }
          out.push({ year, cumulative_m: cumulative });
        }
        return out;
      }),
    ),
  ),

  route('get', '/api/stats/efficiency', ({ request }) =>
    json(
      '/api/stats/efficiency',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        return within(m, from, to, [q.get('sport') ?? 'run']).flatMap((c) =>
          c.efficiency === null
            ? []
            : [{ day: c.day, activity_id: c.a.id, efficiency: c.efficiency, decoupling: c.decoupling }],
        );
      }),
    ),
  ),

  route('get', '/api/stats/calendar', ({ request }) =>
    json(
      '/api/stats/calendar',
      'get',
      memo(request, (m, q) => {
        const from = q.get('from') ?? monthOf(m.today);
        const to = q.get('to') ?? nextMonth(from);
        const form = new Map(m.fitness.map((d) => [d.day, d.tsb]));
        const days = new Map<string, Res<'/api/stats/calendar', 'get'>[number]>();
        for (let d = from; d < to; d = plusDays(d, 1))
          days.set(d, { day: d, tsb: form.get(d) ?? null, activities: [] });
        for (const c of within(m, from, to))
          days.get(c.day)?.activities.push({
            id: c.a.id,
            sport: c.a.sport,
            name: c.a.name,
            load: c.load,
            distance_m: c.a.distance_m,
            moving_s: c.moving,
            ascent_m: c.a.ascent_m,
            start: startOf(c),
          });
        return [...days.values()];
      }),
    ),
  ),

  route('get', '/api/stats/streak', () => {
    const m = model();
    const weeks = new Set(m.list.map((c) => mondayOf(c.day)));
    const first = m.list[0]?.day ?? null;
    const thisWeek = mondayOf(m.today);
    let week = weeks.has(thisWeek) ? thisWeek : plusDays(thisWeek, -7);
    let count = 0;
    while (weeks.has(week)) {
      count += 1;
      week = plusDays(week, -7);
    }
    const since = count ? plusDays(week, 7) : null;
    return json('/api/stats/streak', 'get', {
      weeks: count,
      activities: since ? m.list.filter((c) => c.day >= since).length : 0,
      since,
      first_activity: first,
    });
  }),

  route('get', '/api/stats/heatmap', ({ request }) =>
    json(
      '/api/stats/heatmap',
      'get',
      memo(request, (m, q) => ({
        routes: within(
          m,
          q.get('from') ?? '2000-01-01',
          q.get('to') ?? plusDays(m.today, 1),
          sports(q),
        ).flatMap((c) => c.a.route ?? []),
      })),
    ),
  ),

  route('get', '/api/stats/years', () => {
    const m = model();
    const first = Number(m.list[0]?.day.slice(0, 4) ?? 0);
    const out: number[] = [];
    if (first) for (let y = Number(m.today.slice(0, 4)); y >= first; y--) out.push(y);
    return json('/api/stats/years', 'get', out);
  }),

  route('get', '/api/stats/zones', ({ request }) => {
    const kind = new URL(request.url).searchParams.get('kind') ?? 'hr';
    if (!['hr', 'power', 'pace'].includes(kind)) return fail(422, 'Unknown zones');
    return json(
      '/api/stats/zones',
      'get',
      memo(request, (m, q) => {
        const { from, to } = range(q);
        const count = kind === 'power' ? 7 : 5;
        const of = (c: Computed) =>
          (kind === 'hr' ? c.hrZones : kind === 'power' ? c.powerZones : c.paceZones) ?? [];
        const total = (a: string, b: string) => {
          const out = new Array<number>(count).fill(0);
          for (const c of within(m, a, b, sports(q))) of(c).forEach((s, i) => (out[i] = (out[i] ?? 0) + s));
          return out.map((s) => Math.round(s * 10) / 10);
        };
        const span = Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
        const bounds = kind === 'hr' ? m.hrBounds : kind === 'power' ? m.powerBounds : m.paceBounds;
        return {
          kind,
          bounds: bounds.map((b) => (kind === 'pace' ? Math.round(b * 1000) / 1000 : Math.round(b))),
          seconds: total(from, to),
          previous: total(plusDays(from, -span), from),
        };
      }),
    );
  }),

  route('get', '/api/stats/bests', ({ request }) => {
    const key = new URL(request.url).searchParams.get('key') ?? '';
    if (!BEST_KEYS.includes(key)) return fail(422, 'Unknown best');
    return json(
      '/api/stats/bests',
      'get',
      memo(request, (m) => bests(m, key)),
    );
  }),

  route('get', '/api/stats/bests/recent', ({ request }) =>
    json(
      '/api/stats/bests/recent',
      'get',
      memo(request, (m, q) => {
        const since = plusDays(m.today, -Number(q.get('days') ?? 30));
        const medals: Medal[] = [];
        for (const key of BEST_KEYS) {
          const entries = bests(m, key);
          for (const entry of entries) {
            if (entry.day < since) continue;
            const place = medalOf(entries, entry, lowerIsBetter(key));
            if (place)
              medals.push({
                key,
                activity_id: entry.activity_id,
                day: entry.day,
                value: entry.value,
                rank: place[0],
                scope: place[1],
              });
          }
        }
        return medals.sort(
          (x, y) =>
            Number(x.scope !== 'all') - Number(y.scope !== 'all') ||
            x.rank - y.rank ||
            y.day.localeCompare(x.day),
        );
      }),
    ),
  ),

  route('get', '/api/stats/devices', ({ request }) => {
    const days = Number(new URL(request.url).searchParams.get('days') ?? 30);
    const since = new Date(Date.now() - days * 86_400_000).toISOString();
    const out: Res<'/api/stats/devices', 'get'> = [];
    const latest = db.activities.find((a) => a.start_utc >= since); // newest first
    if (latest)
      out.push({ name: latest.device, source: latest.source, kind: 'activity', last_used: latest.start_utc });
    // The watch also records sleep and the day's health, every morning.
    const morning = new Date(db.today);
    morning.setHours(7, 0, 0, 0);
    if (morning > new Date()) morning.setDate(morning.getDate() - 1);
    out.unshift({
      name: 'Garmin fenix 7',
      source: 'garmin',
      kind: 'health',
      last_used: morning.toISOString(),
    });
    return json('/api/stats/devices', 'get', out);
  }),

  route('get', '/api/stats/review', ({ request }) => {
    const q = new URL(request.url).searchParams;
    const period = q.get('period') ?? 'month';
    if (period !== 'month' && period !== 'year') return fail(422, 'Use month or year');
    const out = memo(request, (m) => review(m, period, q.get('value') ?? ''));
    return out
      ? json('/api/stats/review', 'get', out)
      : fail(400, 'Use YYYY for a year or YYYY-MM for a month');
  }),

  route('get', '/api/stats/strava-comparison', ({ request }) =>
    json('/api/stats/strava-comparison', 'get', memo(request, comparison)),
  ),

  route('get', '/api/sources', () => {
    const lastSync = new Date(Date.now() - 2 * 3600_000).toISOString();
    return json('/api/sources', 'get', [
      {
        kind: 'garmin',
        configured: true,
        key_expires_on: plusDays(db.todayIso, 120),
        expiring_soon: false,
        token_expires_at: new Date(Date.now() + 30 * 86_400_000).toISOString(),
        last_sync_at: lastSync,
        last_error: null,
      },
    ]);
  }),

  route('get', '/api/goals', () => json('/api/goals', 'get', goals().items.map(goalOut))),

  route('post', '/api/goals', async ({ request }) => {
    const body = goalBody(await request.json());
    if (!body) return fail(422, 'Invalid goal');
    const state = goals();
    const goal = { id: state.next++, ...body };
    state.items.push(goal);
    return json('/api/goals', 'post', goalOut(goal), { status: 201 });
  }),

  route('patch', '/api/goals/{goal_id}', async ({ request, params }) => {
    const goal = goals().items.find((g) => g.id === Number(params.goal_id));
    if (!goal) return fail(404, 'Goal not found');
    const body = goalBody(await request.json());
    if (!body) return fail(422, 'Invalid goal');
    Object.assign(goal, body);
    return json('/api/goals/{goal_id}', 'patch', goalOut(goal));
  }),

  route('delete', '/api/goals/{goal_id}', ({ params }) => {
    const state = goals();
    const index = state.items.findIndex((g) => g.id === Number(params.goal_id));
    if (index < 0) return fail(404, 'Goal not found');
    state.items.splice(index, 1);
    return new Response(null, { status: 204 });
  }),

  route('get', '/api/engine', () => json('/api/engine', 'get', engineStatus())),

  route('post', '/api/engine/recompute', () => {
    Object.assign(engine(), {
      running: true,
      done: 0,
      total: db.activities.length,
      reason: 'asked from Settings',
    });
    return json('/api/engine', 'get', engineStatus(), { status: 202 });
  }),
];
