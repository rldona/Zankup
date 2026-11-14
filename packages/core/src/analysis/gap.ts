/**
 * Grade-adjusted speed at each sample, for the run's "Grade-adjusted pace" chart: the speed times
 * Minetti's cost of running at the gradient over the cost on the flat, the same model as the engine's
 * average (packages/metrics/zankup_metrics/gap.py): gradient from altitude over 50 m of distance,
 * clamped to ±45 %. Pure, so it is unit-tested.
 */
const FLAT = 3.6;
const MAX_GRADE = 0.45;

export function cost(grade: number): number {
  const i = Math.max(-MAX_GRADE, Math.min(MAX_GRADE, grade));
  return 155.4 * i ** 5 - 30.4 * i ** 4 - 43.3 * i ** 3 + 46.3 * i ** 2 + 19.5 * i + FLAT;
}

export function gapSpeeds(
  speed: (number | null)[],
  dist: (number | null)[],
  alt: (number | null)[],
  windowM = 50,
): (number | null)[] {
  const n = speed.length;
  const out: (number | null)[] = new Array(n).fill(null);
  let a = 0;
  let b = 0;
  for (let i = 0; i < n; i++) {
    const d = dist[i];
    const v = speed[i];
    if (d == null || v == null || alt[i] == null) continue;
    while (a < i && (dist[a] == null || alt[a] == null || d - (dist[a] ?? 0) > windowM / 2)) a++;
    if (b < i) b = i;
    while (b < n - 1 && dist[b + 1] != null && alt[b + 1] != null && (dist[b + 1] ?? 0) - d <= windowM / 2)
      b++;
    // Samples further apart than the window: the gradient to the neighbours either side.
    let lo = a;
    let hi = b;
    if (lo === hi) {
      if (lo > 0 && dist[lo - 1] != null && alt[lo - 1] != null) lo--;
      if (hi < n - 1 && dist[hi + 1] != null && alt[hi + 1] != null) hi++;
    }
    const run = (dist[hi] ?? 0) - (dist[lo] ?? 0);
    const grade = run > 5 ? ((alt[hi] ?? 0) - (alt[lo] ?? 0)) / run : 0;
    out[i] = (v * cost(grade)) / FLAT;
  }
  return out;
}
