/**
 * Weight over time (Health → Weight): the weighings drawn as one value a day, joined in a straight
 * line between two weighings, and how much it moved over a span.
 */
import { addDays } from './progress/weeks';

export type Weighing = { day: string; weight_kg: number };

export const WEIGHT_RANGES = ['1m', '3m', '6m', '1y', 'all'] as const;
export type WeightRange = (typeof WEIGHT_RANGES)[number];

const DAYS: Record<Exclude<WeightRange, 'all'>, number> = { '1m': 30, '3m': 91, '6m': 182, '1y': 365 };

/** The first day a range shows: so many days back, or the first weighing for all of them. */
export function rangeStart(range: WeightRange, today: string, weighings: Weighing[]): string {
  if (range === 'all') return weighings[0]?.day ?? today;
  return addDays(today, -DAYS[range]);
}

const dayNumber = (iso: string) => Date.parse(`${iso}T00:00:00Z`) / 86_400_000;

/**
 * One value a day from `from` to `to`: a weighing's own, between two weighings the straight line
 * joining them, and nothing before the first or after the last.
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
export function change(weighings: Weighing[], from: string): number | null {
  const inside = weighings.filter((w) => w.day >= from);
  const first = inside[0];
  const last = inside.at(-1);
  if (!first || !last || first === last) return null;
  return Math.round((last.weight_kg - first.weight_kg) * 10) / 10;
}

/** "72,5" from what was typed ("72.5", "72,5"), or null when it is no weight (20 to 300 kg). */
export function parseWeight(text: string): number | null {
  const value = Number(text.trim().replace(',', '.'));
  return Number.isFinite(value) && value >= 20 && value <= 300 ? Math.round(value * 100) / 100 : null;
}
