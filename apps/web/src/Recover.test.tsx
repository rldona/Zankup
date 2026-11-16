import { QueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './App';
import { db } from './mocks/db';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

/** Records the JSON body last sent to each path (by anything but GET), and signs out. */
function signedOut() {
  db.signedIn = false;
  const bodies: Record<string, unknown> = {};
  server.events.on('request:start', async ({ request }) => {
    if (request.method !== 'GET') bodies[new URL(request.url).pathname] = await request.clone().json();
  });
  return bodies;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('password recovery and Google', () => {
  afterEach(() => server.events.removeAllListeners());

  it('asks for a link without saying whether the account exists', async () => {
    const bodies = signedOut();
    const router = renderAt('/sign-in');
    await userEvent.click(await screen.findByRole('link', { name: 'Forgot it?' }));
    expect(router.state.location.pathname).toBe('/forgot');
    await userEvent.type(screen.getByLabelText('E-mail'), 'me@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send link' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If that e-mail has an account');
    expect(bodies['/api/auth/password/forgot']).toEqual({ email: 'me@example.com' });
  });

  it('sets a new password with the link and a code', async () => {
    const bodies = signedOut(); // the demo's code is 123456: any other does not match
    renderAt('/reset-password?token=abc');
    await userEvent.type(screen.getByLabelText(/^Password/), 'a brand new long passphrase');
    await userEvent.type(screen.getByLabelText('Code from the app or a recovery code'), '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('That code does not match')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Code from the app or a recovery code'));
    await userEvent.type(screen.getByLabelText('Code from the app or a recovery code'), '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Password changed');
    expect(bodies['/api/auth/password/reset']).toEqual({
      token: 'abc',
      password: 'a brand new long passphrase',
      code: '123456',
    });
  });

  it('offers Google when the instance has it, and explains a rejected account', async () => {
    signedOut();
    server.use(
      route('get', '/api/auth/google/providers', () =>
        json('/api/auth/google/providers', 'get', { google: true, dev_login: false }),
      ),
    );
    renderAt('/sign-in?error=google&next=%2Factivities%2F7');
    const google = await screen.findByRole('link', { name: 'Continue with Google' });
    expect(google).toHaveAttribute('href', '/api/auth/google/start?mode=login&next=%2Factivities%2F7');
    expect(screen.getByText(/That Google account is not linked/)).toBeInTheDocument();
  });
});
