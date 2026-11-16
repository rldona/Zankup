/**
 * Weight over time (Health → Weight, OS-1009): the weighings drawn as one value a day, joined in a
 * straight line between two weighings, how much it moved over a span, and the history with each
 * weighing's step from the one before. Shared by the app and the web.
 */
import type { Weighing } from '../api/queries';
import { addDays } from '../progress/weeks';

export const WEIGHT_RANGES = ['1m', '3m', '6m', '1y', 'all'] as const;
export type WeightRange = (typeof WEIGHT_RANGES)[number];

const DAYS: Record<Exclude<WeightRange, 'all'>, number> = { '1m': 30, '3m': 91, '6m': 182, '1y': 365 };

/** The first day a range shows: so many days back, or the first weighing for all of them. */
export function weightRangeStart(range: WeightRange, today: string, weighings: Weighing[]): string {
  if (range === 'all') return weighings[0]?.day ?? today;
  return addDays(today, -DAYS[range]);
}

const dayNumber = (iso: string) => Date.parse(`${iso}T00:00:00Z`) / 86_400_000;

/**
 * One value a day from `from` to `to`: a weighing's own, between two weighings the straight line
 * joining them, and nothing before the first or after the last. Weighings oldest first.
 */
export function dailyWeights(weighings: Weighing[], from: string, to: string): (number | null)[] {
  const out: (number | null)[] = [];
  let next = 0;
  for (let day = from; day <= to; day = addDays(day, 1)) {
    while (next < weighings.length && (weighings[next]?.day ?? '') < day) next++;
    const after = weighings[next];
    const before = after?.day === day ? after : weighings[next - 1];
    if (!before || !after) out.push(null);
    else if (before === after) out.push(after.weight_kg);
    else {
      const share = (dayNumber(day) - dayNumber(before.day)) / (dayNumber(after.day) - dayNumber(before.day));
      out.push(before.weight_kg + (after.weight_kg - before.weight_kg) * share);
    }
  }
  return out;
}

/** How much the weight moved from the first weighing on or after `from` to the latest; null with fewer than two. */
export function weightChange(weighings: Weighing[], from: string): number | null {
  const inside = weighings.filter((w) => w.day >= from);
  const first = inside[0];
  const last = inside.at(-1);
  if (!first || !last || first === last) return null;
  return Math.round((last.weight_kg - first.weight_kg) * 10) / 10;
}

/** The history, newest first, each weighing with its step from the one before (null for the first). */
export function weightHistory(weighings: Weighing[]): (Weighing & { step: number | null })[] {
  return weighings
    .map((w, i) => {
      const before = weighings[i - 1];
      return { ...w, step: before ? Math.round((w.weight_kg - before.weight_kg) * 10) / 10 : null };
    })
    .reverse();
}

/** 72.5 from what was typed ("72.5", "72,5"), or null when it is no weight (20 to 300 kg). */
export function parseWeight(text: string): number | null {
  const value = Number(text.trim().replace(',', '.'));
  return text.trim() !== '' && Number.isFinite(value) && value >= 20 && value <= 300
    ? Math.round(value * 100) / 100
    : null;
}
