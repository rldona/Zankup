/**
 * Locale formatters kept once per locale and options. `toLocaleString` and its kin build a new
 * formatter on every call, which on Hermes (the Android app) costs far more than formatting itself: a
 * chart's year of day labels or a list of cards called it hundreds of times per render.
 */
const numbers = new Map<string, Intl.NumberFormat>();
const dates = new Map<string, Intl.DateTimeFormat>();

const keyOf = (locale: string, options: object | undefined) => `${locale}|${JSON.stringify(options ?? {})}`;

/** `n.toLocaleString(locale, options)`, with the formatter kept. */
export function formatNumber(n: number, locale: string, options?: Intl.NumberFormatOptions): string {
  if (typeof Intl?.NumberFormat !== 'function') return n.toLocaleString(locale, options);
  const key = keyOf(locale, options);
  let format = numbers.get(key);
  if (!format) numbers.set(key, (format = new Intl.NumberFormat(locale, options)));
  return format.format(n);
}

/**
 * A date written with `Intl.DateTimeFormat(locale, options)`, the formatter kept: the same as
 * `toLocaleDateString` or `toLocaleTimeString` when the options name the fields (as every caller does).
 */
export function formatDate(date: Date, locale: string, options: Intl.DateTimeFormatOptions): string {
  if (typeof Intl?.DateTimeFormat !== 'function') return date.toLocaleString(locale, options);
  const key = keyOf(locale, options);
  let format = dates.get(key);
  if (!format) dates.set(key, (format = new Intl.DateTimeFormat(locale, options)));
  return format.format(date);
}
