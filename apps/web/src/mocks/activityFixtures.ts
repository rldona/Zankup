/**
 * Whole answers for tests that need an activity with exact figures: a test names what matters to it
 * and the rest is empty, so `json(...)` still checks every answer against the API's types.
 */
import type { Res } from './route';

export type Detail = Res<'/api/activities/{activity_id}', 'get'>;
export type Summary = Res<'/api/activities', 'get'>['items'][number];
export type Context = Res<'/api/activities/{activity_id}/context', 'get'>;
export type Gear = Res<'/api/gear', 'get'>[number];

/** An activity's detail: a short run, with nothing but what `over` gives it. */
export function activityDetail(over: Partial<Detail> & Pick<Detail, 'id'>): Detail {
  return {
    name: 'Morning run',
    sport: 'run',
    sub_sport: null,
    workout_type: null,
    source: 'garmin',
    start_utc: '2026-11-12T08:00:00Z',
    timezone: '+01:00',
    duration_s: 600,
    moving_s: 590,
    distance_m: 3000,
    ascent_m: 40,
    avg_hr: 140,
    avg_power: null,
    has_gps: false,
    route: null,
    photo: null,
    photos: 0,
    exercises: null,
    goals_reached: [],
    description: null,
    gear: null,
    photo_list: [],
    weather: null,
    edited_fields: [],
    original_format: null,
    strava_id: null,
    descent_m: null,
    max_hr: null,
    normalized_power: null,
    calories: null,
    training_effect: null,
    device: null,
    bbox: null,
    has_fit: false,
    imported_at: '2026-11-12T09:00:00Z',
    laps: [],
    splits: [],
    best_efforts: [],
    sets: [],
    metrics: null,
    route_group: null,
    segments: [],
    streams: null,
    ...over,
  };
}

/** The detail's fields that the feed's card does not have. */
const DETAIL_ONLY = [
  'description',
  'gear',
  'photo_list',
  'weather',
  'edited_fields',
  'original_format',
  'strava_id',
  'descent_m',
  'max_hr',
  'normalized_power',
  'calories',
  'training_effect',
  'device',
  'bbox',
  'has_fit',
  'imported_at',
  'laps',
  'splits',
  'best_efforts',
  'sets',
  'metrics',
  'route_group',
  'segments',
  'streams',
] as const satisfies readonly (keyof Detail)[];

/** The feed's card of an activity: its detail's summary fields. */
export function activitySummary(over: Partial<Detail> & Pick<Detail, 'id'>): Summary {
  const only: readonly string[] = DETAIL_ONLY;
  return Object.fromEntries(
    Object.entries(activityDetail(over)).filter(([key]) => !only.includes(key)),
  ) as Summary;
}

/** An activity's context with nothing in it. */
export function activityContext(over: Partial<Context> = {}): Context {
  return {
    fitness: null,
    effort: null,
    goals: [],
    matches: [],
    medals: [],
    segments: [],
    power: null,
    pace_zones: null,
    goals_reached: [],
    ...over,
  };
}

/** A pair of shoes, unused but for `over`. */
export function gearItem(over: Partial<Gear> & Pick<Gear, 'id' | 'name'>): Gear {
  return {
    kind: 'shoe',
    brand: null,
    model: null,
    sports: ['run'],
    initial_m: 0,
    limit_m: null,
    retired: false,
    habitual: false,
    distance_m: 0,
    activities: 0,
    moving_s: 0,
    first_used: null,
    last_used: null,
    over_limit: false,
    ...over,
  };
}
