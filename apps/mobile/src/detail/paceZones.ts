/**
 * A run's time in each pace zone (as Strava's "Pace zones"), from its speed stream and the zones' lower
 * bounds as speeds (m/s, Z1 from 0) the server sends. Standing still and gaps count nothing. Pure, so it
 * is unit-tested.
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
    const dt = times[i + 1] - times[i];
    if (v == null || v < 0.5 || dt <= 0 || dt > maxGap) continue;
    for (let z = bounds.length - 1; z >= 0; z--) {
      if (v >= bounds[z]) {
        out[z] += dt;
        break;
      }
    }
  }
  return out;
}

/** A zone's range as paces, shared with the web. */
export { paceRange } from '@zankup/core';
