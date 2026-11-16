import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../App';
import { server } from '../mocks/node';
import { json, route } from '../mocks/route';
import { routes } from '../router';

const activity = (id: number, sport: string, moving_s: number, distance_m: number, ascent_m: number) => ({
  id,
  sport,
  name: null,
  load: null,
  distance_m,
  moving_s,
  ascent_m,
  start: '2026-11-01T08:00:00Z',
});

// This month: a run and a ride (2h 30min); last month: a run (2h 0min).
const CALENDAR = [
  { day: '2026-10-05', tsb: null, activities: [activity(1, 'run', 7200, 20000, 150)] },
  { day: '2026-11-03', tsb: null, activities: [activity(2, 'run', 3600, 10000, 100)] },
  { day: '2026-11-10', tsb: null, activities: [activity(3, 'ride', 5400, 40000, 500)] },
];

const GOALS = [
  {
    id: 4,
    period: 'week',
    metric: 'distance',
    sport: 'run',
    target: 20000,
    current: 5000,
    projection: 17500,
    start: '2026-11-16',
    end: '2026-11-23',
  },
  {
    id: 5,
    period: 'month',
    metric: 'ascent',
    sport: null,
    target: 2000,
    current: 600,
    projection: 1200,
    start: '2026-11-01',
    end: '2026-12-01',
  },
];

/** The answers the screens are read from, and every request made (with its body). */
function mockApi() {
  const calls: { method: string; path: string; body: unknown }[] = [];
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
    route('get', '/api/stats/calendar', () => json('/api/stats/calendar', 'get', CALENDAR)),
    route('get', '/api/stats/devices', () =>
      json('/api/stats/devices', 'get', [
        { name: 'fēnix 6X Pro', source: 'garmin', kind: 'activity', last_used: '2026-11-14T08:00:00Z' },
        { name: null, source: 'health_connect', kind: 'health', last_used: '2026-11-16T07:00:00Z' },
      ]),
    ),
    route('get', '/api/goals', () => json('/api/goals', 'get', GOALS)),
    route('post', '/api/goals', () =>
      json('/api/goals', 'post', GOALS[0] as (typeof GOALS)[number], { status: 201 }),
    ),
    route('get', '/api/sources', () =>
      json('/api/sources', 'get', [
        {
          kind: 'garmin',
          configured: true,
          key_expires_on: null,
          expiring_soon: false,
          token_expires_at: null,
          last_sync_at: '2026-11-15T10:00:00Z',
          last_error: null,
        },
      ]),
    ),
    route('get', '/api/wellness/settings', () =>
      json('/api/wellness/settings', 'get', {
        sources: [
          {
            source: 'health_connect',
            available: true,
            connected: false,
            needs_reconnect: false,
            missing: [],
            last_sync_at: null,
            nights: 0,
            days: 0,
          },
        ],
        priority: {},
      }),
    ),
  );
  return calls;
}

function open(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('Monthly activities, Data sources and Goals on Progress (OS-1005)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 10, 16, 12, 0));
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });
  afterEach(() => {
    vi.useRealTimers();
    server.events.removeAllListeners();
    localStorage.clear();
  });

  it("shows this month's time, the devices and the goals' rings, and opens the month's page", async () => {
    mockApi();
    const router = open('/progress');

    const monthly = await screen.findByRole('link', { name: /Monthly activities/ });
    expect(within(monthly).getByText('2h 30min')).toBeInTheDocument();
    expect(within(monthly).getByText('Last month: 2h 0min')).toBeInTheDocument();

    expect(await screen.findByText('fēnix 6X Pro')).toBeInTheDocument();
    expect(screen.getByText('Last used: 2 days ago')).toBeInTheDocument();
    expect(screen.getByText('Health Connect')).toBeInTheDocument();
    expect(screen.getByText('Last used: today')).toBeInTheDocument();

    const goals = await screen.findByRole('link', { name: /^Goals.*\/week/ });
    expect(within(goals).getByText('600 m')).toBeInTheDocument();
    expect(within(goals).getByText(/2,000 m\/month/)).toBeInTheDocument();
    expect(within(goals).getByText(/km\/week/)).toBeInTheDocument();
    expect(goals).toHaveAttribute('href', '/goals');

    await userEvent.click(monthly);
    await waitFor(() => expect(router.state.location.pathname).toBe('/progress/monthly'));
    expect(await screen.findByRole('heading', { name: '2 activities this month' })).toBeInTheDocument();
    // Each sport done in either month, the most time first.
    const sports = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(sports.slice(1)).toEqual(['Run', 'Ride']);
  });

  it('opens Apps and devices from the card, with what is connected', async () => {
    mockApi();
    const router = open('/progress');
    await userEvent.click(await screen.findByRole('link', { name: 'Manage apps and devices' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/progress/sources'));
    expect(await screen.findByRole('heading', { name: 'Apps and devices', level: 1 })).toBeInTheDocument();
    const garmin = await screen.findByRole('link', { name: /Garmin Connect/ });
    expect(garmin).toHaveAttribute('href', '/settings/account');
    expect(within(garmin).getByRole('img', { name: 'Connected' })).toBeInTheDocument();
    const hc = screen.getByRole('link', { name: /Health Connect/ });
    expect(within(hc).queryByRole('img', { name: 'Connected' })).not.toBeInTheDocument();
  });

  it('adds an elevation goal on the Goals page, the kinds already taken greyed out', async () => {
    const calls = mockApi();
    open('/goals');
    // Where each goal stands: what is left, and a monthly goal's pace.
    expect(await screen.findByText('This month · All sports')).toBeInTheDocument();
    expect(screen.getAllByText('To go').length).toBe(2);
    expect(screen.getByText('1,400 m')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add goal' }));
    const dialog = screen.getByRole('dialog');
    const elevation = within(dialog).getByRole('radio', { name: 'Elevation' });
    expect(elevation).toBeEnabled();
    await userEvent.click(within(dialog).getByRole('radio', { name: 'This month' }));
    expect(elevation).toBeDisabled();
    await userEvent.click(within(dialog).getByRole('radio', { name: 'This week' }));
    await userEvent.click(elevation);
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Target \(m\)/ }), '1500');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add goal' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'POST')).toEqual({
        method: 'POST',
        path: '/api/goals',
        body: { period: 'week', metric: 'ascent', sport: null, target: 1500 },
      }),
    );
  });
});
