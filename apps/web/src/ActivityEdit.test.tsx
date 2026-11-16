import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, onTestFinished } from 'vitest';

import { App } from './App';
import { db } from './mocks/db';
import { server } from './mocks/node';
import { routes } from './router';

/** The demo athlete's activity 7 (any of theirs would do); the calls the app makes, as "METHOD /path". */
function recordCalls() {
  const calls: string[] = [];
  const listener = ({ request }: { request: Request }) => {
    calls.push(`${request.method} ${new URL(request.url).pathname}`);
  };
  server.events.on('request:start', listener);
  onTestFinished(() => server.events.removeListener('request:start', listener));
  return calls;
}

describe('editing an activity (OS-909)', () => {
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });

  it('deletes it after a confirmation and goes back to the feed', async () => {
    const calls = recordCalls();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities/7'] });
    render(<App queryClient={queryClient} router={router} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Delete activity' }));
    const confirm = await screen.findByRole('dialog', { name: 'Delete activity' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(calls).toContain('DELETE /api/activities/7'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/activities'));
    expect(db.activity(7)).toBeUndefined();
  });

  it('keeps it when the confirmation is cancelled', async () => {
    const calls = recordCalls();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities/7'] });
    render(<App queryClient={queryClient} router={router} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Delete activity' }));
    const confirm = await screen.findByRole('dialog', { name: 'Delete activity' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Cancel' }));
    expect(calls).not.toContain('DELETE /api/activities/7');
    expect(router.state.location.pathname).toBe('/activities/7');
  });

  it('closes when the overlay is clicked', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities/7'] });
    render(<App queryClient={queryClient} router={router} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.click(await screen.findByRole('dialog'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
