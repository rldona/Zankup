/**
 * Training zones, Skills and Best times on Progress, shared by the app and the web (OS-1004): a pace
 * zone's range, the fixed colours of skills, radars and medals, the card's medals and how the bests'
 * chart and list follow each other. Pure, so it is unit-tested.
 */
import { CHART_SLOTS, offsetRange } from './bests';
import { SKILLS, type Skill } from './skillLevels';

/** A zone's range as paces, the way it reads: "> 7:00", "5:25 - 6:02", "< 4:46" (per km or mile). */
export function paceRange(bounds: number[], index: number, unit: number): string {
  const pace = (mps: number) => {
    const s = Math.round(unit / mps);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  };
  const lo = bounds[index];
  const hi = bounds[index + 1];
  if (!lo) return hi ? `> ${pace(hi)}` : '';
  if (hi == null) return `< ${pace(lo)}`;
  return `${pace(hi)} - ${pace(lo)}`;
}

/** The skills' colours, as Strava's (sampled from its app), the same on every theme. */
export const SKILL_COLOURS: Record<Skill, string> = {
  sprint: '#0160cf',
  attack: '#59a61c',
  climb: '#fb7432',
};

/** The words on a skill's ring: white on the dark sprint blue, near-black on the others. */
export const SKILL_TEXT: Record<Skill, string> = { sprint: '#ffffff', attack: '#141414', climb: '#141414' };

/** The two radars' colours, as Strava's: all time in purple, the last eight weeks in pink over it. */
export const RADAR_COLOURS = { all: '#823bae', recent: '#d97ed1' } as const;

/**
 * Medals are gold, silver and bronze in every theme: their colour is what they say. Past the third,
 * the theme's grey.
 */
export const MEDAL_COLOURS = ['#e8b10e', '#8f9ea6', '#c06d3d'];

/** The skill a spoke of the radar belongs to (its index among `DURATIONS`). */
export function skillOf(index: number): Skill {
  let n = index;
  for (const s of SKILLS) {
    if (n < s.durations.length) return s.skill;
    n -= s.durations.length;
  }
  return 'climb';
}

/**
 * The card's medals: one per measure, up to three. The server sorts them all-time first, then by place
 * and newest, so a measure's first is its best medal; `shown` drops the measures of the other units.
 */
export function cardMedals<T extends { key: string }>(medals: T[], shown: (key: string) => boolean): T[] {
  return medals
    .filter((m) => shown(m.key))
    .filter((m, i, all) => all.findIndex((o) => o.key === m.key) === i)
    .slice(0, 3);
}

/**
 * The chart's offset (its left slot) for the list scrolled to `y`: the best whose row is at the top of
 * the list (under the sticky year header, `header` high), fractional while between two rows, at the
 * chart's right edge. `tops` are the rows' tops, newest first. Null until every row is measured.
 */
export function offsetForScroll(
  y: number,
  tops: number[],
  header: number,
  slots = CHART_SLOTS,
): number | null {
  const n = tops.length;
  if (n === 0) return null;
  const [low, high] = offsetRange(n, slots);
  const line = y + header;
  let k = 0;
  while (k < n - 1 && (tops[k + 1] ?? 0) <= line) k++;
  const top = tops[k] ?? 0;
  const next = tops[k + 1];
  const share = next != null && next > top ? Math.min(1, Math.max(0, (line - top) / (next - top))) : 0;
  const right = n - 1 - (k + share);
  return Math.min(high, Math.max(low, right - (slots - 1)));
}

/** The other way: where to scroll the list so its top best is the chart's right edge at `offset`. */
export function scrollForOffset(offset: number, tops: number[], header: number, slots = CHART_SLOTS): number {
  const n = tops.length;
  if (n === 0) return 0;
  const right = Math.min(n - 1, Math.max(0, offset + slots - 1));
  const k = n - 1 - right;
  const k0 = Math.floor(k);
  const k1 = Math.min(n - 1, k0 + 1);
  const a = tops[k0] ?? 0;
  const y = a + (k - k0) * ((tops[k1] ?? a) - a);
  return Math.max(0, y - header);
}
