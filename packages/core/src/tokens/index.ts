/**
 * Design tokens: the single source for the PWA's CSS variables and the React Native theme (OS-214).
 * Values come from the prototype's themes. "System" is not a theme: it picks light or dark from the OS.
 */
import tokens from './tokens.json';

export type ThemeName = keyof typeof tokens.themes;
export type Theme = (typeof tokens.themes)[ThemeName];
export type ThemeToken = Exclude<keyof Theme, 'scheme'>;

export const THEMES = Object.keys(tokens.themes) as ThemeName[];
export const commonTokens = tokens.common;
export const themes = tokens.themes;

const kebab = (name: string) => name.replace(/[A-Z0-9]+/g, (m) => `-${m.toLowerCase()}`);

/** CSS custom properties for a theme, e.g. `{ '--bg': '#f4f5f3', '--accent-fg': '#fff', … }`. */
export function cssVariables(theme: ThemeName): Record<string, string> {
  const { scheme: _scheme, ...colours } = themes[theme];
  const entries = Object.entries({ ...commonTokens, ...colours }).map(
    ([k, v]) => [`--${kebab(k)}`, v] as const,
  );
  return Object.fromEntries(entries);
}

/** A CSS rule with the theme's variables, ready to inject: `[data-theme="dark"]{color-scheme:dark;--bg:…}`. */
export function themeCss(theme: ThemeName, selector = `[data-theme="${theme}"]`): string {
  const body = Object.entries(cssVariables(theme))
    .map(([k, v]) => `${k}:${v}`)
    .join(';');
  return `${selector}{color-scheme:${themes[theme].scheme};${body}}`;
}

/** What the user picks: a theme, or "system" (light or dark from the device, high contrast if asked). */
export type ThemeChoice = 'system' | ThemeName;
export const THEME_CHOICES: readonly ThemeChoice[] = ['system', ...THEMES];
export const THEME_STORAGE_KEY = 'zankup-theme';

export function isThemeChoice(value: unknown): value is ThemeChoice {
  return typeof value === 'string' && (THEME_CHOICES as readonly string[]).includes(value);
}

/**
 * Every theme as one stylesheet. Without `data-theme` on <html> (the "system" choice) the device
 * decides: light, dark with `prefers-color-scheme: dark`, high contrast with `prefers-contrast: more`.
 * Any element with `data-theme` takes that theme's variables, which is how the picker draws previews.
 */
export function allThemesCss(): string {
  const system = ':root:not([data-theme])';
  return [
    themeCss('light', ':root'),
    `@media (prefers-color-scheme: dark){${themeCss('dark', system)}}`,
    `@media (prefers-contrast: more){${themeCss('highContrast', system)}}`,
    ...THEMES.map((name) => themeCss(name)),
  ].join('\n');
}

/**
 * Inline script for <head>: applies the saved theme before the first paint, so there is no flash of
 * the wrong theme. Kept tiny and dependency-free; it runs before any module loads.
 */
export function themeBootScript(): string {
  return `(function(){try{var t=localStorage.getItem(${JSON.stringify(THEME_STORAGE_KEY)});if(${JSON.stringify(THEMES)}.indexOf(t)>=0)document.documentElement.dataset.theme=t}catch(e){}})();`;
}

/**
 * Zone colours as Strava's (sampled from its app): heart rate in reds, pace in blues and power in
 * purples, from the lightest (Z1) to the darkest, the same on every theme. Each kind's chart on an
 * activity's page is drawn in its scale's strong colour.
 */
export const ZONE_SCALES = {
  hr: ['#fcd0d1', '#f3a4a3', '#ee7675', '#eb4748', '#de2625'],
  pace: ['#cce0f2', '#99c0ec', '#66a0e1', '#327fd9', '#0160cf', '#004da7'],
  power: ['#e8cff9', '#d29ef1', '#bc6eed', '#a63de7', '#910de2', '#730ab2', '#550886'],
} as const;

export type ZoneScale = keyof typeof ZONE_SCALES;

/** `count` colours of a scale, light to dark: all of them, or spread over the scale's range. */
export function zoneScale(kind: ZoneScale, count: number): string[] {
  const scale: readonly string[] = ZONE_SCALES[kind];
  if (count === scale.length) return [...scale];
  return Array.from(
    { length: count },
    (_, i) => scale[Math.round((i * (scale.length - 1)) / Math.max(1, count - 1))] ?? '#000000',
  );
}

/** The chart colours of heart rate, pace (and speed) and power: each scale's strong colour. */
export const KIND_COLOURS = {
  hr: ZONE_SCALES.hr[4],
  pace: ZONE_SCALES.pace[4],
  power: ZONE_SCALES.power[4],
} as const;
