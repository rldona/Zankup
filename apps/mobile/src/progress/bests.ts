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

/** The same day a year before (29 February becomes the 28th). */
export function yearBefore(iso: string): string {
  const y = Number(iso.slice(0, 4)) - 1;
  const rest = iso.slice(4) === '-02-29' ? '-02-28' : iso.slice(4);
  return `${y}${rest}`;
}

/**
 * The twelve months the chart shows, as Strava's: after `yearBefore(end)` up to `end`, both ISO days.
 * Swiping moves it a year at a time; a year from the filter is its 1 January to 31 December.
 */
export function windowOf(end: string): { from: string; to: string } {
  return { from: yearBefore(end), to: end };
}

/** The bests inside a window (after `from`, up to `to`), oldest first. */
export function bestsBetween(bests: Best[], from: string, to: string): Best[] {
  return bests.filter((b) => b.day > from && b.day <= to).sort((a, b) => (a.day < b.day ? -1 : 1));
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

/** Where a day falls in a window, from 0 (its start) to 1 (its end). */
export function windowShare(day: string, from: string, to: string): number {
  const span = dayNumber(to) - dayNumber(from);
  return span > 0 ? Math.min(1, Math.max(0, (dayNumber(day) - dayNumber(from)) / span)) : 0;
}

/** The first of each month inside a window: the chart's vertical lines. */
export function monthStarts(from: string, to: string): string[] {
  const out: string[] = [];
  let y = Number(from.slice(0, 4));
  let m = Number(from.slice(5, 7));
  for (;;) {
    m += 1;
    if (m > 12) [y, m] = [y + 1, 1];
    const iso = `${y}-${String(m).padStart(2, '0')}-01`;
    if (iso > to) return out;
    out.push(iso);
  }
}
