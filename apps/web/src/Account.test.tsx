import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import type { Me } from './mocks/db';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

const ME: Me = {
  id: 1,
  email: 'runner@example.com',
  display_name: 'Runner',
  totp_enabled: true,
  google_linked: false,
  location: 'Madrid',
  birth_date: '1985-04-02',
  sex: 'male',
  weight_kg: 72.5,
  height_cm: 178,
};
const SETTINGS: Res<'/api/settings', 'get'> = {
  theme: 'system',
  language: null,
  units: 'metric',
  hr_max: null,
  hr_rest: null,
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
    weekly: true,
  },
  ai: { enabled: false, summaries: false, weekly: false },
};
const settingsAre = (settings = SETTINGS) =>
  route('get', '/api/settings', () => json('/api/settings', 'get', settings));

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
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

describe('settings and profile', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });

  afterEach(() => {
    localStorage.clear();
    server.events.removeAllListeners();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('saves language, units and heart rate, and closes other sessions', async () => {
    let settings = { ...SETTINGS };
    let devices: Res<'/api/auth/devices', 'get'> = [
      {
        id: 1,
        name: 'Chrome on Linux',
        platform: 'web',
        created_at: '2026-11-01T10:00:00Z',
        last_seen: '2026-11-08T10:00:00Z',
        expires_at: null,
        current: true,
      },
      {
        id: 2,
        name: 'Pixel',
        platform: 'android',
        created_at: '2026-10-01T10:00:00Z',
        last_seen: '2026-11-07T10:00:00Z',
        expires_at: null,
        current: false,
      },
    ];
    server.use(
      route('get', '/api/auth/me', () => json('/api/auth/me', 'get', ME)),
      route('get', '/api/settings', () => json('/api/settings', 'get', settings)),
      route('patch', '/api/settings', async ({ request }) => {
        settings = { ...settings, ...((await request.json()) as object) };
        return json('/api/settings', 'patch', settings);
      }),
      route('get', '/api/sources', () =>
        json('/api/sources', 'get', [
          {
            kind: 'garmin',
            configured: true,
            key_expires_on: '2026-11-15',
            expiring_soon: true,
            token_expires_at: null,
            last_sync_at: null,
            last_error: null,
          },
        ]),
      ),
      route('get', '/api/auth/devices', () => json('/api/auth/devices', 'get', devices)),
      route('delete', '/api/auth/devices/{device_id}', ({ params }) => {
        devices = devices.filter((d) => d.id !== Number(params.device_id));
        return new Response(null, { status: 204 });
      }),
    );
    const calls = record();
    renderAt('/settings/general');

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Units' }), 'imperial');
    await waitFor(() =>
      expect(calls).toContainEqual({ method: 'PATCH', path: '/api/settings', body: { units: 'imperial' } }),
    );

    await userEvent.click(screen.getByRole('link', { name: /^Heart rate and zones/ }));
    const max = await screen.findByRole('spinbutton', { name: 'Max HR' });
    expect(max).toHaveAttribute('placeholder', '188');
    expect(screen.getByText(/the highest you have recorded \(188 bpm\)/)).toBeInTheDocument();
    await userEvent.type(max, '191');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PATCH',
        path: '/api/settings',
        body: { hr_max: 191, hr_rest: null },
      }),
    );

    await userEvent.click(screen.getByRole('link', { name: /^Account/ }));
    expect(await screen.findByText(/Renew it soon/)).toBeInTheDocument();
    const pixel = (await screen.findByText('Pixel')).closest('li');
    if (!pixel) throw new Error('no session row');
    expect(screen.getByText('This device')).toBeInTheDocument();
    await userEvent.click(within(pixel).getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(screen.queryByText('Pixel')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('link', { name: /^General/ }));
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: /^Language/ }), 'es');
    await waitFor(() =>
      expect(calls).toContainEqual({ method: 'PATCH', path: '/api/settings', body: { language: 'es' } }),
    );
    expect(localStorage.getItem('zankup-language')).toBe('es');
  });

  it('shows the profile summary and edits the profile', async () => {
    server.use(
      route('get', '/api/auth/me', () => json('/api/auth/me', 'get', ME)),
      route('patch', '/api/auth/me', async ({ request }) =>
        json('/api/auth/me', 'patch', { ...ME, ...((await request.json()) as object) }),
      ),
      settingsAre(),
      route('get', '/api/goals', () => json('/api/goals', 'get', [])),
      route('get', '/api/profile/summary', () =>
        json('/api/profile/summary', 'get', {
          year: 2026,
          this_year: [
            { sport: 'run', count: 120, distance_m: 1_000_000, moving_s: 360_000, ascent_m: 8000 },
            { sport: 'ride', count: 1, distance_m: 40_000, moving_s: 5400, ascent_m: 300 },
          ],
          all_time: [
            { sport: 'run', count: 900, distance_m: 8_000_000, moving_s: 3_000_000, ascent_m: 60_000 },
          ],
          since: '2012-03-04T08:00:00Z',
          records: [
            {
              distance_key: '10k',
              distance_m: 10000,
              time_s: 2700,
              activity_id: 42,
              date: '2025-04-06T08:00:00Z',
            },
          ],
          streak_weeks: 12,
          device: 'fēnix 6X Pro',
        }),
      ),
    );
    const calls = record();
    renderAt('/profile');
    expect(await screen.findByRole('heading', { name: 'Runner' })).toBeInTheDocument();
    expect(await screen.findByText('Madrid')).toBeInTheDocument();
    expect(screen.queryByText(/fēnix 6X Pro/)).not.toBeInTheDocument();
    expect(screen.getByText('12 weeks in a row')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '2026 so far' })).toBeInTheDocument();
    expect(screen.getByText('121')).toBeInTheDocument(); // activities this year
    expect(screen.getByText('120 activities')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Apr.*2025/ })).toHaveAttribute('href', '/activities/42');
    expect(screen.getByRole('row', { name: /10 km 45:00 4:30 \/km/ })).toBeInTheDocument();
    expect(screen.getByText('72.5')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Edit profile' }));
    const location = screen.getByRole('textbox', { name: 'Location' });
    await userEvent.clear(location);
    await userEvent.type(location, 'Valencia');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Valencia')).toBeInTheDocument();
    const patch = calls.find((c) => c.method === 'PATCH');
    expect(patch?.body).toMatchObject({ location: 'Valencia', sex: 'male', weight_kg: 72.5, height_cm: 178 });

    // You leads to the rest, as on the app (OS-914).
    expect(screen.getByRole('link', { name: /Estimates/ })).toHaveAttribute('href', '/estimates');
    expect(screen.getByRole('link', { name: /Account/ })).toHaveAttribute('href', '/settings/account');
  });

  it('on a desktop hides the physical data until the eye shows them, and remembers it', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === '(min-width: 1200px)',
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
    server.use(
      route('get', '/api/auth/me', () => json('/api/auth/me', 'get', ME)),
      settingsAre(),
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
    renderAt('/profile');
    const panel = (await screen.findByRole('heading', { name: 'Physical data' })).closest('section');
    if (!panel) throw new Error('no physical data');
    expect(panel).not.toHaveTextContent('72.5');
    await userEvent.click(within(panel).getByRole('button', { name: 'Show the physical data' }));
    expect(panel).toHaveTextContent('72.5');
    expect(localStorage.getItem('zankup-physical-hidden')).toBe('0');
    localStorage.clear();
  });

  it('lists and removes passkeys in the account tab', async () => {
    let passkeys: Res<'/api/auth/passkeys', 'get'> = [
      {
        id: 3,
        name: 'Chrome on Android',
        created_at: '2026-11-01T10:00:00Z',
        last_used_at: null,
        backed_up: true,
      },
    ];
    server.use(
      route('get', '/api/auth/me', () => json('/api/auth/me', 'get', ME)),
      settingsAre(),
      route('get', '/api/auth/passkeys', () => json('/api/auth/passkeys', 'get', passkeys)),
      route('delete', '/api/auth/passkeys/{passkey_id}', ({ params }) => {
        passkeys = passkeys.filter((p) => p.id !== Number(params.passkey_id));
        return new Response(null, { status: 204 });
      }),
    );
    const calls = record();
    renderAt('/profile?tab=account');
    const row = (await screen.findByText('Chrome on Android')).closest('li');
    if (!row) throw new Error('no passkey row');
    expect(row).toHaveTextContent('not used yet');
    await userEvent.click(within(row).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText('No passkeys yet.')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'DELETE' && c.path === '/api/auth/passkeys/3')).toBe(true);
  });
});
