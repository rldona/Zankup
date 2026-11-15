import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { MARK_SHAPES, MARK_VIEWBOX } from '@zankup/core';
import { describe, expect, it } from 'vitest';

import { variants } from '../plugins/appIcons.json';

describe('app icons', () => {
  it('draws the launcher icons from the mark as packages/core has it', () => {
    const plugin = readFileSync(join(__dirname, '../plugins/withAppIcons.js'), 'utf8');
    expect(plugin).toContain(`const MARK_D = '${MARK_SHAPES.path.d}'`);
    expect(plugin).toContain(
      `width: ${MARK_VIEWBOX.width}, height: ${MARK_VIEWBOX.height}, stroke: ${MARK_SHAPES.path.strokeWidth}`,
    );
  });
  it('has the default first and every variant once, with colours', () => {
    expect(variants[0].name).toBe('default');
    expect(new Set(variants.map((v) => v.name)).size).toBe(variants.length);
    for (const v of variants) {
      expect(v.background.length).toBeGreaterThan(0);
      expect(v.mark).toMatch(/^#[0-9a-f]{6}$/);
    }
  });
});
