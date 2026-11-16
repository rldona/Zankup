import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';
import { gearItem } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { fail, json, route } from './mocks/route';
import { routes } from './router';

const gear = (id: number, name: string, extra: Partial<Parameters<typeof gearItem>[0]> = {}) =>
  gearItem({ id, name, distance_m: 100000, activities: 10, ...extra });

describe('gear: the habitual one (OS-910)', () => {
  it('lists it first with its badge, and the form marks another as habitual', async () => {
    const patches: unknown[] = [];
    const list = [
      gear(1, 'Old pair', { retired: true }),
      gear(2, 'Daily trainers'),
      gear(3, 'Race shoes', { habitual: true }),
    ];
    server.use(
      route('get', '/api/gear', () => json('/api/gear', 'get', list)),
      route('patch', '/api/gear/{gear_id}', async ({ params, request }) => {
        const body = await request.json();
        patches.push(body);
        const item = list.find((g) => String(g.id) === params.gear_id);
        if (!item) return fail(404, 'Gear not found');
        return json('/api/gear/{gear_id}', 'patch', { ...item, habitual: body.habitual ?? item.habitual });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: ['/gear'] })} />,
    );
    const names = await screen.findAllByRole('heading', { level: 2 });
    expect(names.map((h) => h.textContent)).toEqual([
      'Race shoesHabitual',
      'Daily trainers',
      'Old pairRetired',
    ]);

    const daily = names[1]?.closest('li');
    if (!daily) throw new Error('no card');
    await userEvent.click(within(daily).getByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Habitual/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(patches).toEqual([expect.objectContaining({ habitual: true })]));
  });
});
