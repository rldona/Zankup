/**
 * The workout analysis chart's elevation, as Strava's: the altitude along the distance, averaged into
 * `points` steps from the start to `total` metres, drawn as a grey area behind the splits' bars. Pure,
 * so it is unit-tested.
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

/** Which split a point across the chart falls in, the splits as wide as their distance. */
export function splitAt(share: number, distances: number[]): number {
  const total = distances.reduce((a, b) => a + b, 0);
  let covered = 0;
  for (let i = 0; i < distances.length; i++) {
    covered += distances[i] ?? 0;
    if (share * total <= covered) return i;
  }
  return Math.max(0, distances.length - 1);
}
