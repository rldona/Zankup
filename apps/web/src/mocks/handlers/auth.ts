/**
 * Session and sign-in (`/api/auth*`): signed in as the demo athlete from the start; signing out and in
 * again work. The demo sign-in is any e-mail with the password `demo` (or the one a password reset
 * chose), then the code `123456` or one of the recovery codes below; wrong ones answer as the backend
 * does. Passkeys answer plausible WebAuthn options, but the ceremony itself cannot run here: any
 * credential is accepted.
 */
import { db, type Me } from '../db';
import { fail, json, route, type Res } from '../route';

const GENERIC_ERROR = 'Incorrect e-mail, password or code';
const MIN_PASSWORD = 12;
const DEMO_CODE = '123456';

type Device = Res<'/api/auth/devices', 'get'>[number];
type Passkey = Res<'/api/auth/passkeys', 'get'>[number];
type Tokens = Res<'/api/auth/refresh', 'post'>;

/** A time `days` before now, as the API writes it. */
export const ago = (days: number, hours = 0) =>
  new Date(db.today.getTime() - (days * 24 + hours) * 3_600_000).toISOString();

/** The 401 every protected route answers without a session, or null with one. */
export const unsigned = () => (db.signedIn ? null : fail(401, 'Not signed in'));

const state = () =>
  db.slice('auth', () => ({
    password: 'demo',
    /** Codes that work once instead of the 6 digits. */
    recovery: ['demo1-codes', 'demo2-codes', 'demo3-codes'],
    challenges: new Map<string, { client: string; device: string }>(),
    nextId: 4,
    devices: [
      {
        id: 1,
        name: 'Chrome on macOS',
        platform: 'web',
        created_at: ago(40),
        last_seen: ago(0, 0),
        expires_at: null,
        current: true,
      },
      {
        id: 2,
        name: 'Pixel 9 Pro',
        platform: 'android',
        created_at: ago(120),
        last_seen: ago(0, 3),
        expires_at: ago(-60),
        current: false,
      },
      {
        id: 3,
        name: 'Firefox on Linux',
        platform: 'web',
        created_at: ago(25),
        last_seen: ago(9),
        expires_at: ago(-21),
        current: false,
      },
    ] satisfies Device[] as Device[],
    passkeys: [
      { id: 1, name: 'iCloud Keychain', created_at: ago(90), last_used_at: ago(6), backed_up: true },
    ] satisfies Passkey[] as Passkey[],
  }));

const tokens = (): Tokens => ({
  access_token: `demo-access-${Date.now()}`,
  refresh_token: `demo-refresh-${Date.now()}`,
  token_type: 'bearer',
  expires_in: 15 * 60,
});

/** A new session: the browser's answer is the user, the app's its tokens. */
function signIn(client = 'web', device = 'Browser'): Me | Tokens {
  db.signedIn = true;
  const s = state();
  if (client !== 'web') {
    s.devices.push({
      id: s.nextId++,
      name: device,
      platform: 'android',
      created_at: ago(0),
      last_seen: ago(0),
      expires_at: ago(-90),
      current: false,
    });
    return tokens();
  }
  return db.user;
}

/** The 6 digits, or a recovery code (each works once). */
function codeOk(code: string) {
  const s = state();
  if (!/[a-z]/i.test(code)) return code.replace(/\s/g, '') === DEMO_CODE;
  const at = s.recovery.indexOf(code.trim().toLowerCase());
  if (at < 0) return false;
  s.recovery.splice(at, 1);
  return true;
}

const randomChallenge = () =>
  btoa(String(Math.random()).slice(2, 18)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

export const authHandlers = [
  route('post', '/api/auth/login', async ({ request }) => {
    const body = await request.json();
    if (body.password !== state().password) return fail(401, GENERIC_ERROR);
    const challenge = `demo-challenge-${Math.random().toString(36).slice(2)}`;
    state().challenges.set(challenge, {
      client: body.client ?? 'web',
      device: body.device_name ?? 'Browser',
    });
    return json('/api/auth/login', 'post', { mfa_required: true, challenge });
  }),
  route('post', '/api/auth/login/totp', async ({ request }) => {
    const body = await request.json();
    const claims = state().challenges.get(body.challenge);
    if (!claims || !codeOk(body.code)) return fail(401, GENERIC_ERROR);
    state().challenges.delete(body.challenge);
    return json('/api/auth/login/totp', 'post', signIn(claims.client, claims.device));
  }),
  route('post', '/api/auth/refresh', async ({ request }) => {
    const body = await request.json();
    if (!body.refresh_token.startsWith('demo-refresh-')) return fail(401, 'Sign in again');
    return json('/api/auth/refresh', 'post', {
      ...tokens(),
      refresh_token: body.rotate === false ? body.refresh_token : tokens().refresh_token,
    });
  }),
  route('post', '/api/auth/logout', () => {
    db.signedIn = false;
    return new Response(null, { status: 204 });
  }),
  route('get', '/api/auth/me', () => unsigned() ?? json('/api/auth/me', 'get', db.user)),
  route('patch', '/api/auth/me', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const body = await request.json();
    const user = db.user;
    if (body.birth_date) {
      const born = new Date(body.birth_date);
      if (!(born >= new Date('1900-01-01') && born <= db.today))
        return fail(422, 'That date of birth is not possible');
    }
    for (const [name, value] of Object.entries(body) as [keyof typeof body, unknown][]) {
      const v = typeof value === 'string' ? value.trim() || null : value;
      if (name === 'display_name') {
        if (typeof value === 'string') user.display_name = value.trim();
      } else Object.assign(user, { [name]: v });
    }
    return json('/api/auth/me', 'patch', user);
  }),

  // --- sessions ----------------------------------------------------------------------------------
  route('get', '/api/auth/devices', () => unsigned() ?? json('/api/auth/devices', 'get', state().devices)),
  route('delete', '/api/auth/devices/{device_id}', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const s = state();
    const target = s.devices.find((d) => d.id === Number(params.device_id));
    if (!target) return fail(404, 'Session not found');
    if (target.current) return fail(409, 'This is the current session: sign out instead');
    s.devices = s.devices.filter((d) => d !== target);
    return new Response(null, { status: 204 });
  }),

  // --- passkeys ----------------------------------------------------------------------------------
  route('get', '/api/auth/passkeys', () => unsigned() ?? json('/api/auth/passkeys', 'get', state().passkeys)),
  route('post', '/api/auth/passkeys/register/options', () => {
    const denied = unsigned();
    if (denied) return denied;
    return json('/api/auth/passkeys/register/options', 'post', {
      options: {
        rp: { id: location.hostname || 'localhost', name: 'Zankup' },
        user: { id: 'AQ', name: db.user.email, displayName: db.user.display_name ?? db.user.email },
        challenge: randomChallenge(),
        pubKeyCredParams: [
          { type: 'public-key', alg: -7 },
          { type: 'public-key', alg: -257 },
        ],
        timeout: 60000,
        excludeCredentials: state().passkeys.map((p) => ({ type: 'public-key', id: `cred-${p.id}` })),
        authenticatorSelection: { residentKey: 'required', userVerification: 'required' },
        attestation: 'none',
      },
    });
  }),
  route('post', '/api/auth/passkeys/register', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const body = await request.json();
    const s = state();
    const passkey: Passkey = {
      id: s.nextId++,
      name: body.name?.trim() || 'Passkey',
      created_at: new Date().toISOString(),
      last_used_at: null,
      backed_up: true,
    };
    s.passkeys.push(passkey);
    return json('/api/auth/passkeys/register', 'post', passkey, { status: 201 });
  }),
  route('delete', '/api/auth/passkeys/{passkey_id}', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const s = state();
    if (!s.passkeys.some((p) => p.id === Number(params.passkey_id))) return fail(404, 'Passkey not found');
    s.passkeys = s.passkeys.filter((p) => p.id !== Number(params.passkey_id));
    return new Response(null, { status: 204 });
  }),
  route('post', '/api/auth/passkeys/login/options', () =>
    json('/api/auth/passkeys/login/options', 'post', {
      options: {
        challenge: randomChallenge(),
        rpId: location.hostname || 'localhost',
        timeout: 60000,
        allowCredentials: [],
        userVerification: 'required',
      },
    }),
  ),
  route('post', '/api/auth/passkeys/login', async ({ request }) => {
    const body = await request.json();
    const passkey = state().passkeys[0];
    if (!passkey) return fail(401, 'This passkey could not be used');
    passkey.last_used_at = new Date().toISOString();
    return json('/api/auth/passkeys/login', 'post', signIn(body.client, body.device_name));
  }),

  // --- password ----------------------------------------------------------------------------------
  route('post', '/api/auth/password/forgot', () => new Response(null, { status: 202 })),
  route('post', '/api/auth/password/reset', async ({ request }) => {
    const body = await request.json();
    if (!body.token) return fail(400, 'This link has expired or was already used: ask for a new one');
    if (body.password.length < MIN_PASSWORD)
      return fail(422, `The password must have at least ${MIN_PASSWORD} characters`);
    if (!codeOk(body.code ?? '')) return fail(401, 'That code does not match');
    state().password = body.password;
    db.signedIn = false; // every session is signed out
    return new Response(null, { status: 204 });
  }),

  // --- Google ------------------------------------------------------------------------------------
  route('get', '/api/auth/google/providers', () =>
    json('/api/auth/google/providers', 'get', { google: false, google_client_id: null, dev_login: true }),
  ),
  route('get', '/api/auth/google/start', ({ request }) => {
    const url = new URL(request.url);
    if (url.searchParams.get('mode') === 'link') db.user.google_linked = true;
    else db.signedIn = true;
    const next = url.searchParams.get('next') ?? '/';
    return new Response(null, { status: 303, headers: { Location: next.startsWith('/') ? next : '/' } });
  }),
  route(
    'get',
    '/api/auth/google/callback',
    () => new Response(null, { status: 303, headers: { Location: '/' } }),
  ),
  route('get', '/api/auth/google/mobile/nonce', () =>
    json('/api/auth/google/mobile/nonce', 'get', { nonce: randomChallenge(), state: randomChallenge() }),
  ),
  route('post', '/api/auth/google/mobile', async ({ request }) => {
    const body = await request.json();
    if (!db.user.google_linked || !body.id_token) return fail(401, 'This Google account cannot sign in here');
    return json('/api/auth/google/mobile', 'post', signIn('android', body.device_name) as Tokens);
  }),
  route('delete', '/api/auth/google', () => {
    const denied = unsigned();
    if (denied) return denied;
    db.user.google_linked = false;
    return new Response(null, { status: 204 });
  }),
  route('post', '/api/auth/dev-login', () => json('/api/auth/dev-login', 'post', signIn() as Me)),
];
