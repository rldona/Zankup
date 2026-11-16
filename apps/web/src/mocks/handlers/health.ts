/**
 * Mock handlers: health (wellness, weight), the AI (status, Coach, weekly reports, MCP tokens),
 * push notifications, the Garmin sync and the API's own health check.
 *
 * The demo athlete's health is made up day by day from the activities in `db`: a Garmin watch records
 * everything, a phone (Google Health) the steps and some nights, and Health Connect the last weeks'
 * steps and resting HR. As in the API, every source keeps its rows and one is chosen per metric at read
 * time (Settings → Health's order, or the automatic rules), and Readiness compares last night with the
 * 60 days before it. A hard day leaves a lower HRV, a higher resting HR and less form the day after.
 */
import { activityDay, daysBefore, isoDay, type ApiPaths } from '@zankup/core';
import { HttpResponse, type DefaultBodyType } from 'msw';

import { db, SEED, type MockActivity } from '../db';
import { seeded, type Random } from '../random';
import { fail, json, route, type Res } from '../route';

type HealthDay = Res<'/api/wellness/days', 'get'>[number];
type Night = NonNullable<HealthDay['sleep']>;
type Value = NonNullable<HealthDay['steps']>;
type Source = Night['source'];
type Stage = Res<'/api/wellness/nights/{day}', 'get'>['stages'][number];
type HealthSettings = Res<'/api/wellness/settings', 'get'>;
type SourceStatus = HealthSettings['sources'][number];
type Weighing = Res<'/api/weight', 'get'>[number];
type SyncStatus = Res<'/api/sync/{run_id}', 'get'>;
type Weekly = Res<'/api/ai/weekly', 'post'>;
type Token = Res<'/api/ai/tokens', 'get'>[number];
type Subscription = Res<'/api/notifications/subscriptions', 'get'>[number];

/** An answer of 202 Accepted, checked against its type (`json` knows only 200 and 201). */
const accepted = <P extends keyof ApiPaths, M extends 'post'>(
  _path: P,
  _method: M,
  body: ApiPaths[P][M] extends { responses: { 202: { content: { 'application/json': infer T } } } }
    ? T
    : never,
  init: ResponseInit = { status: 202 },
) => HttpResponse.json(body as DefaultBodyType, init);

/** Time moving, or the whole time when the source gave none. */
const mov = (a: { moving_s: number | null; duration_s: number }) => a.moving_s ?? a.duration_s;
const noContent = () => new Response(null, { status: 204 });

const MODEL = 'claude-haiku-5-5';
const SOURCES: Source[] = ['garmin', 'google_health', 'health_connect'];
const FALLBACK: Source[] = ['google_health', 'garmin', 'health_connect'];
const METRICS = ['sleep', 'resting_hr', 'hrv', 'spo2', 'respiration', 'steps'] as const;
type Metric = (typeof METRICS)[number];
type ValueMetric = Exclude<Metric, 'sleep'>;
const OVERNIGHT: ValueMetric[] = ['hrv', 'spo2', 'respiration'];
/** As the API: up to 400 days of health, and 60 days before them for Readiness's baseline. */
const SPAN = 400;
const BASELINE = 60;
const WEIGHTS = { hrv: 0.35, resting_hr: 0.2, sleep: 0.25, form: 0.2 };

const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));
const round1 = (v: number) => Math.round(v * 10) / 10;
const mean = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;
const pstdev = (xs: number[]) => {
  const m = mean(xs);
  return Math.sqrt(mean(xs.map((x) => (x - m) ** 2)));
};
/** Local midnight of a "YYYY-MM-DD", as a Date. */
const dateOf = (day: string) => {
  const [y = 1970, m = 1, d = 1] = day.split('-').map(Number);
  return new Date(y, m - 1, d);
};
const at = (day: string, hour: number, minute = 0) => {
  const d = dateOf(day);
  d.setHours(hour, minute, 0, 0);
  return d;
};
const hoursAgo = (h: number) => new Date(Date.now() - h * 3600_000).toISOString();

// --- training load, for form and the day after a hard session -------------------------------------------

/** A session's load: minutes times how hard the heart worked (a simple TRIMP). */
const load = (a: MockActivity) => {
  const reserve = a.avg_hr ? clamp((a.avg_hr - 50) / 140, 0.2, 1) : 0.5;
  return (mov(a) / 60) * reserve * 1.4;
};

function training() {
  return db.slice('health.training', () => {
    const byDay = new Map<string, number>();
    for (const a of db.activities) byDay.set(activityDay(a), (byDay.get(activityDay(a)) ?? 0) + load(a));
    const first = db.activities.at(-1);
    const tsb = new Map<string, number>();
    let ctl = 0;
    let atl = 0;
    for (let day = first ? activityDay(first) : db.todayIso; day <= db.todayIso; day = daysBefore(day, -1)) {
      tsb.set(day, round1(ctl - atl)); // the form a day starts with: yesterday's fitness minus fatigue
      const l = byDay.get(day) ?? 0;
      ctl += (l - ctl) / 42;
      atl += (l - atl) / 7;
    }
    return { byDay, tsb };
  });
}

// --- what each source recorded -------------------------------------------------------------------------

type NightRecord = Night & { stages: Stage[]; nap: false };
type Row = { source: Source; wear_s: number } & Partial<Record<ValueMetric, number>>;
type Recorded = { day: string; nights: NightRecord[]; rows: Row[] };

/** A night's stages as a sleep tracker gives them: ~90-minute cycles, more deep early, more REM late. */
function stagesOf(rng: Random, inBed: number): Stage[] {
  const out: Stage[] = [];
  let offset = 0;
  const push = (stage: Stage['stage'], minutes: number) => {
    const duration = Math.min(Math.round(minutes * 60), inBed - offset);
    if (duration <= 0) return;
    const last = out.at(-1);
    if (last?.stage === stage) last.duration_s += duration;
    else out.push({ offset_s: offset, duration_s: duration, stage });
    offset += duration;
  };
  push('awake', rng.between(4, 14)); // falling asleep
  for (let cycle = 0; offset < inBed; cycle++) {
    const early = cycle < 2;
    push('light', rng.between(20, 32));
    push('deep', early ? rng.between(22, 36) : rng.between(0, 9));
    push('light', rng.between(8, 18));
    push('rem', early ? rng.between(8, 16) : rng.between(18, 34));
    if (rng.chance(0.45)) push('awake', rng.between(1, 6));
  }
  return out;
}

function night(rng: Random, source: Source, inBed: number, bedtime: Date): NightRecord {
  const stages = stagesOf(rng, inBed);
  const sum = (stage: Stage['stage']) =>
    stages.filter((s) => s.stage === stage).reduce((a, s) => a + s.duration_s, 0);
  const awake = sum('awake');
  const asleep = inBed - awake;
  const deep = sum('deep');
  const rem = sum('rem');
  const hours = asleep / 3600;
  const score = clamp(
    Math.round(
      100 -
        Math.abs(8 - hours) * 9 -
        Math.max(0, 0.16 - deep / asleep) * 150 -
        Math.max(0, 0.2 - rem / asleep) * 100 -
        (awake / inBed) * 110,
    ),
    35,
    98,
  );
  return {
    source,
    start_utc: bedtime.toISOString(),
    end_utc: new Date(bedtime.getTime() + inBed * 1000).toISOString(),
    utc_offset_s: -bedtime.getTimezoneOffset() * 60,
    asleep_s: asleep,
    awake_s: awake,
    light_s: sum('light'),
    deep_s: deep,
    rem_s: rem,
    score,
    others: [],
    stages,
    nap: false,
  };
}

/** Every day's rows, oldest first: made once from the activities, changed by deleting a source's data. */
function recorded() {
  return db.slice('health.recorded', () => {
    const rng = seeded(SEED + 6);
    const { byDay } = training();
    // Steps from the runs and walks of each day: about 1,000 a kilometre running, 1,300 walking.
    const onFoot = new Map<string, number>();
    for (const a of db.activities)
      if (a.sport === 'run' || a.sport === 'walk')
        onFoot.set(
          activityDay(a),
          (onFoot.get(activityDay(a)) ?? 0) +
            ((a.distance_m ?? 0) / 1000) * (a.sport === 'run' ? 1000 : 1300),
        );
    const out: Recorded[] = [];
    const total = SPAN + BASELINE;
    const now = new Date();
    for (let back = total - 1; back >= 0; back--) {
      const day = daysBefore(db.todayIso, back);
      const yesterday = byDay.get(daysBefore(day, 1)) ?? 0;
      const hard = clamp((yesterday - 55) / 90, 0, 1); // 0: rest or easy, 1: a long ride or race
      const weekend = [0, 6].includes(dateOf(day).getDay());
      const season = Math.cos((back / 365) * Math.PI * 2); // fitter lately than a few months ago
      const worn = rng.chance(0.96); // the watch was charging some nights
      const phone = back < 300; // Google Health connected ten months ago
      const healthConnect = back < 35; // and the Android app a month ago

      // Last night: to bed a little later at the weekend, a bit shorter after a hard day.
      const inBed = Math.round(
        clamp(rng.between(6.4, 8.4) + (weekend ? 0.5 : 0) - hard * 0.3, 6.1, 9) * 3600,
      );
      const bedtime = at(daysBefore(day, 1), weekend ? 23 : 22, Math.round(rng.between(20, 75)));
      const nights: NightRecord[] = [];
      if (worn) nights.push(night(rng, 'garmin', inBed, bedtime));
      if (phone && (!worn || rng.chance(0.45))) {
        // The phone's guess: a few minutes off the watch's, without as fine a stage breakdown.
        const shift = Math.round(rng.between(-20, 3)) * 60;
        nights.push(night(rng, 'google_health', inBed + shift, new Date(bedtime.getTime() - shift * 500)));
      }

      const hrv = clamp(Math.round(58 + 6 * season + rng.between(-4, 4) - 7 * hard), 42, 72);
      const rhr = clamp(Math.round(50.5 - season + rng.between(-1.2, 1.2) + 2 * hard), 46, 57);
      let steps = rng.between(6200, 10500) + (weekend ? 1500 : 0) + (onFoot.get(day) ?? 0);
      if (back === 0) steps *= clamp((now.getHours() + now.getMinutes() / 60 - 6) / 16, 0.05, 1);
      const rows: Row[] = [];
      if (worn || back === 0)
        rows.push({
          source: 'garmin',
          wear_s: Math.round(rng.between(20, 23.5) * 3600),
          resting_hr: rhr,
          hrv,
          spo2: Math.round(rng.between(94.5, 98.4)),
          respiration: round1(rng.between(12.8, 15.4)),
          steps: Math.round(steps),
        });
      if (phone)
        rows.push({
          source: 'google_health',
          wear_s: Math.round(rng.between(11, 15) * 3600),
          steps: Math.round(steps * rng.between(0.8, 0.92)),
          ...(nights.some((n) => n.source === 'google_health')
            ? { hrv: hrv + Math.round(rng.between(-4, 4)) }
            : {}),
        });
      if (healthConnect)
        rows.push({
          source: 'health_connect',
          wear_s: Math.round(rng.between(9, 13) * 3600),
          resting_hr: rhr + Math.round(rng.between(0, 2)),
          steps: Math.round(steps * rng.between(0.78, 0.9)),
        });
      out.push({ day, nights, rows });
    }
    return out;
  });
}

type HealthState = {
  priority: Record<Metric, Source[]>;
  connected: Record<Source, boolean>;
  lastSync: Record<Source, string | null>;
  version: number;
  resolved: { version: number; days: HealthDay[] } | null;
};

function healthState() {
  return db.slice<HealthState>('health.state', () => ({
    priority: { sleep: [], resting_hr: [], hrv: [], spo2: [], respiration: [], steps: [] },
    connected: { garmin: true, google_health: true, health_connect: true },
    lastSync: { garmin: hoursAgo(0.3), google_health: hoursAgo(1.2), health_connect: hoursAgo(5) },
    version: 0,
    resolved: null,
  }));
}

const rank = (order: Source[], source: Source) =>
  order.includes(source) ? order.indexOf(source) : order.length;
const fallback = (source: Source) => FALLBACK.indexOf(source);

/** As the API's automatic rules: the night with stages, then the longest. */
function chooseNight(nights: NightRecord[], order: Source[]) {
  return [...nights].sort(
    (a, b) =>
      rank(order, a.source) - rank(order, b.source) ||
      (a.deep_s || a.rem_s ? 0 : 1) - (b.deep_s || b.rem_s ? 0 : 1) ||
      (b.asleep_s ?? 0) - (a.asleep_s ?? 0) ||
      fallback(a.source) - fallback(b.source),
  )[0];
}

/** Overnight figures follow the chosen night's source; the others, the source worn longest. */
function chooseValue(metric: ValueMetric, rows: Row[], order: Source[], nightSource?: Source): Value | null {
  const have = rows.filter((r) => r[metric] != null);
  const key = (r: Row) => {
    const n = r.source === nightSource ? 0 : 1;
    const worn = -r.wear_s;
    return OVERNIGHT.includes(metric) ? [n, worn] : [worn, n];
  };
  const best = [...have].sort((a, b) => {
    const [a1 = 0, a2 = 0] = key(a);
    const [b1 = 0, b2 = 0] = key(b);
    return (
      rank(order, a.source) - rank(order, b.source) ||
      a1 - b1 ||
      a2 - b2 ||
      fallback(a.source) - fallback(b.source)
    );
  })[0];
  if (!best) return null;
  return {
    value: best[metric] ?? 0,
    source: best.source,
    others: have
      .filter((r) => r !== best)
      .map((r) => r.source)
      .sort(),
  };
}

const part = (z: number) => Math.round(clamp(50 + 20 * z, 0, 100));
const z = (value: number, history: number[], floor: number) =>
  history.length < 7 ? null : (value - mean(history)) / Math.max(pstdev(history), floor);

/** Every day with its chosen figures and Readiness, oldest first; worked out again after a change. */
function healthDays(): HealthDay[] {
  const state = healthState();
  if (state.resolved?.version === state.version) return state.resolved.days;
  const { tsb } = training();
  const days: HealthDay[] = [];
  let from = 0; // where the 60 days before the current one start
  for (const r of recorded()) {
    if (!r.nights.length && !r.rows.length) continue;
    const chosen = chooseNight(r.nights, state.priority.sleep);
    const others = r.nights
      .filter((n) => n !== chosen)
      .map((n) => n.source)
      .sort();
    const value = (m: ValueMetric) => chooseValue(m, r.rows, state.priority[m], chosen?.source);
    const form = tsb.get(r.day) ?? null;
    const row: HealthDay = {
      day: r.day,
      sleep: chosen ? { ...strip(chosen), others } : null,
      resting_hr: value('resting_hr'),
      hrv: value('hrv'),
      spo2: value('spo2'),
      respiration: value('respiration'),
      steps: value('steps'),
      tsb: form,
      readiness: null,
    };
    // Readiness against the 60 days before (when there are at least a week of them).
    while ((days[from]?.day ?? r.day) < daysBefore(r.day, BASELINE)) from++;
    const before = days.slice(from);
    const parts: { hrv?: number; resting_hr?: number; sleep?: number; form?: number } = {};
    const hrvZ =
      row.hrv &&
      z(
        Math.log(row.hrv.value),
        before.flatMap((d) => (d.hrv ? [Math.log(d.hrv.value)] : [])),
        0.05,
      );
    if (typeof hrvZ === 'number') parts.hrv = part(hrvZ);
    const rhrZ =
      row.resting_hr &&
      z(
        row.resting_hr.value,
        before.flatMap((d) => (d.resting_hr ? [d.resting_hr.value] : [])),
        1.5,
      );
    if (typeof rhrZ === 'number') parts.resting_hr = part(-rhrZ);
    if (row.sleep?.score != null) parts.sleep = row.sleep.score;
    if (form != null) parts.form = Math.round(clamp(60 + 1.5 * form, 0, 100));
    const keys = Object.keys(parts) as (keyof typeof parts)[];
    if (keys.length >= 2 && keys.some((k) => k !== 'form')) {
      const weight = keys.reduce((s, k) => s + WEIGHTS[k], 0);
      row.readiness = {
        score: Math.round(keys.reduce((s, k) => s + (parts[k] ?? 0) * WEIGHTS[k], 0) / weight),
        hrv: parts.hrv ?? null,
        resting_hr: parts.resting_hr ?? null,
        sleep: parts.sleep ?? null,
        form: parts.form ?? null,
      };
    }
    days.push(row);
  }
  state.resolved = { version: state.version, days };
  return days;
}

function strip(n: NightRecord): Night {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- left out of the answer
  const { stages: _s, nap: _n, ...rest } = n;
  return rest;
}

function healthSettings(): HealthSettings {
  const state = healthState();
  const rows = recorded();
  const sources: SourceStatus[] = SOURCES.map((source) => ({
    source,
    available: true,
    connected: state.connected[source],
    needs_reconnect: false,
    missing: [],
    last_sync_at: state.lastSync[source],
    nights: rows.filter((r) => r.nights.some((n) => n.source === source)).length,
    days: rows.filter((r) => r.rows.some((x) => x.source === source)).length,
  }));
  return { sources, priority: { ...state.priority } };
}

function changed() {
  healthState().version++;
}

// --- weight ------------------------------------------------------------------------------------------

/** Weighings every few days over ten months, coming down to the profile's weight; oldest first. */
function weighings() {
  return db.slice('health.weight', () => {
    const rng = seeded(SEED + 9);
    const target = db.user.weight_kg ?? 72;
    const out: Weighing[] = [];
    for (let back = 300; back >= 2; back -= rng.int(3, 8)) {
      const trend = target + 2.6 * (back / 300) + 0.4 * Math.sin(back / 23); // a little up at Christmas
      out.push({ day: daysBefore(db.todayIso, back), weight_kg: round1(trend + rng.between(-0.35, 0.35)) });
    }
    out.push({ day: daysBefore(db.todayIso, 1), weight_kg: target });
    return out;
  });
}

function followLatest() {
  const latest = weighings().at(-1);
  if (latest) db.user.weight_kg = latest.weight_kg;
}

// --- sync ----------------------------------------------------------------------------------------------

type Run = SyncStatus & { polls: number };

function syncRuns() {
  return db.slice('health.sync', () => {
    const runs: Run[] = [];
    const latest = db.activities[0];
    // Garmin's own syncs every 15 minutes; one brought the latest activity in.
    for (let i = 0; i < 12; i++) {
      const started = new Date(Date.now() - (i * 15 + 6) * 60_000);
      const brought = latest && i === 11 ? 1 : 0;
      runs.push(finished({ id: 400 - i, trigger: 'cron', started, count: brought }));
    }
    return runs;
  });
}

function finished({
  id,
  trigger,
  started,
  count,
}: {
  id: number;
  trigger: SyncStatus['trigger'];
  started: Date;
  count: number;
}): Run {
  return {
    id,
    trigger,
    status: 'success',
    started_at: started.toISOString(),
    finished_at: new Date(started.getTime() + 25_000).toISOString(),
    new_count: count,
    progress: {
      since: daysBefore(isoDay(started), 1),
      listed: count + 2,
      new: count,
      downloaded: count,
      window: 1,
      windows: 1,
    },
    pending_imports: 0,
    error: null,
    polls: 0,
  };
}

/** A run asked about moves on: queued, listing, downloading, done after a few polls. */
function advance(run: Run) {
  if (run.status === 'success' || run.status === 'error') return;
  run.polls++;
  const history = run.trigger === 'initial';
  const windows = history ? 4 : 1;
  const listed = history ? db.activities.length : 3;
  const window = Math.min(run.polls, windows);
  if (run.polls >= windows + 1) {
    run.status = 'success';
    run.finished_at = new Date().toISOString();
    run.progress = { ...run.progress, listed, new: 0, downloaded: 0, window: windows, windows, no_fit: 0 };
    return;
  }
  run.status = 'running';
  run.progress = {
    ...run.progress,
    listed: Math.round((listed * window) / windows),
    new: 0,
    downloaded: 0,
    window,
    windows,
  };
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars -- left out of the answer
const status = ({ polls: _p, ...run }: Run): SyncStatus => run;

// --- AI ------------------------------------------------------------------------------------------------

const km = (m: number | null) => ((m ?? 0) / 1000).toFixed(1);
const pace = (a: MockActivity) => {
  const s = mov(a) / ((a.distance_m ?? 1) / 1000);
  return `${Math.floor(s / 60)}:${String(Math.round(s % 60)).padStart(2, '0')}`;
};
const minutes = (s: number) => {
  const m = Math.round(s / 60);
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`;
};

const monday = (day: string) => daysBefore(day, (dateOf(day).getDay() + 6) % 7);

function weeklyOf(weekStart: string, id: number): Weekly {
  const end = daysBefore(weekStart, -7);
  const inWeek = (s: string) =>
    db.activities.filter((a) => activityDay(a) >= s && activityDay(a) < daysBefore(s, -7));
  const week = inWeek(weekStart);
  const before = inWeek(daysBefore(weekStart, 7));
  const time = week.reduce((s, a) => s + mov(a), 0);
  const prev = before.reduce((s, a) => s + mov(a), 0);
  const runs = week.filter((a) => a.sport === 'run');
  const rides = week.filter((a) => a.sport === 'ride');
  const runKm = runs.reduce((s, a) => s + (a.distance_m ?? 0), 0);
  const rideKm = rides.reduce((s, a) => s + (a.distance_m ?? 0), 0);
  const longest = [...runs].sort((a, b) => (b.distance_m ?? 0) - (a.distance_m ?? 0))[0];
  const change = prev ? Math.round(((time - prev) / prev) * 100) : 0;
  const headline = !week.length
    ? 'A week off'
    : change > 15
      ? 'A step up in volume'
      : change < -15
        ? 'A lighter week'
        : 'A solid, steady week';
  const created = at(end, 7, 3);
  return {
    id,
    week_start: weekStart,
    status: 'done',
    language: 'en',
    headline,
    summary: week.length
      ? `${week.length} sessions and ${minutes(time)} of training: ${runs.length} run${runs.length === 1 ? '' : 's'} for ${km(runKm)} km${rides.length ? `, ${rides.length} ride${rides.length > 1 ? 's' : ''} for ${km(rideKm)} km` : ''}${longest ? `, the longest run ${km(longest.distance_m)} km at ${pace(longest)} min/km` : ''}. ${change >= 0 ? `${change} % more time than the week before.` : `${-change} % less time than the week before.`}`
      : 'No sessions this week: rest counts too.',
    recommendation:
      change > 15
        ? 'Keep the volume for a week before adding more, with one fully easy day.'
        : 'Add one quality session (tempo or intervals) and keep the long run easy.',
    highlights: [
      `${minutes(time)} of training`,
      ...(runKm ? [`${km(runKm)} km running`] : []),
      ...(rideKm ? [`${km(rideKm)} km riding`] : []),
    ],
    error: null,
    created_at: created.toISOString(),
    finished_at: new Date(created.getTime() + 90_000).toISOString(),
  };
}

function ai() {
  return db.slice('health.ai', () => {
    const thisWeek = monday(db.todayIso);
    const weekly = Array.from({ length: 8 }, (_, i) => weeklyOf(daysBefore(thisWeek, 7 * (i + 1)), 80 - i));
    return {
      weekly,
      tokens: [
        {
          id: 1,
          name: 'Claude Desktop',
          prefix: 'zk_q8Rf2',
          created_at: hoursAgo(24 * 40),
          last_used_at: hoursAgo(26),
        },
      ] as Token[],
      nextToken: 2,
    };
  });
}

type CoachEvent =
  | { type: 'text'; text: string }
  | { type: 'tool'; name: string; status: 'running' | 'done' }
  | { type: 'done'; tools: string[]; usage: object };

/** A canned answer to the question, worked out from the demo athlete's own activities and health. */
function coachAnswer(question: string): { tools: string[]; text: string } {
  const q = question.toLowerCase();
  const today = db.todayIso;
  const runs = db.activities.filter((a) => a.sport === 'run');
  if (/10 ?k|race|estimate|marathon|5 ?k/.test(q)) {
    const recent = runs.filter((a) => activityDay(a) >= daysBefore(today, 60) && (a.distance_m ?? 0) >= 8000);
    const best = [...recent].sort((a, b) => mov(a) / (a.distance_m ?? 1) - mov(b) / (b.distance_m ?? 1))[0];
    const perKm = best ? (mov(best) / (best.distance_m ?? 1)) * 1000 * 0.94 : 330;
    const tenK = Math.round(perKm * 10);
    const fmt = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    return {
      tools: ['estimates', 'search_activities'],
      text:
        `On your last two months I'd put your 10K at about **${fmt(tenK)}** (${fmt(Math.round(perKm))} min/km).\n\n` +
        (best
          ? `- Your fastest long-ish run lately: ${km(best.distance_m)} km at ${pace(best)} min/km, /activities/${best.id}\n`
          : '') +
        `- ${recent.length} runs over 8 km in that time\n\n` +
        'To go faster, one session of **5 × 1 km at 10K pace** a week will do more than adding easy kilometres.',
    };
  }
  if (/rest|tired|recover|sleep|hrv|tomorrow/.test(q)) {
    const days = healthDays().slice(-7);
    const last = days.at(-1);
    const hrv = Math.round(mean(days.flatMap((d) => (d.hrv ? [d.hrv.value] : []))));
    const sleep = mean(days.flatMap((d) => (d.sleep?.asleep_s ? [d.sleep.asleep_s / 3600] : [])));
    const score = last?.readiness?.score ?? 60;
    const rest = score < 55 || (last?.tsb ?? 0) < -20;
    return {
      tools: ['daily_health', 'fitness_stats'],
      text:
        `Your readiness today is **${score}**, with an HRV of ${last?.hrv?.value ?? hrv} ms against ${hrv} ms this week and ${sleep.toFixed(1)} h of sleep a night.\n\n` +
        `- Form (TSB): ${last?.tsb ?? 0}\n- Resting HR: ${last?.resting_hr?.value ?? '–'} bpm\n\n` +
        (rest
          ? 'I would **take tomorrow off** or keep it to 30 easy minutes: the fatigue of the last days is still there.'
          : 'No need to rest: you are recovered enough for **a normal session** tomorrow.'),
    };
  }
  // By default: what this month (or week) looks like.
  const since = /week/.test(q) ? monday(today) : `${today.slice(0, 7)}-01`;
  const period = db.activities.filter((a) => activityDay(a) >= since);
  const monthRuns = period.filter((a) => a.sport === 'run');
  const longest = [...monthRuns].sort((a, b) => (b.distance_m ?? 0) - (a.distance_m ?? 0))[0];
  const total = monthRuns.reduce((s, a) => s + (a.distance_m ?? 0), 0);
  return {
    tools: ['search_activities'],
    text:
      `You ran **${km(total)} km** in ${monthRuns.length} runs ${/week/.test(q) ? 'this week' : 'this month'}` +
      (longest ? `, the longest being /activities/${longest.id}.` : '.') +
      '\n\n' +
      monthRuns
        .slice(0, 4)
        .map(
          (a) =>
            `- ${km(a.distance_m)} km on ${dateOf(activityDay(a)).toLocaleDateString('en', { weekday: 'long' })}`,
        )
        .join('\n') +
      `\n\nAll in all, ${period.length} sessions and ${minutes(period.reduce((s, a) => s + mov(a), 0))} of training.`,
  };
}

function ndjson(events: CoachEvent[]) {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      async start(controller) {
        for (const event of events) {
          controller.enqueue(encoder.encode(JSON.stringify(event) + '\n'));
          await new Promise((resolve) => setTimeout(resolve, event.type === 'tool' ? 120 : 25));
        }
        controller.close();
      },
    }),
    { headers: { 'Content-Type': 'application/x-ndjson' } },
  );
}

// --- notifications --------------------------------------------------------------------------------------

function devices() {
  return db.slice('health.push', () => ({
    items: [
      { id: 1, name: 'Chrome on macOS' },
      { id: 2, name: 'Pixel 8' },
    ] as Subscription[],
    next: 3,
  }));
}

// --- handlers ------------------------------------------------------------------------------------------

export const healthHandlers = [
  route('get', '/api/health', () =>
    json('/api/health', 'get', {
      status: 'ok',
      version: '0.0.0-mock',
      database: 'ok',
      schema_revision: 'mock',
    }),
  ),

  // wellness
  route('get', '/api/wellness/days', ({ request }) => {
    const url = new URL(request.url);
    const end = url.searchParams.get('end') ?? daysBefore(db.todayIso, -1);
    const start = url.searchParams.get('start') ?? daysBefore(end, 90);
    if (start >= end) return fail(422, 'Ranges of 1 to 400 days');
    return json(
      '/api/wellness/days',
      'get',
      healthDays().filter((d) => d.day >= start && d.day < end),
    );
  }),
  route('get', '/api/wellness/nights/{day}', ({ params }) => {
    const r = recorded().find((x) => x.day === params.day);
    const chosen = r && chooseNight(r.nights, healthState().priority.sleep);
    if (!r || !chosen) return fail(404, 'No sleep recorded that night');
    const rest = r.nights.filter((n) => n !== chosen);
    return json('/api/wellness/nights/{day}', 'get', {
      day: r.day,
      chosen: { ...strip(chosen), others: rest.map((n) => n.source).sort() },
      stages: chosen.stages,
      alternatives: rest.map(strip),
    });
  }),
  route('get', '/api/wellness/settings', () => json('/api/wellness/settings', 'get', healthSettings())),
  route('put', '/api/wellness/priority', async ({ request }) => {
    const body = await request.json();
    const state = healthState();
    for (const [metric, order] of Object.entries(body.priority))
      if ((METRICS as readonly string[]).includes(metric)) state.priority[metric as Metric] = order;
    changed();
    return json('/api/wellness/priority', 'put', healthSettings());
  }),
  route('post', '/api/wellness/sync', () => {
    // Garmin and Google are asked by the server; Health Connect only uploads from the phone.
    const state = healthState();
    const queued = (['garmin', 'google_health'] as const).filter((s) => state.connected[s]);
    for (const s of queued) state.lastSync[s] = new Date().toISOString();
    return accepted('/api/wellness/sync', 'post', { queued: [...queued] });
  }),
  route('post', '/api/wellness/history', () => {
    const state = healthState();
    const queued = (['garmin', 'google_health'] as const).filter((s) => state.connected[s]);
    return accepted('/api/wellness/history', 'post', { queued: [...queued] });
  }),
  route('delete', '/api/wellness/sources/{source}/data', ({ params }) => {
    const source = params.source as Source;
    if (!SOURCES.includes(source)) return fail(422, 'Unknown source');
    for (const r of recorded()) {
      r.nights = r.nights.filter((n) => n.source !== source);
      r.rows = r.rows.filter((x) => x.source !== source);
    }
    changed();
    return noContent();
  }),
  route('post', '/api/wellness/health-connect', async ({ request }) => {
    const body = await request.json();
    healthState().lastSync.health_connect = new Date().toISOString();
    return json('/api/wellness/health-connect', 'post', {
      sleep: body.sleep?.length ?? 0,
      days: body.days?.length ?? 0,
    });
  }),
  route('delete', '/api/wellness/google', () => {
    healthState().connected.google_health = false;
    return noContent();
  }),
  route('get', '/api/wellness/google/start', () => {
    const state = healthState();
    state.connected.google_health = true;
    state.lastSync.google_health = new Date().toISOString();
    return new Response(null, { status: 303, headers: { Location: '/settings/health?health=connected' } });
  }),
  route('get', '/api/wellness/google/callback', () => {
    healthState().connected.google_health = true;
    return new Response(null, { status: 303, headers: { Location: '/settings/health?health=connected' } });
  }),

  // weight
  route('get', '/api/weight', () => json('/api/weight', 'get', weighings())),
  route('put', '/api/weight', async ({ request }) => {
    const body = await request.json();
    if (!(body.weight_kg >= 20 && body.weight_kg <= 300)) return fail(422, 'Weight between 20 and 300 kg');
    const list = weighings();
    const entry = { day: body.day, weight_kg: Math.round(body.weight_kg * 100) / 100 };
    const i = list.findIndex((w) => w.day === body.day);
    if (i >= 0) list[i] = entry;
    else list.push(entry);
    list.sort((a, b) => a.day.localeCompare(b.day));
    followLatest();
    return json('/api/weight', 'put', entry);
  }),
  route('delete', '/api/weight/{day}', ({ params }) => {
    const list = weighings();
    const i = list.findIndex((w) => w.day === params.day);
    if (i < 0) return fail(404, 'No weighing that day');
    list.splice(i, 1);
    followLatest();
    return noContent();
  }),

  // sync
  route('post', '/api/sync', async ({ request }) => {
    const body = await request
      .clone()
      .json()
      .catch(() => null);
    const runs = syncRuns();
    const active = runs.find((r) => r.status === 'queued' || r.status === 'running');
    if (active) return accepted('/api/sync', 'post', status(active), { status: 200 });
    const since = body?.history ? '2000-01-01' : (body?.since ?? null);
    const run: Run = {
      id: (runs[0]?.id ?? 0) + 1,
      trigger: since ? 'initial' : 'button',
      status: 'queued',
      started_at: new Date().toISOString(),
      finished_at: null,
      new_count: 0,
      progress: { since: since ?? daysBefore(db.todayIso, 1) },
      pending_imports: 0,
      error: null,
      polls: 0,
    };
    runs.unshift(run);
    return accepted('/api/sync', 'post', status(run));
  }),
  route('get', '/api/sync', ({ request }) => {
    const limit = Number(new URL(request.url).searchParams.get('limit') ?? 10);
    const runs = syncRuns();
    runs.forEach(advance);
    return json('/api/sync', 'get', runs.slice(0, limit).map(status));
  }),
  route('get', '/api/sync/{run_id}', ({ params }) => {
    const run = syncRuns().find((r) => r.id === Number(params.run_id));
    if (!run) return fail(404, 'Sync not found');
    advance(run);
    return json('/api/sync/{run_id}', 'get', status(run));
  }),

  // AI
  route('get', '/api/ai/status', () =>
    json('/api/ai/status', 'get', {
      available: true,
      enabled: true,
      summaries: true,
      weekly: true,
      model: MODEL,
      budget: { month_cost_usd: 0.37, limit_usd: 5, warning: false, exceeded: false },
      sends: [
        'sport, date, duration, distance, elevation, heart rate, power and calories of an activity',
        'the metrics Zankup computes: relative effort, zones, efficiency, fitness, fatigue and form',
        'splits, laps, best efforts and records',
        'weekly totals, estimates, and sleep, HRV, resting heart rate and readiness averages',
        'your questions to the Coach and the language and units you chose',
      ],
      never_sends: [
        'coordinates, routes, maps or where you train',
        'activity names and descriptions, photos and device names',
        'your name, e-mail, birth date, sex, weight or height',
      ],
    }),
  ),
  route('get', '/api/ai/weekly', () => json('/api/ai/weekly', 'get', ai().weekly)),
  route('post', '/api/ai/weekly', async ({ request }) => {
    const body = await request.json().catch(() => null);
    const state = ai();
    const weekStart = body?.week_start ?? daysBefore(monday(db.todayIso), 7);
    const existing = state.weekly.find((w) => w.week_start === weekStart);
    if (existing) return json('/api/ai/weekly', 'post', existing);
    const report = weeklyOf(weekStart, Math.max(0, ...state.weekly.map((w) => w.id)) + 1);
    state.weekly = [report, ...state.weekly].sort((a, b) => b.week_start.localeCompare(a.week_start));
    return json('/api/ai/weekly', 'post', report);
  }),
  route('post', '/api/ai/coach', async ({ request }) => {
    const body = await request.json();
    const question = [...body.messages].reverse().find((m) => m.role === 'user')?.content ?? '';
    const { tools, text } = coachAnswer(question);
    // Written as it streams: a few words at a time.
    const pieces = text.match(/\S+\s*/g) ?? [text];
    const chunks: CoachEvent[] = [];
    for (let i = 0; i < pieces.length; i += 4)
      chunks.push({ type: 'text', text: pieces.slice(i, i + 4).join('') });
    return ndjson([
      ...tools.flatMap((name): CoachEvent[] => [
        { type: 'tool', name, status: 'running' },
        { type: 'tool', name, status: 'done' },
      ]),
      ...chunks,
      { type: 'done', tools, usage: {} },
    ]);
  }),
  route('get', '/api/ai/tokens', () => json('/api/ai/tokens', 'get', ai().tokens)),
  route('post', '/api/ai/tokens', async ({ request }) => {
    const body = await request.json();
    const state = ai();
    const secret = `zk_${Array.from({ length: 32 }, () => 'abcdefghijkmnpqrstuvwxyz23456789'[Math.floor(Math.random() * 32)]).join('')}`;
    const token: Token = {
      id: state.nextToken++,
      name: body.name,
      prefix: secret.slice(0, 8),
      created_at: new Date().toISOString(),
      last_used_at: null,
    };
    state.tokens = [...state.tokens, token];
    return json(
      '/api/ai/tokens',
      'post',
      { ...token, token: secret, url: `${location.origin}/api/mcp` },
      { status: 201 },
    );
  }),
  route('delete', '/api/ai/tokens/{token_id}', ({ params }) => {
    const state = ai();
    const before = state.tokens.length;
    state.tokens = state.tokens.filter((t) => t.id !== Number(params.token_id));
    return state.tokens.length < before ? noContent() : fail(404, 'Token not found');
  }),

  // push notifications
  route('get', '/api/notifications/key', () =>
    json('/api/notifications/key', 'get', {
      public_key: 'BMockZankupVapidKeyOnlyForTheDemo0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
    }),
  ),
  route('get', '/api/notifications/subscriptions', () =>
    json('/api/notifications/subscriptions', 'get', devices().items),
  ),
  route('post', '/api/notifications/subscriptions', async ({ request }) => {
    const body = await request.json();
    const state = devices();
    const item = { id: state.next++, name: body.name ?? null };
    state.items = [...state.items, item];
    return json('/api/notifications/subscriptions', 'post', item, { status: 201 });
  }),
  route('post', '/api/notifications/unsubscribe', () => noContent()),
  route('put', '/api/notifications/fcm', () => noContent()),
  route('post', '/api/notifications/test', () =>
    json('/api/notifications/test', 'post', { sent: devices().items.length }),
  ),
];
