import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

type HealthDay = Res<'/api/wellness/days', 'get'>[number];
type Night = NonNullable<HealthDay['sleep']>;
type Source = Night['source'];
type HealthSettings = Res<'/api/wellness/settings', 'get'>;

const night = (day: string, source: Source, others: Source[] = []): Night => ({
  source,
  start_utc: `${day}T00:00:00Z`,
  end_utc: `${day}T07:30:00Z`,
  utc_offset_s: 3600,
  asleep_s: 7 * 3600,
  awake_s: 1800,
  light_s: 4 * 3600,
  deep_s: 3600,
  rem_s: 2 * 3600,
  score: 86,
  others,
});
const value = (v: number, source: Source, others: Source[] = []) => ({ value: v, source, others });
const DAYS: HealthDay[] = [
  {
    day: '2026-11-07',
    sleep: night('2026-11-07', 'garmin'),
    resting_hr: value(51, 'garmin'),
    hrv: value(45, 'garmin'),
    spo2: null,
    respiration: null,
    steps: value(9000, 'garmin'),
    tsb: -4,
    readiness: { score: 61, hrv: 55, resting_hr: 60, sleep: 86, form: 54 },
  },
  {
    day: '2026-11-08',
    sleep: night('2026-11-08', 'google_health', ['garmin']),
    resting_hr: value(49, 'garmin', ['google_health']),
    hrv: value(58, 'google_health', ['garmin']),
    spo2: value(96, 'google_health'),
    respiration: null,
    steps: value(10500, 'google_health'),
    tsb: 2,
    readiness: { score: 74, hrv: 80, resting_hr: 65, sleep: 86, form: 63 },
  },
];
const NIGHT: Res<'/api/wellness/nights/{day}', 'get'> = {
  day: '2026-11-08',
  chosen: night('2026-11-08', 'google_health', ['garmin']),
  stages: [
    { offset_s: 0, duration_s: 1800, stage: 'awake' },
    { offset_s: 1800, duration_s: 3600, stage: 'deep' },
    { offset_s: 5400, duration_s: 7200, stage: 'light' },
  ],
  alternatives: [night('2026-11-08', 'garmin')],
};
const status = (source: Source, extra: Partial<HealthSettings['sources'][number]> = {}) => ({
  source,
  available: true,
  connected: true,
  needs_reconnect: false,
  missing: [],
  last_sync_at: '2026-11-09T07:00:00Z',
  nights: 10,
  days: 12,
  ...extra,
});
const HEALTH_SETTINGS: HealthSettings = {
  sources: [
    status('garmin'),
    // Disconnected, but what it synced before is still there: it can still be preferred.
    status('google_health', { connected: false, nights: 5, days: 5 }),
    status('health_connect', { connected: false, nights: 0, days: 0, last_sync_at: null }),
  ],
  priority: { sleep: [], resting_hr: [], hrv: [], spo2: [], respiration: [], steps: [] },
};

const daysAre = (days: HealthDay[]) =>
  route('get', '/api/wellness/days', () => json('/api/wellness/days', 'get', days));
const settingsAre = (settings: HealthSettings) =>
  route('get', '/api/wellness/settings', () => json('/api/wellness/settings', 'get', settings));

/** The fixtures above in place of the demo athlete's health; the rest of the API is the mock's. */
function useFixtures() {
  server.use(
    daysAre(DAYS),
    route('get', '/api/wellness/nights/{day}', () => json('/api/wellness/nights/{day}', 'get', NIGHT)),
    settingsAre(HEALTH_SETTINGS),
  );
}

/** Records every request: method, path and JSON body (null for GET). */
function record() {
  const calls: { method: string; path: string; body: unknown }[] = [];
  server.events.on('request:start', async ({ request }) => {
    const text = request.method === 'GET' ? '' : await request.clone().text();
    calls.push({
      method: request.method,
      path: new URL(request.url).pathname,
      body: text ? (JSON.parse(text) as unknown) : null,
    });
  });
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('phase 6: health', () => {
  afterEach(() => {
    server.events.removeAllListeners();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('shows last night with its stages, today and where each figure comes from', async () => {
    // The fixtures' last night is the one to November 8: "Last night" only on that day.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-08T12:00:00'));
    useFixtures();
    renderAt('/health');
    expect(await screen.findByRole('heading', { name: 'Last night' })).toBeInTheDocument();
    expect(screen.getAllByText(/November 8/).length).toBeGreaterThan(0);
    // The source is a mark in the corner, named in full for screen readers.
    expect(
      screen.getAllByRole('img', { name: /From Google Health · also recorded by Garmin/ })[0],
    ).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'Sleep stages through the night' })).toBeInTheDocument();
    expect(screen.getByText('86')).toBeInTheDocument();
    const readiness = screen.getByRole('heading', { name: 'Readiness' }).closest('article');
    expect(readiness && within(readiness).getByText('74')).toBeInTheDocument();
    const hrv = screen.getByRole('heading', { name: 'HRV' }).closest('article');
    expect(hrv && within(hrv).getByText('58 ms')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Readiness and form' })).toBeInTheDocument();
  });

  it('picks the period from a segmented control in the header', async () => {
    const starts: string[] = [];
    useFixtures();
    server.use(
      route('get', '/api/wellness/days', ({ request }) => {
        starts.push(new URL(request.url).searchParams.get('start') ?? '');
        return json('/api/wellness/days', 'get', DAYS);
      }),
    );
    const router = renderAt('/health');
    const periods = await screen.findByRole('group', { name: 'Period' });
    expect(within(periods).getByRole('button', { name: '30 days' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(within(periods).getByRole('button', { name: '90 days' }));
    await waitFor(() => expect(router.state.location.search).toBe('?days=90'));
    expect(within(periods).getByRole('button', { name: '90 days' })).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() => expect(new Set(starts).size).toBe(2));
  });

  it('keeps its charts on screen while another period loads, so they slide to it (OS-1012)', async () => {
    let release = () => {};
    const later = new Promise<void>((resolve) => (release = resolve));
    let asked = 0;
    useFixtures();
    server.use(
      route('get', '/api/wellness/days', async () => {
        asked += 1;
        if (asked > 1) await later;
        return json('/api/wellness/days', 'get', DAYS);
      }),
    );
    renderAt('/health');
    const sleep = (await screen.findByRole('heading', { name: 'Sleep' })).closest('section');
    const chart = sleep?.querySelector('[role="img"]');
    expect(chart).toBeTruthy();
    await userEvent.click(
      within(screen.getByRole('group', { name: 'Period' })).getByRole('button', { name: '90 days' }),
    );
    await waitFor(() => expect(asked).toBe(2));
    // The same chart, not a skeleton, while the new period is on its way, and after it arrives.
    expect(chart).toBeInTheDocument();
    expect(document.querySelector('.skeleton, [aria-busy="true"]')).toBeNull();
    release();
    await waitFor(() => expect(chart).toBeInTheDocument());
    expect(
      screen.getByRole('heading', { name: 'Sleep' }).closest('section')?.querySelector('[role="img"]'),
    ).toBe(chart);
  });

  it('without data, it points to connecting a source', async () => {
    useFixtures();
    server.use(daysAre([]));
    renderAt('/health');
    expect(await screen.findByText(/No health data yet/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Connect a source/ })).toHaveAttribute(
      'href',
      '/settings/health',
    );
  });

  it('fetch now says what it brings, and that it is done once the source has synced', async () => {
    let synced = false;
    useFixtures();
    server.use(
      route('post', '/api/wellness/sync', () => {
        synced = true;
        return Response.json({ queued: ['garmin'] }, { status: 202 });
      }),
      // The job has run by the first time it is asked: Garmin's last sync moves on.
      route('get', '/api/wellness/settings', () =>
        json(
          '/api/wellness/settings',
          'get',
          synced
            ? { ...HEALTH_SETTINGS, sources: [status('garmin', { last_sync_at: '2026-11-09T09:00:00Z' })] }
            : HEALTH_SETTINGS,
        ),
      ),
    );
    const calls = record();
    renderAt('/health');
    await userEvent.click(await screen.findByRole('button', { name: 'Fetch now' }));
    expect(await screen.findByText('Bringing your health data from Garmin…')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fetch now' })).toBeDisabled();
    expect(await screen.findByText('Health data up to date.', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fetch now' })).toBeEnabled();
    const days = calls.filter((c) => c.path === '/api/wellness/days').length;
    expect(days).toBeGreaterThan(1); // refreshed after the sync
  }, 10_000);

  it('fetch now with no source connected says so', async () => {
    useFixtures();
    server.use(route('post', '/api/wellness/sync', () => Response.json({ queued: [] }, { status: 202 })));
    renderAt('/health');
    await userEvent.click(await screen.findByRole('button', { name: 'Fetch now' }));
    expect(await screen.findByText('No health source is connected.')).toBeInTheDocument();
  });

  it('settings: connect Google Health, choose a source per metric and delete one source’s data', async () => {
    useFixtures();
    server.use(
      route('put', '/api/wellness/priority', () =>
        json('/api/wellness/priority', 'put', {
          ...HEALTH_SETTINGS,
          priority: { ...HEALTH_SETTINGS.priority, sleep: ['garmin'] },
        }),
      ),
    );
    const calls = record();
    vi.stubGlobal('confirm', () => true);
    renderAt('/settings?health=connected');
    expect(await screen.findByRole('heading', { name: 'Health' })).toBeInTheDocument();
    expect(await screen.findByRole('status')).toHaveTextContent('Google Health is connected');
    expect(screen.getByRole('link', { name: 'Connect Google Health' })).toHaveAttribute(
      'href',
      '/api/wellness/google/start',
    );
    expect(screen.getByText(/10 nights, 12 days/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /^Sleep:/ }));
    await userEvent.click(screen.getByRole('option', { name: 'Garmin' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ priority: { sleep: ['garmin'] } }),
    );
    const [garminDelete] = screen.getAllByRole('button', { name: 'Delete its data' }); // Garmin's row is first
    if (garminDelete) await userEvent.click(garminDelete);
    await waitFor(() =>
      expect(calls.some((c) => c.method === 'DELETE' && c.path === '/api/wellness/sources/garmin/data')).toBe(
        true,
      ),
    );
  });

  it('settings: brings a year of health history (OS-917)', async () => {
    // The mock queues the sources it fetches from: Garmin and Google Health.
    const calls = record();
    renderAt('/settings/health');
    await userEvent.click(await screen.findByRole('button', { name: 'Bring the last year' }));
    expect(await screen.findByText(/Under way: Garmin, Google Health/)).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'POST' && c.path === '/api/wellness/history')).toBe(true);
  });
});
