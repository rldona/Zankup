import { describe, expect, it } from 'vitest';

import { formatDate, formatNumber } from './intl';

describe('kept formatters', () => {
  it('write as toLocaleString and toLocaleDateString do', () => {
    expect(formatNumber(1234.5, 'es', { maximumFractionDigits: 1 })).toBe(
      (1234.5).toLocaleString('es', { maximumFractionDigits: 1 }),
    );
    expect(formatNumber(7, 'en')).toBe('7');
    const d = new Date(Date.UTC(2026, 9, 27, 12));
    const opts = { day: 'numeric', month: 'short', timeZone: 'UTC' } as const;
    expect(formatDate(d, 'es', opts)).toBe(d.toLocaleDateString('es', opts));
    // Kept per options: other options, another formatter.
    expect(formatDate(d, 'es', { month: 'long', timeZone: 'UTC' })).toBe('octubre');
  });
});
