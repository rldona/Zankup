/**
 * What Progress's Fitness, Relative effort and race predictions draw, shared by the app and the web
 * (OS-1003): the fitness chart's round axis and the days written under it, each week's effort judged
 * against the three before it, and the days labelled under a prediction's line. Pure, so it is
 * unit-tested.
 */
import { effortStatus, weeklyRange, type EffortRange, type EffortStatus } from './effort';
import { addDays } from './weeks';

const round = (v: number) => Math.round(v * 1e6) / 1e6;

/** A round step (1, 2, 2.5 or 5 times a power of ten) from zero to over `top` in `intervals` steps. */
function roundScale(top: number, intervals: number): number[] {
  let magnitude = 10 ** Math.floor(Math.log10(top / intervals));
  for (let tries = 0; tries < 40; tries++) {
    for (const m of [1, 2, 2.5, 5]) {
      const step = m * magnitude;
      if (intervals * step >= top - 1e-9)
        return Array.from({ length: intervals + 1 }, (_, i) => round(i * step));
    }
    magnitude *= 10;
  }
  return [0, top];
}

/**
 * The fitness chart's axis: from zero to the tightest round value over the line's top, in four to six
 * steps (Strava: 106 → 120, not 125); on a tie, the fewer steps. Never under 10, so a new account's
 * line is not stretched over the whole chart.
 */
export function fitnessScale(values: number[]): { max: number; ticks: number[] } {
  const top = Math.max(10, ...values) * 1.05;
  const ticks = [4, 5, 6]
    .map((n) => roundScale(top, n))
    .reduce((best, s) => ((s.at(-1) ?? 0) < (best.at(-1) ?? 0) ? s : best));
  return { max: ticks.at(-1) ?? top, ticks };
}

/** The days written under a chart of `count` days: about `wanted`, evenly spread, the first and the last. */
export function spreadDays(count: number, wanted = 5): number[] {
  if (count <= 0) return [];
  if (count <= wanted) return Array.from({ length: count }, (_, i) => i);
  const every = (count - 1) / (wanted - 1);
  return Array.from({ length: wanted }, (_, i) => Math.round(i * every));
}

export type EffortWeek = { monday: string; score: number; range: EffortRange | null; status: EffortStatus };

/**
 * Weekly buckets of effort (the API's volume by week, oldest first) as Relative effort's weeks: each
 * week's score, the range from the three weeks before it and where the score falls. The first three
 * buckets only give the range of the fourth, so they are not weeks of their own.
 */
export function effortWeeks(buckets: { start: string; totals: { load?: number | null } }[]): EffortWeek[] {
  const scores = buckets.map((b) => ({ monday: b.start, score: Math.round(b.totals.load ?? 0) }));
  return scores.slice(3).map((w, i) => {
    const range = weeklyRange(scores.slice(i, i + 3).map((s) => s.score));
    return { ...w, range, status: effortStatus(w.score, range) };
  });
}

/** The ranges of race predictions over time, and the days each looks back. */
export const PREDICTION_DAYS = { '1m': 30, '3m': 83, '6m': 166 } as const;
export type PredictionRange = 'today' | keyof typeof PREDICTION_DAYS;
export const PREDICTION_RANGES: PredictionRange[] = ['today', '1m', '3m', '6m'];

/** The days labelled under a prediction's line, between its ends: Mondays over a month, else firsts. */
export function predictionLabelDays(
  range: keyof typeof PREDICTION_DAYS,
  from: string,
  today: string,
): string[] {
  const out: string[] = [];
  for (let day = addDays(from, 1); day < today; day = addDays(day, 1)) {
    const d = new Date(`${day}T12:00:00`);
    if (range === '1m' ? d.getDay() === 1 : d.getDate() === 1) out.push(day);
  }
  return out;
}
