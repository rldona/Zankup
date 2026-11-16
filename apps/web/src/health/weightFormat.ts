/** How the weight is written (OS-1009): 72,5 kg, a signed change, and its days. */
import { locale } from '../i18n';

/** 72,5: one decimal at most, in the reader's language. */
export const kg = (n: number) => n.toLocaleString(locale, { maximumFractionDigits: 1 });

/** +0,5, −1,5 (a true minus sign) or 0. */
export const signed = (n: number) => `${n > 0 ? '+' : n < 0 ? '−' : ''}${kg(Math.abs(n))}`;

/** 12 Nov. */
export const shortDay = (day: string) =>
  new Date(`${day}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'short' });

/** 12 November 2026. */
export const longDay = (day: string) =>
  new Date(`${day}T12:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });

/** Every day from `from` to `to`, both included (YYYY-MM-DD). */
export function daysBetween(from: string, to: string): string[] {
  const out: string[] = [];
  const day = new Date(`${from}T00:00:00Z`);
  const last = new Date(`${to}T00:00:00Z`);
  for (; day <= last; day.setUTCDate(day.getUTCDate() + 1)) out.push(day.toISOString().slice(0, 10));
  return out;
}
