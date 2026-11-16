import { describe, expect, it } from 'vitest';

import { message } from '../i18n';
import { glanceText, monthLabels, monthsBack, rangeLabels, volumeFormat, weekTitle } from './volumeView';

const t = (key: Parameters<typeof message>[1], values?: Record<string, string | number>) =>
  message('en', key, values);

describe('volumeFormat', () => {
  it('writes totals in metric and imperial units', () => {
    const metric = volumeFormat(false, 'en');
    expect(metric.value('time', 15_420)).toBe('4h 17min');
    expect(metric.value('distance', 28_970)).toBe('28.97 km');
    expect(metric.value('distance', 0)).toBe('0 km');
    expect(metric.value('ascent', 1421)).toBe('1,421 m');
    expect(metric.value('count', 22)).toBe('22');
    const imperial = volumeFormat(true, 'en');
    expect(imperial.value('distance', 16_093.44, 1)).toBe('10.0 mi');
    expect(imperial.value('ascent', 100)).toBe('328 ft');
  });
  it('labels the axis and rounds its ticks in the units shown', () => {
    const metric = volumeFormat(false, 'en');
    expect(metric.tick('distance', 30_000)).toBe('30 km');
    expect(metric.tick('distance', 2500)).toBe('2.5 km');
    expect(metric.ticks(9000, 'distance', 2)).toEqual([0, 5000, 10_000]);
    const feet = volumeFormat(true, 'en').ticks(300, 'ascent', 2);
    expect(feet.map((v) => Math.round(v * 3.28084))).toEqual([0, 500, 1000]);
  });
});

describe('dates under the charts', () => {
  it('titles a week from Monday to Sunday', () => {
    expect(weekTitle('2026-09-07', 'en')).toBe('Sep 7 - Sep 13, 2026');
  });
  it('puts a month where one starts', () => {
    expect(monthLabels(['2026-09-21', '2026-09-28', '2026-10-05', '2026-10-12'], 'en')).toEqual([
      { index: 2, text: 'OCT' },
    ]);
  });
  it('writes days over 7 days and Mondays over a month', () => {
    const week = [
      '2026-11-09',
      '2026-11-10',
      '2026-11-11',
      '2026-11-12',
      '2026-11-13',
      '2026-11-14',
      '2026-11-15',
    ];
    expect(rangeLabels('7d', week, 'en').map((l) => l.text)).toEqual(['M', 'T', 'W', 'T', 'F', 'S', 'S']);
    expect(rangeLabels('1m', week, 'en')).toEqual([{ index: 0, text: 'NOV 9' }]);
  });
});

describe('glanceText', () => {
  const base = { range: '3m' as const, compare: null, format: (v: number) => `${v} h` };
  it('says how the range went against the one before', () => {
    expect(glanceText({ ...base, total: 12, previousTotal: 10, t })).toBe(
      'Nice work! You logged 12 h in the last three months, 20 % more than the period before (10 h).',
    );
    expect(glanceText({ ...base, total: 10, previousTotal: 10, t })).toContain('the same');
    expect(glanceText({ ...base, total: 5, previousTotal: 0, t })).toBe(
      'You logged 5 h in the last three months. Keep it up!',
    );
    expect(glanceText({ ...base, total: 0, previousTotal: 4, t })).toBe(
      'No activities in the last three months yet.',
    );
  });
  it('against years back, names the span', () => {
    expect(glanceText({ ...base, compare: 2, total: 8, previousTotal: 10, t })).toBe(
      'You logged 20 % less than in the same span 2 years ago (10 h).',
    );
  });
});

describe('monthsBack', () => {
  it('lists the months back to the first activity, newest first', () => {
    expect(monthsBack('2025-11-20', '2026-01-03')).toEqual([
      { year: 2026, month: 1 },
      { year: 2025, month: 12 },
      { year: 2025, month: 11 },
    ]);
  });
});
