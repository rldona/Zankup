import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';

import { App } from './App';
import { activityDetail, activitySummary, gearItem, type Detail } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

const ride = (id: number, hoursAgo: number) =>
  activityDetail({
    id,
    name: `Ride ${id}`,
    sport: 'ride',
    start_utc: new Date(Date.now() - hoursAgo * 3600 * 1000).toISOString(),
    timezone: null,
    duration_s: 3600,
    moving_s: 3500,
    distance_m: 30000,
    ascent_m: 300,
    avg_hr: 140,
  });

/** The mock API's feed is these rides, with this gear; the edits sent are kept. */
function mockApi(items: Detail[]) {
  const patches: unknown[] = [];
  server.use(
    route('get', '/api/activities', () =>
      json('/api/activities', 'get', { items: items.map(activitySummary), next_cursor: null }),
    ),
    route('get', '/api/activities/{activity_id}', ({ params }) => {
      const found = items.find((a) => String(a.id) === params.activity_id);
      return found ? json('/api/activities/{activity_id}', 'get', found) : undefined;
    }),
    route('get', '/api/gear', () =>
      json('/api/gear', 'get', [
        gearItem({ id: 5, name: 'Road bike', kind: 'bike', sports: ['ride'] }),
        gearItem({ id: 6, name: 'Old shoes', retired: true }),
      ]),
    ),
    route('patch', '/api/activities/{activity_id}', async ({ params, request }) => {
      const body = await request.json();
      patches.push(body);
      return json('/api/activities/{activity_id}', 'patch', {
        id: Number(params.activity_id),
        name: body.name ?? null,
        description: null,
        sport: 'ride',
        gear_id: body.gear_id ?? null,
        workout_type: null,
        edited_fields: Object.keys(body),
      });
    }),
  );
  return patches;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

describe('the latest activity on arrival (OS-906)', () => {
  beforeEach(() => {
    localStorage.clear();
    // jsdom has no modal dialogs: opening one only marks it open.
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });

  it('only remembers the newest on the first visit', async () => {
    mockApi([ride(2, 2)]);
    renderAt('/');
    await waitFor(() => expect(localStorage.getItem('zankup-latest-seen')).toBe('2'));
    expect(screen.queryByRole('dialog', { name: 'Latest activity' })).not.toBeInTheDocument();
  });

  it('shows a new activity once, saves what changed and remembers it', async () => {
    localStorage.setItem('zankup-latest-seen', '1');
    const patches = mockApi([ride(2, 2), ride(1, 30)]);
    renderAt('/');
    const name = await screen.findByRole('textbox', { name: 'Name' });
    expect(name).toHaveValue('Ride 2');
    await userEvent.clear(name);
    await userEvent.type(name, 'Hill repeats');
    await userEvent.click(await screen.findByRole('radio', { name: 'Road bike' }));
    expect(screen.queryByRole('radio', { name: 'Old shoes' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(patches).toEqual([{ name: 'Hill repeats', gear_id: 5 }]));
    await waitFor(() => expect(localStorage.getItem('zankup-latest-seen')).toBe('2'));
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
  });

  it('does not show an activity older than three days', async () => {
    localStorage.setItem('zankup-latest-seen', '1');
    mockApi([ride(2, 24 * 4)]);
    renderAt('/');
    await screen.findByRole('heading', { name: 'Home' });
    expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
  });
});
