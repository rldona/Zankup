import { describe, expect, it } from 'vitest';

import { cardMedals, offsetForScroll, paceRange, scrollForOffset, skillOf } from './zonesSkillsBests';

describe('pace zones', () => {
  // Lower bounds as speeds (m/s), Z1 from 0.
  const bounds = [0, 2.574, 2.904, 3.135, 3.3];

  it('writes each range as paces, the fast ones last', () => {
    expect(paceRange(bounds, 0, 1000)).toBe('> 6:29');
    expect(paceRange(bounds, 2, 1000)).toBe('5:19 - 5:44');
    expect(paceRange(bounds, 4, 1000)).toBe('< 5:03');
    expect(paceRange([], 0, 1000)).toBe('');
  });
});

describe('skills', () => {
  it('puts each spoke in its skill', () => {
    expect([0, 2, 3, 6, 7, 11].map(skillOf)).toEqual([
      'sprint',
      'sprint',
      'attack',
      'attack',
      'climb',
      'climb',
    ]);
  });
});

describe('the card’s medals', () => {
  it('keeps the first of each measure, up to three, of the shown units', () => {
    const medals = [
      { key: '10k', rank: 1 },
      { key: '10k', rank: 2 },
      { key: '1mile', rank: 1 },
      { key: '5k', rank: 3 },
      { key: 'power_60', rank: 1 },
      { key: 'longest', rank: 1 },
    ];
    const shown = cardMedals(medals, (k) => k !== '1mile');
    expect(shown.map((m) => `${m.key}${m.rank}`)).toEqual(['10k1', '5k3', 'power_601']);
  });
});

describe('the bests’ chart and list', () => {
  // Twenty rows, 50 px each, under a 40 px year header.
  const tops = Array.from({ length: 20 }, (_, i) => i * 50);

  it('starts with the newest best at the right edge', () => {
    expect(offsetForScroll(0, tops, 0)).toBe(8);
    expect(scrollForOffset(8, tops, 0)).toBe(0);
  });

  it('moves the chart back as the list scrolls down, and the other way round', () => {
    expect(offsetForScroll(100, tops, 0)).toBe(6);
    expect(offsetForScroll(125, tops, 0)).toBe(5.5);
    expect(scrollForOffset(5.5, tops, 0)).toBe(125);
    expect(scrollForOffset(6, tops, 40)).toBe(60);
  });

  it('stays within the bests', () => {
    expect(offsetForScroll(5000, tops, 0)).toBe(0);
    expect(offsetForScroll(0, [], 0)).toBeNull();
    expect(offsetForScroll(0, [0, 50], 0)).toBe(-10);
  });
});
