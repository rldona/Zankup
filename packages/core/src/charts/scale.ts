/**
 * Round axis scales, shared by the app's charts and the web's: a round step and the domain it covers,
 * and the tightest of them over a domain, so the lines fill the chart.
 */

export type Scale = { domain: [number, number]; ticks: number[] };

const INTERVALS = 4;

const round = (v: number) => Math.round(v * 1e6) / 1e6;

/** A round step (1, 2, 2.5 or 5 times a power of ten) and the domain it covers in four intervals. */
export function niceScale([lo, hi]: [number, number], intervals = INTERVALS): Scale {
  const span = hi - lo || Math.abs(hi) || 1;
  let magnitude = 10 ** Math.floor(Math.log10(span / intervals));
  for (let tries = 0; tries < 40; tries++) {
    for (const m of [1, 2, 2.5, 5]) {
      const step = m * magnitude;
      const start = Math.floor(lo / step + 1e-9) * step;
      if (start + intervals * step >= hi - 1e-9) {
        const ticks = Array.from({ length: intervals + 1 }, (_, i) => round(start + i * step));
        return { domain: [round(start), round(start + intervals * step)], ticks };
      }
    }
    magnitude *= 10;
  }
  return { domain: [lo, hi], ticks: [lo, hi] };
}

/**
 * The tightest round scale over a domain in four to six steps: 0–5300 ends at 6000 (six steps of 1000),
 * not at 8000 (four of 2000), so the lines fill the chart. On a tie, the fewer steps.
 */
export function tightScale(domain: [number, number]): Scale {
  return [5, 6]
    .map((n) => niceScale(domain, n))
    .reduce(
      (best, s) => (s.domain[1] - s.domain[0] < best.domain[1] - best.domain[0] ? s : best),
      niceScale(domain, 4),
    );
}
