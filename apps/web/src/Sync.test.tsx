import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

type SyncStatus = Res<'/api/sync/{run_id}', 'get'>;

const run = (status: SyncStatus['status'], extra: Partial<SyncStatus> = {}): SyncStatus => ({
  id: 9,
  trigger: 'button',
  status,
  started_at: '2026-11-08T10:00:00Z',
  finished_at: status === 'success' ? '2026-11-08T10:01:00Z' : null,
  new_count: 2,
  progress: {
    since: '2026-11-01',
    listed: 5,
    new: 2,
    downloaded: status === 'success' ? 2 : 1,
    window: 1,
    windows: 1,
  },
  pending_imports: status === 'success' ? 0 : 1,
  error: null,
  ...extra,
});

/** Records every request as "METHOD /path". */
function record() {
  const calls: string[] = [];
  server.events.on('request:start', ({ request }) => {
    calls.push(`${request.method} ${new URL(request.url).pathname}`);
  });
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

describe('sync', () => {
  afterEach(() => {
    server.events.removeAllListeners();
  });

  it('syncs from the Activities header, follows the progress and refreshes the feed', async () => {
    let state: SyncStatus[] = [];
    let polls = 0;
    server.use(
      route('get', '/api/activities', () => json('/api/activities', 'get', { items: [], next_cursor: null })),
      route('get', '/api/sync', () => {
        if (state.length && ++polls >= 1) state = [run('success')];
        return json('/api/sync', 'get', state);
      }),
      route('post', '/api/sync', () => {
        state = [run('running')];
        return Response.json(state[0], { status: 202 });
      }),
    );
    const calls = record();
    renderAt('/activities');
    // As in the app, sync sits in the Activities header; the frame has no sync button of its own.
    await userEvent.click(await screen.findByRole('button', { name: 'Sync' }));
    const sheet = await screen.findByRole('dialog', { name: 'Sync' });
    expect(within(sheet).getByText('Syncing with Garmin…')).toBeInTheDocument();
    expect(sheet).toHaveTextContent('5 found · 2 new · 1 downloaded');
    expect(sheet).toHaveTextContent('1 activity still to import');
    expect(
      await within(sheet).findByText('Done: 2 new activities.', {}, { timeout: 4000 }),
    ).toBeInTheDocument();
    await waitFor(() => expect(calls.filter((c) => c === 'GET /api/activities').length).toBeGreaterThan(1));
  });

  it('starts the whole history from the Sync screen', async () => {
    let body: unknown = null;
    server.use(
      route('get', '/api/sync', () => json('/api/sync', 'get', [])),
      route('post', '/api/sync', async ({ request }) => {
        body = await request.json();
        return Response.json(run('queued', { trigger: 'initial' }), { status: 202 });
      }),
    );
    renderAt('/sync');
    await userEvent.click(await screen.findByRole('button', { name: 'Load the whole history' }));
    expect(await screen.findByText('Waiting for its turn…')).toBeInTheDocument();
    expect(body).toEqual({ history: true });
    expect(screen.getByRole('button', { name: 'Sync now' })).toBeDisabled();
  });
});
