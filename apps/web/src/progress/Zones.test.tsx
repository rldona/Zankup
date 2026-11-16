import { QueryClient } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { App } from '../App';
import { db } from '../mocks/db';
import { server } from '../mocks/node';
import { json, route } from '../mocks/route';
import { routes } from '../router';

/** A rider's best power: 15 s to 1 h (watts), the last eight weeks a little under all time. */
const CURVE = [
  [15, 900],
  [30, 700],
  [60, 520],
  [120, 420],
  [180, 380],
  [300, 340],
  [600, 310],
  [900, 295],
  [1200, 285],
  [1800, 275],
  [2700, 265],
  [3600, 255],
].map(([duration_s = 0, value = 0]) => ({ duration_s, value, activity_id: 3, day: '2026-10-01' }));

const BESTS_10K = [
  { activity_id: 11, name: 'Race', day: '2026-10-12', value: 2405 },
  { activity_id: 12, name: 'Tempo', day: '2026-06-03', value: 2520 },
  { activity_id: 13, name: 'Long', day: '2025-04-20', value: 2610 },
];

/** The answers the figures are read from, and every request made (path and query). */
function mockApi() {
  const calls: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    calls.push(`${url.pathname}${url.search}`);
  });
  server.use(
    route('get', '/api/auth/me', () =>
      json('/api/auth/me', 'get', { ...db.user, display_name: 'Rider', weight_kg: 70 }),
    ),
    route('get', '/api/stats/zones', ({ request }) => {
      const kind = new URL(request.url).searchParams.get('kind');
      return json(
        '/api/stats/zones',
        'get',
        kind === 'pace'
          ? {
              kind,
              bounds: [0, 2.574, 2.904, 3.135, 3.3],
              seconds: [600, 1200, 3000, 900, 300],
              previous: [],
            }
          : {
              kind: kind ?? 'hr',
              bounds: [0, 120, 140, 155, 170],
              seconds: [1800, 7200, 3600, 1800, 600],
              previous: [1800, 3600, 3600, 1800, 600],
            },
      );
    }),
    route('get', '/api/stats/curves', ({ request }) =>
      json(
        '/api/stats/curves',
        'get',
        new URL(request.url).searchParams.get('from') === '2000-01-01'
          ? CURVE
          : CURVE.map((p) => ({ ...p, value: p.value - 20 })),
      ),
    ),
    route('get', '/api/stats/bests/recent', () =>
      json('/api/stats/bests/recent', 'get', [
        { key: '10k', activity_id: 11, day: '2026-10-12', value: 2405, rank: 1, scope: 'all' },
        { key: '10k', activity_id: 12, day: '2026-06-03', value: 2520, rank: 2, scope: 'all' },
        { key: 'power_60', activity_id: 3, day: '2026-10-01', value: 520, rank: 2, scope: 'year' },
      ]),
    ),
    route('get', '/api/stats/bests', ({ request }) =>
      json(
        '/api/stats/bests',
        'get',
        new URL(request.url).searchParams.get('key') === '10k' ? BESTS_10K : [],
      ),
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

describe('Progress: training zones, skills and best times (OS-1004)', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });
  afterEach(() => server.events.removeAllListeners());

  it('shows the three cards with their figures', async () => {
    mockApi();
    open('/progress');
    const zones = await screen.findByRole('link', { name: /Training zones/ });
    expect(await within(zones).findByText('48 % in Z2')).toBeInTheDocument();
    // Z2's share went from 32 % to 48 %.
    expect(within(zones).getByText('16 %')).toBeInTheDocument();
    const skills = await screen.findByRole('link', { name: /Skills/ });
    expect(await within(skills).findByText('Sprint · 15 s')).toBeInTheDocument();
    expect(within(skills).getByText('880 W')).toBeInTheDocument();
    const bests = await screen.findByRole('link', { name: /Best times/ });
    expect(await within(bests).findByText('40:05')).toBeInTheDocument();
    expect(within(bests).getByText('520 W')).toBeInTheDocument();
    expect(within(bests).getByText('This year')).toBeInTheDocument();
  });

  it('opens the zones from their card, a run on pace', async () => {
    const calls = mockApi();
    const router = open('/progress');
    await userEvent.click(await screen.findByRole('link', { name: /Training zones/ }));
    expect(router.state.location.pathname).toBe('/progress/zones');
    expect(await screen.findByRole('heading', { name: '48 % in Zone 2' })).toBeInTheDocument();
    expect(screen.getByText('2h 0min')).toBeInTheDocument();
    expect(screen.getByText('121 - 140 bpm')).toBeInTheDocument();
    expect(screen.getByText(/Your time in zone 2 went up 100 %/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit zones' })).toHaveAttribute('href', '/settings/heart');
    await userEvent.click(screen.getByRole('radio', { name: 'Run' }));
    expect(await screen.findByText('5:19-5:44 /km')).toBeInTheDocument();
    expect(calls.some((c) => c.includes('kind=pace') && c.includes('sport=run'))).toBe(true);
  });

  it('opens a duration’s levels from the skills radar', async () => {
    mockApi();
    open('/progress/skills');
    await userEvent.click(await screen.findByRole('button', { name: '1 min: 500 W' }));
    const dialog = await screen.findByRole('dialog', { name: '1 min power' });
    expect(within(dialog).getByText(/for 70 kg/)).toBeInTheDocument();
    expect(within(dialog).getByText('Legend')).toBeInTheDocument();
    expect(within(dialog).getByRole('row', { current: true })).toHaveTextContent('500 W');
  });

  it('shows the 10 km record, the bests by year and the top 10', async () => {
    mockApi();
    open('/progress/bests');
    expect(await screen.findByRole('heading', { name: '10 km', level: 1 })).toBeInTheDocument();
    expect(await screen.findAllByText('40:05')).not.toHaveLength(0);
    expect(screen.getByRole('region', { name: '2025' })).toHaveTextContent('43:30');
    await userEvent.click(screen.getByRole('tab', { name: 'Top 10' }));
    const rows = within(screen.getByRole('list', { name: 'Top 10' })).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('40:05');
    expect(within(rows[2] as HTMLElement).getByRole('link')).toHaveAttribute('href', '/activities/13');
  });
});
