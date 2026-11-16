import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';
import { gearItem } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { fail, json, route, type Res } from './mocks/route';
import { routes } from './router';

type Settings = Res<'/api/settings', 'get'>;
type Import = Res<'/api/imports/{import_id}', 'get'>;

const NOTIFICATIONS = {
  activity: true,
  record: true,
  sync_error: true,
  garmin_key: true,
  estimate: false,
  import: true,
  health_reauth: true,
  weekly: true,
};
const SETTINGS: Settings = {
  theme: 'system',
  language: null,
  units: 'metric',
  hr_max: 190,
  hr_rest: 50,
  hr_max_estimate: 188,
  zone_model: 'max',
  lthr: null,
  ftp: null,
  privacy_zones: [{ id: 'home', name: 'Home', lat: 51.5, lon: -0.16, radius_m: 400 }],
  notifications: NOTIFICATIONS,
  ai: { enabled: false, summaries: false, weekly: false },
};
const OLD_TRAINERS = gearItem({
  id: 1,
  name: 'Old trainers',
  brand: 'Brand',
  model: 'Model',
  limit_m: 800000,
  distance_m: 812000,
  activities: 90,
  moving_s: 300000,
  first_used: '2025-01-01T08:00:00Z',
  last_used: '2026-11-01T08:00:00Z',
  over_limit: true,
});
const GEAR = [OLD_TRAINERS];
const IMPORT: Import = {
  id: 5,
  kind: null,
  status: 'uploading',
  filename: 'export.zip',
  size: 6,
  received: 0,
  total: 0,
  processed: 0,
  created: 0,
  merged: 0,
  skipped: 0,
  failed: 0,
  photos: 0,
  errors: [],
  error: null,
  created_at: '2026-11-09T09:00:00Z',
  finished_at: null,
};
const totals = (count: number, km: number) => ({
  count,
  distance_m: km * 1000,
  moving_s: count * 3600,
  ascent_m: 100 * count,
  load: 80 * count,
});

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('phase 4 screens', () => {
  it('gear: mileage against its limit, and a warning past it', async () => {
    const posted: unknown[] = [];
    server.use(
      route('get', '/api/gear', () => json('/api/gear', 'get', GEAR)),
      route('post', '/api/gear', async ({ request }) => {
        posted.push(await request.json());
        return json('/api/gear', 'post', OLD_TRAINERS, { status: 201 });
      }),
    );
    renderAt('/gear');
    expect(await screen.findByRole('heading', { name: 'Old trainers' })).toBeInTheDocument();
    expect(screen.getByText(/Past its limit/)).toBeInTheDocument();
    expect(screen.getByRole('meter', { name: 'Mileage against its limit' })).toHaveAttribute(
      'aria-valuenow',
      '100',
    );
    expect(screen.getByText(/90 activities/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add gear' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), 'New bike');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Type' }), 'bike');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Ride' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toMatchObject({ name: 'New bike', kind: 'bike', sports: ['ride'], limit_m: 800000 });
  });

  it('import: a file goes up in parts and its import is listed with its counts', async () => {
    let listed: Import[] = [];
    const parts: string[] = [];
    const created: unknown[] = [];
    server.use(
      route('get', '/api/imports', () => json('/api/imports', 'get', listed)),
      route('post', '/api/imports', async ({ request }) => {
        created.push(await request.json());
        return json(
          '/api/imports',
          'post',
          {
            import: IMPORT,
            upload: { url: 'http://api.example.com/api/direct/uploads/5', token: 'tok', part_size: 4 },
          },
          { status: 201 },
        );
      }),
      route('put', '/api/direct/uploads/{import_id}/{index}', ({ params, request }) => {
        if (params.index === '0') parts.push(new URL(request.url).searchParams.get('token') ?? '');
        return new Response(null, { status: 204 });
      }),
      route('post', '/api/imports/{import_id}/finish', () => {
        const done: Import = {
          ...IMPORT,
          kind: 'strava',
          status: 'done',
          received: 6,
          total: 1816,
          processed: 1816,
          created: 1609,
          merged: 207,
          failed: 1,
          photos: 108,
          errors: [{ file: 'activities/1.gpx', error: 'The GPX file has no times' }],
          finished_at: '2026-11-09T09:30:00Z',
        };
        listed = [done];
        return json('/api/imports/{import_id}/finish', 'post', done);
      }),
    );
    renderAt('/settings/import');
    expect(await screen.findByRole('heading', { name: 'Import' })).toBeInTheDocument();
    const input = document.querySelector('input[type=file]') as HTMLInputElement;
    await userEvent.upload(input, new File(['abcdef'], 'export.zip', { type: 'application/zip' }));
    expect(await screen.findByText(/1609 new · 207 merged/)).toBeInTheDocument();
    expect(parts).toEqual(['tok']);
    expect(created[0]).toEqual({
      filename: 'export.zip',
      size: 6,
    });
    expect(screen.getByText(/108 photos/)).toBeInTheDocument();
    expect(screen.getByText('1 file could not be read')).toBeInTheDocument();
  });

  it('settings: notifications per kind and the privacy zones', async () => {
    const patches: unknown[] = [];
    server.use(
      route('get', '/api/settings', () => json('/api/settings', 'get', SETTINGS)),
      route('patch', '/api/settings', async ({ request }) => {
        const body = await request.json();
        patches.push(body);
        return json('/api/settings', 'patch', { ...SETTINGS, ...body } as Settings);
      }),
    );
    renderAt('/settings/notifications');
    expect((await screen.findAllByRole('heading', { name: 'Notifications' })).length).toBeGreaterThan(0);
    expect(await screen.findByText(/cannot receive notifications/)).toBeInTheDocument(); // no push in jsdom
    await userEvent.click(screen.getByRole('checkbox', { name: /Estimates going up/ }));
    await waitFor(() => expect(patches.length).toBeGreaterThan(0));
    expect(patches[0]).toEqual({
      notifications: { ...NOTIFICATIONS, estimate: true },
    });
    await userEvent.click(screen.getByRole('link', { name: /^Privacy zones/ }));
    const zones = (await screen.findAllByRole('heading', { name: 'Privacy zones' }))
      .map((h) => h.closest('section'))
      .find((section) => section?.textContent?.includes('Home'));
    if (!zones) throw new Error('no privacy zones section');
    expect(within(zones).getByText('Home')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await waitFor(() => expect(patches.at(-1)).toEqual({ privacy_zones: [] }));
    await userEvent.click(screen.getByRole('link', { name: /^Your data/ }));
    expect(await screen.findByRole('link', { name: 'Gear' })).toHaveAttribute('href', '/gear');
    expect(screen.getByRole('button', { name: 'Prepare export' })).toBeInTheDocument();
  });

  it('a shared activity opens without a session and shows no private place', async () => {
    server.use(
      route('get', '/api/auth/me', () => fail(401, 'Not signed in')),
      route('get', '/api/settings', () => fail(401, 'Not signed in')),
      route('get', '/api/public/shares/{token}', () =>
        json('/api/public/shares/{token}', 'get', {
          name: 'Sunday long run',
          sport: 'run',
          sub_sport: null,
          day: '2026-11-08',
          athlete: 'Runner',
          duration_s: 5400,
          moving_s: 5300,
          distance_m: 18000,
          ascent_m: 120,
          avg_hr: 145,
          avg_power: null,
          route: [
            [
              [51.5, -0.1],
              [51.51, -0.11],
            ],
          ],
          profile: [
            { distance_m: 0, altitude_m: 10, speed: 3 },
            { distance_m: 18000, altitude_m: 30, speed: 3 },
          ],
          splits: [],
          photos: [],
          expires_at: '2026-11-15T09:00:00Z',
        }),
      ),
    );
    renderAt('/s/abc');
    expect(await screen.findByRole('heading', { name: 'Sunday long run' })).toBeInTheDocument();
    expect(screen.getByText('18.00 km')).toBeInTheDocument();
    expect(screen.getByText(/Parts of the route near private places are hidden/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Route' })).toBeInTheDocument();
  });

  it('an expired link says so', async () => {
    // No link has this token: the mock API's share links answer 404, as the server.
    renderAt('/s/old');
    expect(await screen.findByText('This link has expired or does not exist.')).toBeInTheDocument();
  });

  it('a month in review: totals against the month before, records and gear', async () => {
    server.use(
      route('get', '/api/stats/review', ({ request }) => {
        expect(new URL(request.url).searchParams.get('value')).toBe('2026-10');
        return json('/api/stats/review', 'get', {
          period: 'month',
          start: '2026-10-01',
          end: '2026-11-01',
          totals: totals(20, 200),
          previous: totals(10, 100),
          by_sport: { run: totals(20, 200) },
          active_days: 18,
          longest_streak: 6,
          longest: {
            id: 9,
            name: 'Half marathon',
            sport: 'run',
            day: '2026-10-19',
            distance_m: 21100,
            moving_s: 6300,
            ascent_m: 80,
            load: 210,
          },
          hardest: null,
          hilliest: null,
          records: [
            {
              distance_key: 'half_marathon',
              time_s: 6300,
              activity_id: 9,
              day: '2026-10-19',
              previous_s: 6500,
            },
          ],
          gear: [{ id: 1, name: 'Old trainers', kind: 'shoe', distance_m: 200000, count: 20 }],
          buckets: Array.from({ length: 31 }, (_, i) => ({
            start: `2026-10-${String(i + 1).padStart(2, '0')}`,
            distance_m: i % 2 ? 10000 : 0,
            moving_s: 3600,
            count: i % 2,
          })),
          photos: 0,
        });
      }),
    );
    renderAt('/review/month/2026-10');
    expect(await screen.findByRole('heading', { name: /October 2026 in review/ })).toBeInTheDocument();
    await screen.findAllByText('Half marathon');
    expect(screen.getAllByText(/\+100 % vs\. before/).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', { name: 'Half marathon' })[0]).toHaveAttribute(
      'href',
      '/activities/9',
    );
    expect(screen.getByText(/beat 1:48:20/)).toBeInTheDocument();
    expect(screen.getAllByText('Old trainers').length).toBeGreaterThan(0);
    expect(screen.getByText('18')).toBeInTheDocument(); // active days
    expect(screen.getByRole('link', { name: /September 2026/ })).toHaveAttribute(
      'href',
      '/review/month/2026-09',
    );
  });
});
