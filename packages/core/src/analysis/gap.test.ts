import { describe, expect, it } from 'vitest';

import { cost, gapSpeeds } from './gap';

describe('grade-adjusted pace', () => {
  it("is Minetti's cost, 3.6 on the flat, more uphill, less on a gentle downhill", () => {
    expect(cost(0)).toBeCloseTo(3.6);
    expect(cost(0.1)).toBeGreaterThan(3.6);
    expect(cost(-0.05)).toBeLessThan(3.6);
    expect(cost(2)).toBeCloseTo(cost(0.45)); // clamped
  });
  it('makes an uphill stretch count faster and a flat one the same', () => {
    const dist = Array.from({ length: 50 }, (_, i) => i * 3);
    const flat = gapSpeeds(
      dist.map(() => 3),
      dist,
      dist.map(() => 600),
    );
    expect(flat[25]).toBeCloseTo(3);
    const climb = gapSpeeds(
      dist.map(() => 3),
      dist,
      dist.map((d) => 600 + d * 0.08),
    ); // 8 %
    expect(climb[25] ?? 0).toBeGreaterThan(3.5);
    expect(gapSpeeds([3, null], [0, 3], [600, 600])[1]).toBeNull();
  });
});
