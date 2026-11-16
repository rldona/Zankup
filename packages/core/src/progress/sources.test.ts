import { describe, expect, it } from 'vitest';

import { daysAgo, SOURCE_NAMES } from './sources';

describe('data sources', () => {
  const now = new Date(2026, 10, 16, 9, 0);

  it('counts whole calendar days, never below zero', () => {
    expect(daysAgo(new Date(2026, 10, 16, 1, 0).toISOString(), now)).toBe(0);
    expect(daysAgo(new Date(2026, 10, 15, 23, 30).toISOString(), now)).toBe(1);
    expect(daysAgo(new Date(2026, 10, 2, 12, 0).toISOString(), now)).toBe(14);
    expect(daysAgo(new Date(2026, 10, 17, 12, 0).toISOString(), now)).toBe(0);
  });

  it('names the services', () => {
    expect(SOURCE_NAMES.garmin).toBe('Garmin');
    expect(SOURCE_NAMES.file).toBeUndefined();
  });
});
