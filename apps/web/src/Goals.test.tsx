import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

const GOAL = {
  id: 4,
  period: 'week',
  metric: 'distance',
  sport: 'run',
  target: 20000,
  current: 5000,
  projection: 17500,
  start: '2026-11-09',
  end: '2026-11-16',
};

/** The goal above, the calendar given (its days) and the requests made, by path. */
function mockGoals(days: Res<'/api/stats/calendar', 'get'> = []) {
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
    route('get', '/api/goals', () => json('/api/goals', 'get', [GOAL])),
    route('patch', '/api/goals/{goal_id}', () => json('/api/goals/{goal_id}', 'patch', GOAL)),
    route('get', '/api/stats/calendar', () => json('/api/stats/calendar', 'get', days)),
  );
  return calls;
}

describe('Goals (OS-911)', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    server.events.removeAllListeners();
    localStorage.clear();
  });

  it('opens a goal day by day, and edits it in place', async () => {
    const calls = mockGoals([
      {
        day: '2026-11-09',
        tsb: null,
        activities: [
          {
            id: 1,
            sport: 'run',
            name: null,
            load: null,
            distance_m: 5000,
            moving_s: 1500,
            start: '2026-11-09T08:00:00Z',
          },
        ],
      },
    ]);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: ['/goals'] })} />,
    );

    const card = await screen.findByRole('button', { name: /This week · Run/ });
    expect(card).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(card);
    expect(card).toHaveAttribute('aria-expanded', 'true');
    await waitFor(() => expect(calls.some((c) => c.path === '/api/stats/calendar')).toBe(true));

    await userEvent.click(screen.getByRole('button', { name: 'Edit', pressed: false }));
    await userEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const dialog = screen.getByRole('dialog');
    const target = within(dialog).getByRole('textbox', { name: /Target/ });
    expect(target).toHaveValue('20');
    await userEvent.clear(target);
    await userEvent.type(target, '25');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PATCH')).toEqual({
        method: 'PATCH',
        path: '/api/goals/4',
        body: { period: 'week', metric: 'distance', sport: 'run', target: 25000 },
      }),
    );
  });

  it('on a desktop, shows every chart open and the card is not a toggle', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === '(min-width: 1200px)',
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
    const calls = mockGoals();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: ['/goals'] })} />,
    );
    expect(await screen.findByText('This week · Run')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /This week · Run/ })).not.toBeInTheDocument();
    await waitFor(() => expect(calls.map((c) => c.path)).toContain('/api/stats/calendar'));
    // The header's buttons, labelled.
    expect(screen.getByRole('button', { name: 'Add goal' })).toHaveTextContent('Add goal');
  });

  it('keeps a chart open or folded as it was left, after leaving the page', async () => {
    mockGoals();
    const open = () =>
      render(
        <App
          queryClient={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
          router={createMemoryRouter(routes, { initialEntries: ['/goals'] })}
        />,
      );
    const first = open();
    await userEvent.click(await screen.findByRole('button', { name: /This week · Run/ }));
    first.unmount();

    const second = open();
    const card = await screen.findByRole('button', { name: /This week · Run/ });
    expect(card).toHaveAttribute('aria-expanded', 'true'); // still open
    await userEvent.click(card);
    second.unmount();

    open();
    expect(await screen.findByRole('button', { name: /This week · Run/ })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });
});
