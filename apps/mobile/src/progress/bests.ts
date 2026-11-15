/**
 * Best times on Progress (as Strava's): every activity's best at a measure (a run distance, a ride's
 * power over a duration or its distance), its place of all time and in its year, the top ten and the
 * medals. Pure, so it is unit-tested; the bests come from /api/stats/bests.
 */

export type Best = { activity_id: number; name?: string | null; day: string; value: number };

/** The run distances (seconds, lower is better) and the ride's measures (higher is better). */
export const RUN_KEYS = [
  '400m',
  'half_mile',
  '1k',
  '1mile',
  '2mile',
  '5k',
  '10k',
  '15k',
  '10mile',
  'half_marathon',
  'marathon',
] as const;
export const RIDE_KEYS = [
  'longest',
  'power_5',
  'power_15',
  'power_30',
  'power_60',
  'power_300',
  'power_600',
  'power_1200',
  'power_3600',
] as const;

export const lowerIsBetter = (key: string) => !key.startsWith('power_') && key !== 'longest';

const better = (key: string) => (a: number, b: number) => (lowerIsBetter(key) ? a < b : a > b);

/** Best first; equal ones by date, the earlier first (it was there first). */
export function ranked(key: string, bests: Best[]): Best[] {
  const lower = lowerIsBetter(key);
  return [...bests].sort((a, b) =>
    a.value !== b.value ? (lower ? a.value - b.value : b.value - a.value) : a.day < b.day ? -1 : 1,
  );
}

/** The place of a best among others: one more than those strictly better. */
export function placeOf(key: string, bests: Best[], best: Best): number {
  const beats = better(key);
  return 1 + bests.filter((b) => beats(b.value, best.value)).length;
}

/** A best's medal in its year (1 to 3), shown beside it in the list; null otherwise. */
export function yearMedal(key: string, bests: Best[], best: Best): number | null {
  const year = best.day.slice(0, 4);
  const place = placeOf(
    key,
    bests.filter((b) => b.day.startsWith(year)),
    best,
  );
  return place <= 3 ? place : null;
}

/** The years with bests, newest first. */
export function yearsOf(bests: Best[]): string[] {
  return [...new Set(bests.map((b) => b.day.slice(0, 4)))].sort().reverse();
}

/** The bests of a year, or of the last twelve months, newest first (the list and the chart). */
export function bestsIn(bests: Best[], year: string | null, today: string): Best[] {
  const since = `${Number(today.slice(0, 4)) - 1}${today.slice(4)}`;
  return bests
    .filter((b) => (year ? b.day.startsWith(year) : b.day > since))
    .sort((a, b) => (a.day < b.day ? 1 : -1));
}

/** A measure's value written as Strava does: 26:45 · 551 W · 102,2 km. */
export function bestValue(key: string, value: number, locale: string, imperial = false): string {
  if (key.startsWith('power_')) return `${Math.round(value)} W`;
  if (key === 'longest') {
    const v = value / (imperial ? 1609.344 : 1000);
    return `${v.toLocaleString(locale, { maximumFractionDigits: 1 })} ${imperial ? 'mi' : 'km'}`;
  }
  const s = Math.round(value);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const pad = (n: number) => String(n).padStart(2, '0');
  return h ? `${h}:${pad(m)}:${pad(s % 60)}` : `${m}:${pad(s % 60)}`;
}

/** The distance in metres of a run key, for its pace. */
export const RUN_METRES: Record<string, number> = {
  '400m': 400,
  half_mile: 804.672,
  '1k': 1000,
  '1mile': 1609.344,
  '2mile': 3218.688,
  '5k': 5000,
  '10k': 10000,
  '15k': 15000,
  '10mile': 16093.44,
  half_marathon: 21097.5,
  marathon: 42195,
};

/**
 * The chart, as Strava's: one slot per best, oldest to newest, `CHART_SLOTS` of them on screen at a
 * time, scrolled sideways. `offset` is the slot at the left edge (fractional while it moves); with fewer
 * bests than slots they sit on the right, the newest at the edge.
 */
export const CHART_SLOTS = 12;

export function offsetRange(count: number, slots = CHART_SLOTS): [number, number] {
  const low = Math.min(0, count - slots);
  return [low, Math.max(low, count - slots)];
}

/** The offset that puts a best (its index, oldest first) at the right edge, as far as the bests allow. */
export function offsetFor(index: number, count: number, slots = CHART_SLOTS): number {
  const [low, high] = offsetRange(count, slots);
  return Math.min(high, Math.max(low, index - (slots - 1)));
}

/** The best at the right edge for an offset: the one the list shows at its top. */
export function rightIndex(offset: number, count: number, slots = CHART_SLOTS): number {
  return Math.min(count - 1, Math.max(0, Math.round(offset) + slots - 1));
}

/**
 * The vertical scale, the same while the chart scrolls: from the record (top) to the 90th percentile of
 * the rest (bottom), so a few very slow days do not squash everything; worse ones sit on the bottom.
 */
export function chartScale(key: string, bests: Best[]): { record: number; worst: number } {
  const order = ranked(key, bests);
  const record = order[0]?.value ?? 0;
  const worst = order[Math.floor(0.9 * (order.length - 1))]?.value ?? record;
  return { record, worst };
}

const dayNumber = (iso: string) =>
  Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10)) / 86_400_000;

/**
 * The trend through the bests (Strava's smooth line): at each best, the average of those within
 * `days` either side, so one good or bad day moves it a little and the line shows the progress.
 * `context` are bests outside the window that still count near its edges.
 */
export function trend(points: Best[], days = 45, context: Best[] = points): number[] {
  return points.map((p) => {
    const at = dayNumber(p.day);
    const near = context.filter((b) => Math.abs(dayNumber(b.day) - at) <= days);
    return near.reduce((s, b) => s + b.value, 0) / Math.max(1, near.length);
  });
}
