/**
 * A run's splits and pace zones as Strava's activity page reads them (OS-1006, OS-1007; the app's since
 * #272 and #308): the time in each pace zone from the speed stream, the elevation drawn behind the
 * training analysis' bars, the split under a point across that chart, the zone a split's pace falls in
 * and the fastest splits. Pure, shared by the app and the web, and unit-tested.
 */

/**
 * A run's time in each pace zone (as Strava's "Pace zones"), from its speed stream and the zones' lower
 * bounds as speeds (m/s, Z1 from 0) the server sends. Standing still and gaps count nothing.
 */
export function paceZoneSeconds(
  speeds: (number | null)[],
  times: number[],
  bounds: number[],
  maxGap = 30,
): number[] {
  const out = bounds.map(() => 0);
  for (let i = 0; i < times.length - 1; i++) {
    const v = speeds[i];
    const dt = (times[i + 1] ?? 0) - (times[i] ?? 0);
    if (v == null || v < 0.5 || dt <= 0 || dt > maxGap) continue;
    for (let z = bounds.length - 1; z >= 0; z--) {
      if (v >= (bounds[z] ?? 0)) {
        out[z] = (out[z] ?? 0) + dt;
        break;
      }
    }
  }
  return out;
}

/** The pace zone a speed falls in: the highest whose lower bound it reaches, or null without zones. */
export function paceZoneOf(speed: number, bounds: number[]): number | null {
  if (!(speed > 0) || !bounds.length) return null;
  return bounds.reduce((zone, bound, i) => (speed >= bound ? i : zone), 0);
}

/**
 * The training analysis chart's elevation, as Strava's: the altitude along the distance, averaged into
 * `points` steps from the start to `total` metres, drawn as a grey area behind the splits' bars.
 */
export function elevationProfile(
  dist: (number | null)[] | null | undefined,
  alt: (number | null)[] | null | undefined,
  total: number,
  points = 120,
): (number | null)[] {
  if (!dist || !alt || total <= 0) return [];
  const sums = Array<number>(points).fill(0);
  const counts = Array<number>(points).fill(0);
  for (let i = 0; i < Math.min(dist.length, alt.length); i++) {
    const d = dist[i];
    const a = alt[i];
    if (d == null || a == null) continue;
    const k = Math.min(points - 1, Math.max(0, Math.floor((d / total) * points)));
    sums[k] = (sums[k] ?? 0) + a;
    counts[k] = (counts[k] ?? 0) + 1;
  }
  // A step with no sample takes the one before it, so the area has no holes.
  let last: number | null = null;
  return sums.map((s, k) => {
    if (counts[k]) last = s / (counts[k] ?? 1);
    return last;
  });
}

/** Which split a point across the chart falls in (0 to 1), the splits as wide as their distance. */
export function splitAt(share: number, distances: number[]): number {
  const total = distances.reduce((a, b) => a + b, 0);
  let covered = 0;
  for (let i = 0; i < distances.length; i++) {
    covered += distances[i] ?? 0;
    if (share * total <= covered) return i;
  }
  return Math.max(0, distances.length - 1);
}

/** Each split's speed (m/s), 0 where it has no time. */
export const splitSpeeds = (splits: { distance_m: number; time_s: number | null }[]): number[] =>
  splits.map((s) => (s.time_s ? s.distance_m / s.time_s : 0));

/** The indices of the `count` fastest splits, drawn darker on the training analysis. */
export function fastestSplits(speeds: number[], count = 2): Set<number> {
  return new Set(
    speeds
      .map((v, i) => ({ v, i }))
      .filter((x) => x.v > 0)
      .sort((a, b) => b.v - a.v)
      .slice(0, count)
      .map((x) => x.i),
  );
}
