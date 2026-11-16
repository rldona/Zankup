/**
 * Data sources on Progress (as Strava's): the names of the services that record without naming a
 * device, and how long ago a device was last used. Pure, so it is unit-tested; the devices come from
 * /api/stats/devices.
 */

/** A source's name, for a device the source names none for. */
export const SOURCE_NAMES: Record<string, string> = {
  garmin: 'Garmin',
  strava: 'Strava',
  google_health: 'Google Health',
  health_connect: 'Health Connect',
};

/** Days since a moment, as whole calendar days. */
export function daysAgo(iso: string, now = new Date()): number {
  const then = new Date(iso);
  const start = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Math.max(0, Math.round((start(now) - start(then)) / 86_400_000));
}
