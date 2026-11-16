import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';

import { App } from './App';
import { activityDetail, activitySummary, type Detail } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

const GYM = activityDetail({
  id: 9,
  name: 'Strength',
  sport: 'strength',
  sub_sport: 'strength_training',
  workout_type: null,
  start_utc: '2026-11-16T17:25:00Z',
  duration_s: 2800,
  moving_s: 2723,
  distance_m: null,
  ascent_m: null,
  avg_hr: 120,
  sets: [{ start_s: 0, duration_s: 60, kind: 'active', reps: 18, weight_kg: null, category: null }],
});

/** The mock API answers with this workout; the edits sent are kept. */
function mockApi(activity: Detail) {
  const patches: unknown[] = [];
  server.use(
    route('get', '/api/activities', () =>
      json('/api/activities', 'get', { items: [activitySummary(activity)], next_cursor: null }),
    ),
    route('get', '/api/activities/{activity_id}', ({ params }) =>
      params.activity_id === '9' ? json('/api/activities/{activity_id}', 'get', activity) : undefined,
    ),
    route('patch', '/api/activities/{activity_id}', async ({ request }) => {
      const body = await request.json();
      patches.push(body);
      return json('/api/activities/{activity_id}', 'patch', {
        id: activity.id,
        name: body.name ?? activity.name,
        description: activity.description,
        sport: activity.sport,
        gear_id: null,
        workout_type: body.workout_type ?? null,
        edited_fields: ['name', 'workout_type'],
      });
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
    mockApi(activityDetail({ ...GYM, name: 'Tuesday class', workout_type: 'grit' }));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities'] });
    render(<App queryClient={queryClient} router={router} />);
    expect(await screen.findByText(/· GRIT/)).toBeInTheDocument();
  });
});
