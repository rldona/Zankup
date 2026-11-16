import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

const SETTINGS: Res<'/api/settings', 'get'> = {
  theme: 'system',
  language: null,
  units: 'metric',
  hr_max: 190,
  hr_rest: 50,
  hr_max_estimate: 188,
  zone_model: 'max',
  lthr: null,
  ftp: null,
  privacy_zones: [],
  notifications: {
    activity: true,
    record: true,
    sync_error: true,
    garmin_key: true,
    estimate: true,
    import: true,
    health_reauth: true,
    weekly: false,
  },
  ai: { enabled: false, summaries: false, weekly: false },
};
const totals = (count: number, km: number) => ({
  count,
  distance_m: km * 1000,
  moving_s: count * 3600,
  ascent_m: 100 * count,
  load: 80 * count,
});
const ESTIMATES: Res<'/api/stats/estimates', 'get'> = [
  {
    kind: 'ftp',
    value: 192.3,
    method: 'ftp_20min',
    day: '2026-11-08',
    sources: [{ id: 3, name: 'ROUVY', sport: 'ride', day: '2026-10-11' }],
    history: [
      { day: '2026-10-01', value: 185 },
      { day: '2026-11-08', value: 192.3 },
    ],
  },
  {
    kind: 'critical_speed',
    value: 3.09,
    method: 'critical_speed',
    day: '2026-11-08',
    sources: [],
    history: [{ day: '2026-11-08', value: 3.09 }],
  },
  { kind: 'race_10k', value: 3211, method: 'riegel:10', day: '2026-11-08', sources: [], history: [] },
];

const COMPARISON: Res<'/api/stats/strava-comparison', 'get'> = {
  metrics: [
    {
      metric: 'effort',
      count: 12,
      r2: 0.912,
      mae: 4.2,
      ratio: 1.03,
      points: [
        { activity_id: 7, ours: 80, strava: 78 },
        { activity_id: 6, ours: 60, strava: 64 },
      ],
      largest: [{ activity_id: 6, ours: 60, strava: 64 }],
    },
  ],
  effort_scale: null,
};
const COMPARE_TITLE = 'How these metrics compare';

type Call = { method: string; path: string; body: unknown };

/** The answers these screens are read from (`extra` first), and every request made with its body. */
function mockApi(...extra: Parameters<typeof server.use>) {
  const calls: Call[] = [];
  server.events.on('request:start', ({ request }) => {
    void (async () => {
      const text = request.method === 'GET' ? '' : await request.clone().text();
      calls.push({
        method: request.method,
        path: new URL(request.url).pathname,
        body: text ? JSON.parse(text) : null,
      });
    })();
  });
  server.use(
    route('get', '/api/settings', () => json('/api/settings', 'get', SETTINGS)),
    route('get', '/api/stats/years', () => json('/api/stats/years', 'get', [2026, 2025])),
    route('get', '/api/stats/fitness', () =>
      json(
        '/api/stats/fitness',
        'get',
        Array.from({ length: 30 }, (_, i) => ({
          day: `2026-10-${String(i + 1).padStart(2, '0')}`,
          load: 50,
          ctl: 40 + i / 10,
          atl: 30,
          tsb: 8,
        })),
      ),
    ),
    route('get', '/api/stats/summary', () =>
      json('/api/stats/summary', 'get', {
        current: totals(10, 100),
        previous: totals(5, 40),
        by_sport: { run: totals(10, 100) },
      }),
    ),
    route('get', '/api/stats/volume', () =>
      json('/api/stats/volume', 'get', [
        { start: '2026-10-01', totals: totals(3, 30), by_sport: { run: totals(3, 30) } },
      ]),
    ),
    route('get', '/api/stats/curves', () =>
      json('/api/stats/curves', 'get', [
        { duration_s: 60, value: 4.2, activity_id: 7, day: '2026-10-05' },
        { duration_s: 1200, value: 3.3, activity_id: 7, day: '2026-10-05' },
      ]),
    ),
    route('get', '/api/stats/records', () =>
      json('/api/stats/records', 'get', {
        all_time: [{ distance_key: '5k', distance_m: 5000, time_s: 1500, activity_id: 7, day: '2026-10-05' }],
        period: [{ distance_key: '5k', distance_m: 5000, time_s: 1500, activity_id: 7, day: '2026-10-05' }],
        progression: {
          '5k': [
            { distance_key: '5k', distance_m: 5000, time_s: 1600, activity_id: 6, day: '2026-01-05' },
            { distance_key: '5k', distance_m: 5000, time_s: 1500, activity_id: 7, day: '2026-10-05' },
          ],
        },
      }),
    ),
    route('get', '/api/stats/estimates', () => json('/api/stats/estimates', 'get', ESTIMATES)),
    route('get', '/api/stats/progress', () =>
      json('/api/stats/progress', 'get', [
        { year: 2025, cumulative_m: [0, 5000, 9000] },
        { year: 2026, cumulative_m: [0, 6000] },
      ]),
    ),
    route('get', '/api/stats/efficiency', () => json('/api/stats/efficiency', 'get', [])),
    route('get', '/api/week', () =>
      json('/api/week', 'get', {
        today: '2026-11-09',
        days: [],
        this_week: { week_start: '2026-11-09', count: 0, distance_m: 0, moving_s: 0, ascent_m: 0 },
        previous: [],
        streak_weeks: 3,
        records: [],
      }),
    ),
    route('get', '/api/goals', () => json('/api/goals', 'get', [])),
    route('get', '/api/events', () =>
      json('/api/events', 'get', [
        {
          id: 1,
          kind: 'record',
          payload: { activity_id: 7, distance_key: '5k', time_s: 1500 },
          created_at: '2026-10-05T10:00:00Z',
        },
      ]),
    ),
    route('get', '/api/engine', () =>
      json('/api/engine', 'get', {
        running: true,
        reason: 'x',
        done: 12,
        total: 207,
        computed: 12,
        activities: 207,
        metrics_version: 2,
      }),
    ),
  );
  if (extra.length) server.use(...extra);
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('phase 3 screens', () => {
  afterEach(() => server.events.removeAllListeners());

  it('stats: now, summary against last year, records and a period in the URL', async () => {
    mockApi();
    const router = renderAt('/stats');
    expect(await screen.findByText('Recomputing your metrics: 12 of 207 activities.')).toBeInTheDocument();
    expect((await screen.findAllByText('+100 %')).length).toBeGreaterThan(0);
    expect(screen.getByText('+150 %')).toBeInTheDocument(); // 100 km against 40
    // The period's bests, not the records of all time (OS-1010).
    expect(await screen.findByRole('heading', { name: 'Bests in this period' })).toBeInTheDocument();
    // The period as on the app (OS-912): a year moved with arrows, or a run of months.
    const year = new Date().getFullYear();
    await userEvent.click(screen.getByRole('radio', { name: 'Year' }));
    expect(router.state.location.search).toBe(`?period=${year}`);
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await userEvent.click(screen.getByRole('radio', { name: 'Months' }));
    const month = String(new Date().getMonth() + 1).padStart(2, '0');
    expect(router.state.location.search).toBe(`?period=${year}-${month}`);
    // Estimates have their own page now (OS-913).
    expect(screen.queryByText(/Riegel's formula/)).not.toBeInTheDocument();
  });

  it("stats by its period: the period's bests open their best times, the rest lives elsewhere (OS-1010)", async () => {
    mockApi(
      route('get', '/api/stats/strava-comparison', () =>
        json('/api/stats/strava-comparison', 'get', COMPARISON),
      ),
    );
    const router = renderAt('/stats');
    // Without a period in the URL, months from last month to this one (#285).
    const months = await screen.findByRole('radio', { name: 'Months' });
    expect(months).toHaveAttribute('aria-checked', 'true');
    expect(screen.getAllByRole('button', { name: 'Previous' })).toHaveLength(2);
    // The summary first (#314), best efforts on power (#286).
    await screen.findByRole('heading', { name: 'Summary' });
    const titles = [...document.querySelectorAll('.statgrid > .panel h2')].map((h) => h.textContent);
    expect(titles[0]).toBe('Summary');
    const curves = screen.getByRole('group', { name: 'Best efforts' });
    expect(within(curves).getByRole('button', { name: 'Power' })).toHaveAttribute('aria-pressed', 'true');
    // The period's bests: the distance opens its best times, and a link leads to all of them.
    const bests = (await screen.findByRole('heading', { name: 'Bests in this period' })).closest('section');
    const card = within(bests as HTMLElement);
    expect(card.getByRole('link', { name: '5 km' })).toHaveAttribute('href', '/progress/bests?key=5k');
    expect(card.getByRole('link', { name: '25:00' })).toHaveAttribute('href', '/activities/7');
    expect(card.getByRole('link', { name: 'See all your best times' })).toHaveAttribute(
      'href',
      '/progress/bests',
    );
    // Year by year and the comparison with Strava are no longer on Stats.
    expect(screen.queryByText('2025')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: COMPARE_TITLE })).not.toBeInTheDocument();
    // Year by year, from Stats' menu, on its own page: a click on a year makes it stand out.
    await userEvent.click(screen.getByRole('link', { name: 'Year over year' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/stats/years'));
    expect(await screen.findByRole('heading', { name: 'Year over year', level: 1 })).toBeInTheDocument();
    const year = screen.getByRole('button', { name: /2025/ });
    expect(year).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(year);
    expect(year).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /2026/ })).toHaveAttribute('aria-pressed', 'false');
  });

  it('stats: no bests in the period says so, and still leads to all of them', async () => {
    mockApi(
      route('get', '/api/stats/records', () =>
        json('/api/stats/records', 'get', {
          all_time: [
            { distance_key: '5k', distance_m: 5000, time_s: 1500, activity_id: 7, day: '2025-10-05' },
          ],
          period: [],
          progression: {},
        }),
      ),
    );
    renderAt('/stats');
    expect(await screen.findByText('No bests in this period.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See all your best times' })).toHaveAttribute(
      'href',
      '/progress/bests',
    );
  });

  it('import: how the metrics compare with Strava, under the imports (OS-1010)', async () => {
    mockApi(
      route('get', '/api/imports', () => json('/api/imports', 'get', [])),
      route('get', '/api/stats/strava-comparison', () =>
        json('/api/stats/strava-comparison', 'get', COMPARISON),
      ),
    );
    renderAt('/settings/import');
    expect(await screen.findByRole('heading', { name: COMPARE_TITLE })).toBeInTheDocument();
    expect(screen.getByText('0.912')).toBeInTheDocument(); // R²
  });

  it('notifications: the bell on Home opens what the engine noticed (OS-916)', async () => {
    mockApi();
    const router = renderAt('/');
    const header = within(await screen.findByRole('main'));
    await userEvent.click(await header.findByRole('link', { name: 'Notifications' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/notifications'));
    expect(await screen.findByText('New 5 km record: 25:00')).toBeInTheDocument();
  });

  it("goals: add one from Goals (You's old Goals tab leads there)", async () => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    let goals: Res<'/api/goals', 'get'> = [];
    const calls = mockApi(
      route('get', '/api/goals', () => json('/api/goals', 'get', goals)),
      route('post', '/api/goals', async ({ request }) => {
        const body = await request.json();
        goals = [
          {
            id: 1,
            period: 'week',
            metric: 'distance',
            sport: null,
            target: body.target,
            start: '2026-11-09',
            end: '2026-11-16',
            current: 10000,
            projection: 70000,
          },
        ];
        return json('/api/goals', 'post', goals[0] as (typeof goals)[number], { status: 201 });
      }),
      route('get', '/api/profile/summary', () =>
        json('/api/profile/summary', 'get', {
          year: 2026,
          this_year: [],
          all_time: [],
          since: null,
          records: [],
          streak_weeks: 0,
          device: null,
        }),
      ),
    );
    const router = renderAt('/profile?tab=goals');
    await waitFor(() => expect(router.state.location.pathname).toBe('/goals'));
    await userEvent.click(await screen.findByRole('button', { name: 'Add goal' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Target/ }), '40');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add goal' }));
    expect(await screen.findByText('On track: about 70.00 km by the end')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      period: 'week',
      metric: 'distance',
      sport: null,
      target: 40000,
    });
  });

  it('calendar: bubbles per activity, form per day and the week totals', async () => {
    mockApi(
      route('get', '/api/stats/calendar', () =>
        json(
          '/api/stats/calendar',
          'get',
          Array.from({ length: 35 }, (_, i) => {
            const d = new Date(2026, 9, 26 + i);
            const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
            return {
              day,
              tsb: i === 10 ? -5 : null,
              activities:
                i === 10
                  ? [
                      {
                        id: 9,
                        sport: 'run',
                        name: 'Tempo',
                        load: 90,
                        distance_m: 10000,
                        moving_s: 3000,
                        start: `${day}T08:00:00Z`,
                      },
                    ]
                  : [],
            };
          }),
        ),
      ),
    );
    renderAt('/stats/calendar?month=2026-11');
    expect(await screen.findByRole('link', { name: 'Tempo · 50:00' })).toHaveAttribute(
      'href',
      '/activities/9',
    );
    expect(screen.getByText('-5')).toBeInTheDocument();
    expect(screen.getAllByText('10.00 km').length).toBe(1);
    expect(screen.getAllByText('50 min · 1 activity').length).toBe(1);
  });

  it('routes and segments lists', async () => {
    mockApi(
      route('get', '/api/routes', () =>
        json('/api/routes', 'get', [
          {
            id: 4,
            sport: 'run',
            name: 'River loop',
            route: null,
            count: 3,
            best: {
              id: 5,
              name: 'River loop',
              day: '2026-10-01',
              moving_s: 1500,
              distance_m: 5000,
              avg_hr: 150,
            },
            latest: {
              id: 6,
              name: 'River loop',
              day: '2026-11-01',
              moving_s: 1600,
              distance_m: 5000,
              avg_hr: 148,
            },
          },
        ]),
      ),
      route('get', '/api/segments', () =>
        json('/api/segments', 'get', [
          { id: 2, name: 'The hill', track: '', distance_m: 800, ascent_m: 40, efforts: 4, best_s: 230 },
        ]),
      ),
    );
    renderAt('/routes');
    expect(await screen.findByText(/3 times/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /River loop/ })).toHaveAttribute('href', '/routes/4');
  });

  it('settings: the zone model recomputes, with the estimates as hints', async () => {
    const calls = mockApi(
      route('patch', '/api/settings', async ({ request }) =>
        json('/api/settings', 'patch', {
          ...SETTINGS,
          ...((await request.json()) as Partial<typeof SETTINGS>),
        }),
      ),
      route('get', '/api/sources', () => json('/api/sources', 'get', [])),
      route('get', '/api/auth/devices', () => json('/api/auth/devices', 'get', [])),
      route('get', '/api/stats/estimates', () =>
        json('/api/stats/estimates', 'get', [
          ...ESTIMATES,
          { kind: 'lthr', value: 161, method: 'lthr_60min', day: '2026-11-08', sources: [], history: [] },
        ]),
      ),
    );
    renderAt('/settings/heart');
    const model = await screen.findByRole('combobox', { name: 'Heart rate zones' });
    expect(screen.getByText('Estimated: 161 bpm')).toBeInTheDocument();
    expect(screen.getByText('Estimated: 192 W')).toBeInTheDocument();
    await userEvent.selectOptions(model, 'reserve');
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PATCH',
        path: '/api/settings',
        body: { zone_model: 'reserve' },
      }),
    );
    const status = await screen.findAllByRole('status');
    expect(within(status[0] as HTMLElement).getByText(/Recomputing/)).toBeInTheDocument();
  });
});
