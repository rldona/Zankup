import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { db, type Me } from './mocks/db';
import { openSignUp } from './mocks/handlers/account';
import { server } from './mocks/node';
import { fail, json, route } from './mocks/route';
import { routes } from './router';
import { passwordStrength } from '@zankup/core';

const ME: Me = {
  id: 1,
  email: 'me@example.com',
  display_name: 'Raúl',
  totp_enabled: true,
  google_linked: false,
};
const CODES = [
  'abcde-fghjk',
  'mnpqr-stuvw',
  'xyz23-45678',
  '9abcd-efghj',
  'kmnpq-rstuv',
  'wxyz2-34567',
  '89abc-defgh',
  'jkmnp-qrstu',
];

/** Records each request's JSON body, by path. */
function record() {
  const bodies: Record<string, Record<string, unknown>[]> = {};
  server.events.on('request:start', async ({ request }) => {
    const text = await request.clone().text();
    (bodies[new URL(request.url).pathname] ??= []).push(
      text ? (JSON.parse(text) as Record<string, unknown>) : {},
    );
  });
  return bodies;
}

/** A brand-new instance: nobody signed in, sign-up open. */
function brandNew() {
  db.signedIn = false;
  openSignUp();
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

async function fillAccount(code = 'ABCD-EFGH-JKMN') {
  await userEvent.type(await screen.findByLabelText('Setup code', { exact: false }), code);
  await userEvent.type(screen.getByLabelText('Your name'), 'Raúl');
  await userEvent.type(screen.getByLabelText('E-mail'), 'me@example.com');
  await userEvent.type(screen.getByLabelText(/^Password/), 'a long and unique passphrase');
  await userEvent.click(screen.getByRole('checkbox'));
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
}

describe('sign-up', () => {
  afterEach(() => {
    server.events.removeAllListeners();
    vi.restoreAllMocks();
  });

  it('creates the account: code and password, QR and code, recovery codes, all set', async () => {
    brandNew();
    server.use(
      route('get', '/api/setup', () => json('/api/setup', 'get', { open: !db.signedIn })),
      route('post', '/api/setup/account', () =>
        json('/api/setup/account', 'post', {
          setup_token: 'tok',
          secret: 'JBSWY3DPEHPK3PXP',
          uri: 'otpauth://totp/x',
          qr_svg: '<svg data-qr="1"></svg>',
        }),
      ),
      route('post', '/api/setup/totp', () => {
        db.signedIn = true;
        return json('/api/setup/totp', 'post', { me: ME, recovery_codes: CODES });
      }),
    );
    const bodies = record();
    const router = renderAt('/setup');
    await fillAccount();
    await waitFor(() =>
      expect(bodies['/api/setup/account']?.[0]).toEqual({
        code: 'ABCD-EFGH-JKMN',
        display_name: 'Raúl',
        email: 'me@example.com',
        password: 'a long and unique passphrase',
        accept: true,
      }),
    );

    expect(await screen.findByRole('heading', { name: 'Two-step verification' })).toBeInTheDocument();
    expect(document.querySelector('.qr svg[data-qr]')).not.toBeNull();
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP')).toBeInTheDocument();
    await userEvent.keyboard('123456');

    expect(await screen.findByRole('heading', { name: 'Recovery codes' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(8);
    const next = screen.getByRole('button', { name: 'Continue' });
    expect(next).toBeDisabled(); // only once they are saved
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(next);

    await userEvent.click(await screen.findByRole('button', { name: 'Go to your activities' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(bodies['/api/setup/totp']).toEqual([
      { setup_token: 'tok', code: '123456', client: 'web', device_name: 'Browser' },
    ]);
  });

  it('explains a wrong setup code and where to find it', async () => {
    brandNew(); // the demo's setup code is ABCD-EFGH-JKMN: any other is wrong
    renderAt('/setup');
    expect(await screen.findByText(/server logs/)).toBeInTheDocument();
    await fillAccount('WRONG');
    expect(await screen.findByRole('alert')).toHaveTextContent('That setup code is not right. Copy it again');
  });

  it("shows the server's reason, such as a breached password", async () => {
    brandNew();
    server.use(
      route('post', '/api/setup/account', () =>
        fail(422, 'This password appears in known data breaches: choose another one'),
      ),
    );
    renderAt('/setup');
    await fillAccount();
    expect(await screen.findByRole('alert')).toHaveTextContent('known data breaches');
  });

  it('says sign-up is closed once the instance has its account', async () => {
    db.signedIn = false; // the demo instance has its account
    renderAt('/setup');
    expect(await screen.findByRole('heading', { name: 'Sign-up is closed' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to sign-in' })).toHaveAttribute('href', '/sign-in');
  });

  it('sends visitors of a brand-new instance from sign-in to sign-up', async () => {
    brandNew();
    const router = renderAt('/');
    await waitFor(() => expect(router.state.location.pathname).toBe('/setup'));
    expect(await screen.findByRole('heading', { name: 'Create your account' })).toBeInTheDocument();
  });

  it('rates passwords for the meter', () => {
    expect(passwordStrength('short')).toBe(0);
    expect(passwordStrength('twelve chars')).toBe(1);
    expect(passwordStrength('Sixteen-chars-1x')).toBe(3);
    expect(passwordStrength('a much longer passphrase with 4 parts!')).toBe(4);
  });
});
