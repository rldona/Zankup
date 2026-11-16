/** A seeded random generator (mulberry32): the same demo data on every start and in every test. */
export function seeded(seed: number) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    /** A number between `lo` and `hi`. */
    between: (lo: number, hi: number) => lo + (hi - lo) * next(),
    /** A whole number between `lo` and `hi`, both included. */
    int: (lo: number, hi: number) => Math.floor(lo + (hi - lo + 1) * next()),
    /** True with probability `p`. */
    chance: (p: number) => next() < p,
    pick: <T>(items: readonly T[]): T => items[Math.floor(next() * items.length)] as T,
  };
}

export type Random = ReturnType<typeof seeded>;
