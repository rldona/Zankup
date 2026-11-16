import { QueryClient } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it, onTestFinished, vi } from 'vitest';

import { App } from './App';
import {
  activityContext,
  activityDetail,
  activitySummary,
  type Context,
  type Detail,
  type Summary,
} from './mocks/activityFixtures';
import { server } from './mocks/node';
import { fail, json, route, type Res } from './mocks/route';
import { routes } from './router';

/** The activity's page in Strava's order (OS-1006 to OS-1008), as the app's. */

type Streams = Res<'/api/activities/{activity_id}/streams', 'get'>;

const n = 61;
const STREAMS = {
  t: Array.from({ length: n }, (_, i) => i * 10),
  dist: Array.from({ length: n }, (_, i) => i * 50),
  alt: Array.from({ length: n }, (_, i) => 100 + (i % 20)),
  hr: Array.from({ length: n }, (_, i) => (i < 30 ? 120 : 160)),
};
const RIDE = activityDetail({
  id: 5,
  name: 'Long ride',
  sport: 'ride',
  start_utc: '2026-11-10T08:00:00Z',
  avg_power: 210,
  streams: { series: ['t', 'dist', 'alt', 'hr', 'power'], sample_count: n },
  splits: [
    { distance_m: 1000, time_s: 120, elevation_change_m: 3, avg_hr: 130 },
    { distance_m: 1000, time_s: 118, elevation_change_m: 2, avg_hr: 140 },
  ],
  metrics: {
    trimp: 80,
    hr_zones: null,
    hr_zone_bounds: null,
    power_zones: [60, 120, 200, 120, 60, 30, 10],
    power_zone_bounds: [0, 140, 190, 230, 270, 320, 400],
    gap_speed: null,
    calories: null,
    calories_estimated: false,
    efficiency: null,
    decoupling: null,
    power_curve: [],
    speed_curve: [],
  },
});
const RUN = activityDetail({
  id: 7,
  name: 'Morning run',
  sport: 'run',
  start_utc: '2026-11-12T08:00:00Z',
  streams: { series: ['t', 'dist', 'alt', 'hr'], sample_count: n },
  splits: [
    { distance_m: 1000, time_s: 330, elevation_change_m: 5, avg_hr: 140 },
    { distance_m: 1000, time_s: 300, elevation_change_m: -3, avg_hr: 150 },
    { distance_m: 1000, time_s: 315, elevation_change_m: 1, avg_hr: 155 },
  ],
});
const GOAL = { goal_id: 3, period: 'year', metric: 'distance', sport: 'run', target: 1_000_000 };
const context = (over: Partial<Context> = {}) =>
  activityContext({
    fitness: {
      before: 40,
      after: 43,
      points: [
        { day: '2026-11-01', ctl: 38 },
        { day: '2026-11-12', ctl: 43 },
      ],
    },
    effort: { score: 84, average: 60, status: 'above' },
    goals: [{ period: 'month', metric: 'distance', sport: null, target: 200_000, current: 150_000 }],
    matches: [
      { activity_id: 2, day: '2025-10-01', speed_mps: 3.0, moving_s: 1000, relative_effort: 50, this: false },
      { activity_id: 7, day: '2026-11-12', speed_mps: 3.2, moving_s: 940, relative_effort: 60, this: true },
    ],
    medals: [{ key: '5k', value: 1500, rank: 1, scope: 'year' }],
    ...over,
  });

/** The mock API answers with this activity (in the feed too), its series and context. */
function mockActivity(
  activity: Detail,
  { streams, ...over }: { streams?: Streams; context?: Context; feed?: Summary[] } = {},
) {
  const calls: string[] = [];
  const listener = ({ request }: { request: Request }) => {
    calls.push(new URL(request.url).pathname);
  };
  server.events.on('request:start', listener);
  onTestFinished(() => server.events.removeListener('request:start', listener));
  server.use(
    route('get', '/api/activities', () =>
      json('/api/activities', 'get', {
        items: over.feed ?? [activitySummary(activity)],
        next_cursor: null,
      }),
    ),
    // Only this activity: /api/activities/months and the like go on to the default handlers.
    route('get', '/api/activities/{activity_id}', ({ params }) =>
      params.activity_id === String(activity.id)
        ? json('/api/activities/{activity_id}', 'get', activity)
        : undefined,
    ),
    route('get', '/api/activities/{activity_id}/context', () =>
      json('/api/activities/{activity_id}/context', 'get', over.context ?? context()),
    ),
    route('get', '/api/activities/{activity_id}/streams', () =>
      streams ? json('/api/activities/{activity_id}/streams', 'get', streams) : fail(404, 'No series'),
    ),
  );
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

/** The cards' titles, top to bottom. */
const titles = (root: HTMLElement) =>
  within(root)
    .getAllByRole('heading', { level: 2 })
    .map((h) => h.textContent ?? '');

/** `expected` appear in this order (others may come between them). */
function expectInOrder(actual: string[], expected: string[]) {
  const at = expected.map((title) => actual.indexOf(title));
  expect(at, `${expected.join(' → ')} in ${actual.join(' · ')}`).not.toContain(-1);
  expect([...at].sort((a, b) => a - b)).toEqual(at);
}

describe('the activity page in Strava order (OS-1006)', () => {
  it('lays a ride out: the context blocks, then each chart with its zones, and no splits', async () => {
    const calls = mockActivity(RIDE, {
      streams: { ...STREAMS, power: Array.from({ length: n }, (_, i) => 150 + i) },
      context: context({
        matches: [],
        medals: [],
        segments: [
          { segment_id: 4, name: 'The climb', distance_m: 2100, elapsed_s: 420, avg_power: 260, rank: 2 },
        ],
        power: { work_kj: 820, intensity: 0.82, load: 96, ftp: 250 },
      }),
    });
    renderAt('/activities/5');
    const page = await screen.findByRole('article');
    await within(page).findByRole('heading', { name: 'Power' });
    await within(page).findByRole('heading', { name: 'Fitness up' });
    // Fetched beside the activity, not after it.
    expect(calls).toContain('/api/activities/5/context');
    const order = titles(page);
    expectInOrder(order, [
      'Fitness up',
      'Relative effort',
      'Goals',
      'Results',
      'Power',
      'Power zones',
      'Heart rate',
      'Heart rate zones',
      'Speed',
      'Altitude',
    ]);
    expect(order).not.toContain('Splits');
    expect(order).not.toContain('Training analysis');
    // Its blocks sit together, beside the charts on a large screen.
    const side = within(page).getByRole('complementary', { name: 'How it compares' });
    expect(within(side).getByText('+3')).toBeInTheDocument();
    expect(within(side).getByText('The climb')).toBeInTheDocument();
    // The power chart carries its work, intensity and load.
    const power = within(page).getByRole('heading', { name: 'Power' }).closest('article');
    expect(power).toHaveTextContent('Total work820 kJ');
    expect(power).toHaveTextContent('Training load96');
    // Power zones as thick bars, the time beside each.
    const zones = within(page).getByRole('heading', { name: 'Power zones' }).closest('section');
    expect(zones).toHaveTextContent('Z33:20190–229 W');
  });

  it('lays a run out: the blocks, the training analysis and splits, pace and its zones', async () => {
    mockActivity(RUN, {
      streams: STREAMS,
      context: context({
        pace_zones: { threshold_mps: 3.3, bounds: [0, 2.574, 2.904, 3.135, 3.3, 3.6] },
      }),
    });
    renderAt('/activities/7');
    const page = await screen.findByRole('article');
    await within(page).findByRole('heading', { name: 'Pace zones' });
    expectInOrder(titles(page), [
      'Fitness up',
      'Matched runs',
      'Relative effort',
      'Goals',
      'Results',
      'Training analysis',
      'Splits',
      'Pace',
      'Grade-adjusted pace',
      'Pace zones',
      'Heart rate',
      'Heart rate zones',
      'Elevation',
    ]);
    // Pace with its moving and elapsed time and the fastest split.
    const pace = within(page).getByRole('heading', { name: 'Pace' }).closest('article');
    expect(pace).toHaveTextContent('Moving time9:50');
    expect(pace).toHaveTextContent('Fastest split5:00 /km');
    // The splits as Strava's table: Km · Pace · bar · Elev. · HR.
    const splits = within(page).getByRole('heading', { name: 'Splits' }).closest('section');
    if (!splits) throw new Error('no splits');
    expect(
      within(splits)
        .getAllByRole('columnheader')
        .map((c) => c.textContent),
    ).toEqual(['Km', 'Pace', 'Elev.', 'HR']);
    expect(within(splits).getByRole('row', { name: '2 5:00 -3 150' })).toBeInTheDocument();
    // The matched runs and the analysis have their own pages.
    expect(within(page).getByRole('link', { name: 'See 1 matched activity' })).toHaveAttribute(
      'href',
      '/activities/7/matches',
    );
    expect(within(page).getByRole('link', { name: 'View workout' })).toHaveAttribute(
      'href',
      '/activities/7/analysis',
    );
  });

  it('lists the matched activities by year, each opening its activity', async () => {
    mockActivity(RUN);
    const router = renderAt('/activities/7/matches');
    expect(await screen.findByRole('heading', { name: 'Matched runs', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^2025/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: /October 1/ }));
    expect(router.state.location.pathname).toBe('/activities/2');
  });
});

describe('the training analysis page (OS-1007)', () => {
  it('picks the split under the pointer, in the box and in the list, and a click keeps it', async () => {
    mockActivity(RUN, {
      streams: STREAMS,
      context: context({
        pace_zones: { threshold_mps: 3.3, bounds: [0, 2.574, 2.904, 3.135, 3.3, 3.6] },
      }),
    });
    renderAt('/activities/7/analysis');
    const chart = await screen.findByRole('slider', { name: 'Training analysis' });
    const box = document.querySelector('.splitbox');
    // The first split from the start, as Strava's.
    await waitFor(() => expect(box).toHaveTextContent('Split 1 · 5:30 /km · Zone 3 · Tempo'));
    vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 300, 300));
    // Over the third split (the last third of the chart).
    fireEvent.mouseMove(chart, { clientX: 250 });
    expect(box).toHaveTextContent('Split 3 · 5:15 /km · Zone 4 · Threshold');
    const rows = screen.getAllByRole('row');
    expect(rows[2]).toHaveClass('on');
    expect(rows[0]).not.toHaveClass('on');
    // Leaving the chart goes back to the kept split; a click keeps the one under the pointer.
    fireEvent.mouseLeave(chart);
    expect(box).toHaveTextContent('Split 1');
    fireEvent.click(chart, { clientX: 150 });
    fireEvent.mouseLeave(chart);
    expect(box).toHaveTextContent('Split 2 · 5:00 /km · Zone 5 · VO2 max');
    // The keys step through the splits too.
    fireEvent.keyDown(chart, { key: 'ArrowRight' });
    expect(box).toHaveTextContent('Split 3');
  });
});

describe('goals reached (OS-1008)', () => {
  it('shows a capsule on the feed card and a banner above the Coach on the page', async () => {
    mockActivity(RUN, {
      feed: [activitySummary({ ...RUN, goals_reached: [GOAL] })],
      context: context({ goals_reached: [GOAL] }),
    });
    const router = renderAt('/activities');
    const card = await screen.findByRole('link', { name: /Morning run/ });
    expect(card).toHaveTextContent('Goal reached · Yearly: Distance goal · 1,000 km · Run');

    await userEvent.click(card);
    const banner = await screen.findByRole('link', { name: /Well done! You reached your goal/ });
    expect(banner).toHaveTextContent('Yearly: Distance goal · 1,000 km · Run');
    expect(banner).toHaveAttribute('href', '/goals');
    // Above the Coach: right after the figures.
    expect(banner.previousElementSibling).toHaveClass('figures');
    expect(router.state.location.pathname).toBe('/activities/7');
  });
});
