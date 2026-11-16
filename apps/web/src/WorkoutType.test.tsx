import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { routes } from './router';

const GYM = {
  id: 9,
  name: 'Strength',
  sport: 'strength',
  sub_sport: 'strength_training',
  workout_type: null,
  source: 'garmin',
  start_utc: '2026-11-16T17:25:00Z',
  timezone: '+01:00',
  duration_s: 2800,
  moving_s: 2723,
  distance_m: null,
  ascent_m: null,
  avg_hr: 120,
  avg_power: null,
  has_gps: false,
  route: null,
  photo: null,
  photos: 0,
};

function mockApi(activity: Record<string, unknown>) {
  const patches: unknown[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request | string, init?: RequestInit) => {
      const request =
        typeof input === 'string' ? new Request(new URL(input, 'http://localhost'), init) : input;
      const url = new URL(request.url);
      if (request.method === 'PATCH') {
        patches.push(await request.json());
        return Response.json({ ...activity, edited_fields: ['name', 'workout_type'] });
      }
      const body: Record<string, unknown> = {
        '/api/auth/me': { id: 1, email: 'me@example.com', display_name: 'Lifter', totp_enabled: true },
        '/api/activities': { items: [activity], next_cursor: null },
        '/api/activities/9': {
          ...activity,
          laps: [],
          splits: [],
          best_efforts: [],
          photo_list: [],
          edited_fields: [],
          sets: [{ start_s: 0, duration_s: 60, kind: 'active', reps: 18, weight_kg: null, category: null }],
        },
        '/api/gear': [],
      };
      const data = body[url.pathname];
      return data === undefined
        ? Response.json({ detail: 'Not Found' }, { status: 404 })
        : Response.json(data);
    }),
  );
  return patches;
}

describe("a strength workout's type (OS-1015)", () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });
  afterEach(() => vi.unstubAllGlobals());

  it('is chosen in Edit, names the activity after it and is saved', async () => {
    const patches = mockApi(GYM);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities/9'] });
    render(<App queryClient={queryClient} router={router} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit' });
    const types = within(dialog).getByRole('radiogroup', { name: 'Workout type' });
    expect(within(types).getAllByRole('radio')).toHaveLength(8);
    await userEvent.click(within(types).getByRole('radio', { name: 'Hyrox' }));
    expect(within(dialog).getByRole('textbox', { name: 'Name' })).toHaveValue('Hyrox');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(patches).toHaveLength(1));
    expect(patches[0]).toMatchObject({ name: 'Hyrox', workout_type: 'hyrox' });
  });

  it('shows after the date when the activity has another name', async () => {
    mockApi({ ...GYM, name: 'Tuesday class', workout_type: 'grit' });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities'] });
    render(<App queryClient={queryClient} router={router} />);
    expect(await screen.findByText(/· GRIT/)).toBeInTheDocument();
  });
});
