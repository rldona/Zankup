/**
 * Account and settings: sign-up (closed: the instance has its account), preferences, the profile's
 * summary, the instance's keys (shown as set or not, never a real secret) and settings, an assistant's
 * OAuth approval, and imports and exports with a past one each. Changes stay until a reload; a new
 * import or export goes through queued and running to done in a few seconds.
 */
import { db } from '../db';
import { fail, json, route, type Req, type Res } from '../route';
import { ago, unsigned } from './auth';

type Preferences = Res<'/api/settings', 'get'>;
type Key = Res<'/api/keys', 'get'>[number];
type Setting = Res<'/api/instance', 'get'>['settings'][number];
type Import = Res<'/api/imports', 'get'>[number];
type Export = Res<'/api/exports', 'get'>[number];
type SportTotals = Res<'/api/profile/summary', 'get'>['this_year'][number];
type ProfileRecord = Res<'/api/profile/summary', 'get'>['records'][number];

const CLOSED = 'Sign-up is closed: this instance already has its account';
const SETUP_CODE = 'ABCDEFGHJKMN';
const PART_SIZE = 8 * 1024 * 1024;
/** How long a new import or export takes, from queued to done. */
const IMPORT_MS = 8000;
const EXPORT_MS = 5000;

/** The settings the apps can change, with their defaults (as the backend's). */
const INSTANCE_DEFAULTS: Record<string, unknown> = {
  ai_model: 'claude-haiku-5-5',
  ai_monthly_budget_usd: 5,
  weekly_report_hour_utc: 6,
  sync_interval_minutes: 30,
  alert_email: null,
  backup_hour_utc: 3,
  backup_keep_days: 14,
  backup_s3_endpoint: null,
  backup_s3_bucket: null,
  backup_s3_region: 'auto',
  backup_s3_access_key_id: null,
  google_health_enabled: false,
};
const MODELS = [
  { id: 'claude-haiku-5-5', input_usd: 0.1, output_usd: 0.5 },
  { id: 'claude-sonnet-5-5', input_usd: 2, output_usd: 10 },
  { id: 'claude-opus-5-5', input_usd: 4, output_usd: 20 },
];

/** The keys set in the environment (none is real: only what the screens show of them). */
const ENV_KEYS: Partial<Record<Key['name'], string>> = { anthropic: '7fXa' };

const state = () =>
  db.slice('account', () => {
    const hrMax = Math.max(0, ...db.activities.map((a) => a.max_hr ?? 0));
    return {
      setup: { open: false, pending: null as null | { name: string; email: string } },
      settings: {
        theme: 'system',
        language: null,
        units: 'metric',
        hr_max: null,
        hr_rest: 52,
        hr_max_estimate: hrMax || null,
        zone_model: 'max',
        lthr: null,
        ftp: 245,
        privacy_zones: [{ id: 'home', name: 'Home', lat: 40.4168, lon: -3.7038, radius_m: 300 }],
        notifications: {
          activity: true,
          record: true,
          sync_error: true,
          garmin_key: true,
          estimate: true,
          import: true,
          health_reauth: true,
          weekly: false,
        },
        ai: { enabled: true, summaries: true, weekly: true },
      } satisfies Preferences as Preferences,
      /** The keys saved from the apps (the rest come from the environment, or are not set). */
      keys: new Map<Key['name'], { last4: string; expires_on: string | null }>([
        ['garmin', { last4: 'k3Q9', expires_on: ago(-75).slice(0, 10) }],
      ]),
      /** The settings saved from the apps. */
      instance: new Map<string, unknown>([['sync_interval_minutes', 15]]),
      nextImport: 2,
      imports: [
        {
          id: 1,
          kind: 'strava',
          status: 'done',
          filename: 'export_48213977.zip',
          size: 412_377_088,
          received: 412_377_088,
          total: 1240,
          processed: 1240,
          created: 214,
          merged: 1003,
          skipped: 21,
          failed: 2,
          photos: 87,
          errors: [
            { file: 'activities/9128377.fit', error: 'The file is truncated' },
            { file: 'activities/9311028.gpx', error: 'No track points' },
          ],
          error: null,
          created_at: ago(200),
          finished_at: ago(200, -1),
        },
      ] satisfies Import[] as Import[],
      /** When each new import started processing, for its progress. */
      importStarted: new Map<number, number>(),
      tokens: new Map<number, string>(),
      nextExport: 2,
      exports: [
        {
          id: 1,
          status: 'done',
          size: 1_204_551_680,
          error: null,
          created_at: ago(2),
          finished_at: ago(2, -1),
          expires_at: ago(-5),
          download_url: '/api/direct/exports/1?token=demo-download-1',
        },
      ] satisfies Export[] as Export[],
      exportStarted: new Map<number, number>(),
    };
  });

/** A brand-new instance: sign-up is open until the account is made (for tests and demos of it). */
export function openSignUp() {
  state().setup.open = true;
}

/** A new import, as far along as the time since it was finished says. */
function progressed(row: Import): Import {
  const started = state().importStarted.get(row.id);
  if (started === undefined || row.status === 'done') return row;
  const share = (Date.now() - started) / IMPORT_MS;
  if (share < 0.1) return { ...row, status: 'queued' };
  if (share < 1) return { ...row, status: 'running', processed: Math.floor(row.total * share) };
  const created = Math.round(row.total * 0.15);
  const skipped = Math.round(row.total * 0.02);
  Object.assign(row, {
    status: 'done',
    processed: row.total,
    created,
    skipped,
    merged: row.total - created - skipped,
    photos: row.kind === 'strava' ? Math.round(row.total * 0.07) : 0,
    finished_at: new Date().toISOString(),
  });
  state().importStarted.delete(row.id);
  return row;
}

function exportProgressed(row: Export): Export {
  const started = state().exportStarted.get(row.id);
  if (started === undefined) return row;
  const share = (Date.now() - started) / EXPORT_MS;
  if (share < 0.2) return row;
  if (share < 1) return { ...row, status: 'running' };
  Object.assign(row, {
    status: 'done',
    size: 1_215_002_624,
    finished_at: new Date().toISOString(),
    expires_at: new Date(Date.now() + 7 * 86_400_000).toISOString(),
    download_url: `/api/direct/exports/${row.id}?token=demo-download-${row.id}`,
  });
  state().exportStarted.delete(row.id);
  return row;
}

function keyStatus(name: Key['name']): Key {
  const saved = state().keys.get(name);
  if (saved)
    return { name, configured: true, source: 'app', last4: saved.last4, expires_on: saved.expires_on };
  const env = ENV_KEYS[name];
  return { name, configured: !!env, source: env ? 'env' : null, last4: env ?? null, expires_on: null };
}

function instance(): Res<'/api/instance', 'get'> {
  const saved = state().instance;
  const settings: Setting[] = Object.entries(INSTANCE_DEFAULTS).map(([name, value]) =>
    saved.has(name)
      ? { name, value: saved.get(name), source: 'app' }
      : { name, value, source: name === 'alert_email' ? 'env' : 'default' },
  );
  return { settings, models: MODELS };
}

const yearOf = (iso: string) => Number(iso.slice(0, 4));

function totals(activities: typeof db.activities): SportTotals[] {
  const by = new Map<string, SportTotals>();
  for (const a of activities) {
    const t = by.get(a.sport) ?? { sport: a.sport, count: 0, distance_m: 0, moving_s: 0, ascent_m: 0 };
    t.count++;
    t.distance_m += a.distance_m ?? 0;
    t.moving_s += a.moving_s ?? 0;
    t.ascent_m += a.ascent_m ?? 0;
    by.set(a.sport, t);
  }
  return [...by.values()].sort((x, y) => y.count - x.count);
}

/** The best time at each running distance, at the run's average pace. */
function records(): ProfileRecord[] {
  const keys: [string, number][] = [
    ['5k', 5000],
    ['10k', 10000],
    ['15k', 15000],
    ['half_marathon', 21097.5],
  ];
  const runs = db.activities.filter((a) => a.sport === 'run' && a.distance_m && a.moving_s);
  return keys.flatMap(([key, distance]) => {
    let best: ProfileRecord | null = null;
    for (const a of runs) {
      if ((a.distance_m ?? 0) < distance) continue;
      const time = Math.round(((a.moving_s ?? 0) / (a.distance_m ?? 1)) * distance);
      if (!best || time < best.time_s)
        best = {
          distance_key: key,
          distance_m: distance,
          time_s: time,
          activity_id: a.id,
          date: a.start_utc,
        };
    }
    return best ? [best] : [];
  });
}

/** Weeks in a row with an activity, up to this week or the last. */
function streak(): number {
  const week = (d: Date) => {
    const monday = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7));
    return monday.getTime();
  };
  const weeks = new Set(db.activities.map((a) => week(new Date(a.start_utc))));
  let at = week(db.today);
  if (!weeks.has(at)) at -= 7 * 86_400_000;
  let n = 0;
  while (weeks.has(at)) {
    n++;
    at = week(new Date(at - 3 * 86_400_000));
  }
  return n;
}

/** An empty ZIP: the export's file, as far as a browser can tell. */
const EMPTY_ZIP = new Uint8Array([0x50, 0x4b, 0x05, 0x06, ...new Array<number>(18).fill(0)]);

export const accountHandlers = [
  // --- sign-up -----------------------------------------------------------------------------------
  route('get', '/api/setup', () => json('/api/setup', 'get', { open: state().setup.open })),
  route('post', '/api/setup/account', async ({ request }) => {
    const s = state().setup;
    if (!s.open) return fail(410, CLOSED);
    const body: Req<'/api/setup/account', 'post'> = await request.json();
    if (body.code.toUpperCase().replace(/[^A-Z0-9]/g, '') !== SETUP_CODE)
      return fail(401, 'That setup code is not right');
    if (!body.accept) return fail(422, 'Accept how your data is handled to continue');
    const email = body.email.trim().toLowerCase();
    if (!/^[^@]+@[^@]+\.[^@]+$/.test(email)) return fail(422, 'Enter a valid e-mail');
    if (body.password.length < 12) return fail(422, 'The password must have at least 12 characters');
    s.pending = { name: body.display_name.trim(), email };
    const secret = 'JBSWY3DPEHPK3PXP';
    const uri = `otpauth://totp/Zankup:${encodeURIComponent(email)}?secret=${secret}&issuer=Zankup`;
    return json('/api/setup/account', 'post', {
      setup_token: 'demo-setup-token',
      secret,
      uri,
      qr_svg:
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 29 29" width="145" height="145"><rect width="29" height="29" fill="#fff"/><path d="M2 2h7v7H2zM20 2h7v7h-7zM2 20h7v7H2zM12 12h5v5h-5z" fill="#000"/></svg>',
    });
  }),
  route('post', '/api/setup/totp', async ({ request }) => {
    const s = state().setup;
    if (!s.open) return fail(410, CLOSED);
    const body = await request.json();
    if (!s.pending || body.setup_token !== 'demo-setup-token')
      return fail(401, 'That took too long: start again with the setup code');
    if (body.code !== '123456')
      return fail(401, "That code does not match: check the app's clock and try the next one");
    Object.assign(db.user, { email: s.pending.email, display_name: s.pending.name, totp_enabled: true });
    s.open = false;
    db.signedIn = true;
    const recovery_codes = Array.from({ length: 8 }, (_, i) => `demo${i + 1}-codes`);
    return json('/api/setup/totp', 'post', {
      me: db.user,
      recovery_codes,
      tokens:
        body.client && body.client !== 'web'
          ? {
              access_token: 'demo-access',
              refresh_token: 'demo-refresh-setup',
              token_type: 'bearer',
              expires_in: 900,
            }
          : null,
    });
  }),

  // --- preferences and profile -------------------------------------------------------------------
  route('get', '/api/settings', () => unsigned() ?? json('/api/settings', 'get', state().settings)),
  route('patch', '/api/settings', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const body = await request.json();
    const s = state();
    const next = { ...s.settings };
    for (const [name, value] of Object.entries(body)) {
      // Null clears the language and the numbers; the rest keep their value.
      if (value === null && !['language', 'hr_max', 'hr_rest', 'lthr', 'ftp'].includes(name)) continue;
      Object.assign(next, { [name]: value });
    }
    s.settings = next;
    return json('/api/settings', 'patch', next);
  }),
  route('get', '/api/profile/summary', () => {
    const denied = unsigned();
    if (denied) return denied;
    const year = db.today.getFullYear();
    const oldest = db.activities.at(-1);
    return json('/api/profile/summary', 'get', {
      year,
      this_year: totals(db.activities.filter((a) => yearOf(a.start_utc) === year)),
      all_time: totals(db.activities),
      since: oldest?.start_utc ?? null,
      records: records(),
      streak_weeks: streak(),
      device: db.activities[0]?.device ?? null,
    });
  }),

  // --- the instance ------------------------------------------------------------------------------
  route(
    'get',
    '/api/keys',
    () =>
      unsigned() ??
      json('/api/keys', 'get', (['garmin', 'anthropic', 'resend', 'backup_s3'] as const).map(keyStatus)),
  ),
  route('put', '/api/keys/{name}', async ({ params, request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const name = params.name as Key['name'];
    if (!['garmin', 'anthropic', 'resend', 'backup_s3'].includes(name)) return fail(422, 'Unknown key');
    const body = await request.json();
    const value = body.value.trim();
    if (value.length < 8) return fail(422, 'String should have at least 8 characters');
    if (/^(bad|invalid)/i.test(value)) return fail(422, 'The service refused this key');
    state().keys.set(name, {
      last4: value.slice(-4),
      expires_on: name === 'garmin' ? (body.expires_on ?? null) : null,
    });
    return json('/api/keys/{name}', 'put', keyStatus(name));
  }),
  route('delete', '/api/keys/{name}', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const name = params.name as Key['name'];
    state().keys.delete(name);
    return json('/api/keys/{name}', 'delete', keyStatus(name));
  }),
  route('get', '/api/instance', () => unsigned() ?? json('/api/instance', 'get', instance())),
  route('patch', '/api/instance', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const changes = (await request.json()) as Record<string, unknown>;
    const unknown = Object.keys(changes).find((name) => !(name in INSTANCE_DEFAULTS));
    if (unknown) return fail(422, `${unknown} cannot be set from the apps`);
    const saved = state().instance;
    for (const [name, value] of Object.entries(changes)) {
      if (value === null) saved.delete(name);
      else saved.set(name, value);
    }
    return json('/api/instance', 'patch', instance());
  }),

  // --- an assistant's access (OAuth) -------------------------------------------------------------
  route('get', '/api/oauth/client', ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const url = new URL(request.url);
    if (!url.searchParams.get('client_id') || !url.searchParams.get('redirect_uri'))
      return fail(400, 'Unknown assistant or address');
    return json('/api/oauth/client', 'get', { name: 'Claude' });
  }),
  route('post', '/api/oauth/approve', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const body = await request.json();
    if ((body.code_challenge_method ?? 'S256') !== 'S256') return fail(400, 'Only S256');
    const redirect = new URL(body.redirect_uri);
    redirect.searchParams.set('code', 'demo-authorization-code');
    if (body.state) redirect.searchParams.set('state', body.state);
    return json('/api/oauth/approve', 'post', { redirect: redirect.toString() });
  }),

  // --- imports -----------------------------------------------------------------------------------
  route(
    'get',
    '/api/imports',
    () => unsigned() ?? json('/api/imports', 'get', state().imports.map(progressed)),
  ),
  route('post', '/api/imports', async ({ request }) => {
    const denied = unsigned();
    if (denied) return denied;
    const body = await request.json();
    const s = state();
    const row: Import = {
      id: s.nextImport++,
      kind: null,
      status: 'uploading',
      filename: body.filename.split(/[\\/]/).pop()?.slice(0, 255) || 'upload',
      size: body.size,
      received: 0,
      total: 0,
      processed: 0,
      created: 0,
      merged: 0,
      skipped: 0,
      failed: 0,
      photos: 0,
      errors: [],
      error: null,
      created_at: new Date().toISOString(),
      finished_at: null,
    };
    s.imports.unshift(row);
    const token = `demo-upload-${row.id}`;
    s.tokens.set(row.id, token);
    return json(
      '/api/imports',
      'post',
      { import: row, upload: { url: `/api/direct/uploads/${row.id}`, token, part_size: PART_SIZE } },
      { status: 201 },
    );
  }),
  route('post', '/api/imports/{import_id}/finish', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const s = state();
    const row = s.imports.find((i) => i.id === Number(params.import_id));
    if (!row) return fail(404, 'Import not found');
    if (row.status !== 'uploading') return json('/api/imports/{import_id}/finish', 'post', progressed(row));
    if (row.received < row.size) return fail(409, 'The upload is not complete');
    const name = row.filename.toLowerCase();
    row.kind = name.endsWith('.zip') ? (name.startsWith('export_') ? 'strava' : 'archive') : 'file';
    row.total = row.kind === 'file' ? 1 : Math.max(1, Math.round(row.size / 300_000));
    row.status = 'queued';
    s.importStarted.set(row.id, Date.now());
    return json('/api/imports/{import_id}/finish', 'post', row);
  }),
  route('get', '/api/imports/{import_id}', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const row = state().imports.find((i) => i.id === Number(params.import_id));
    return row ? json('/api/imports/{import_id}', 'get', progressed(row)) : fail(404, 'Import not found');
  }),
  route('delete', '/api/imports/{import_id}', ({ params }) => {
    const denied = unsigned();
    if (denied) return denied;
    const s = state();
    const row = s.imports.find((i) => i.id === Number(params.import_id));
    if (!row) return fail(404, 'Import not found');
    const now = progressed(row);
    if (now.status === 'queued' || now.status === 'running') return fail(409, 'The import is running');
    s.imports = s.imports.filter((i) => i !== row);
    return new Response(null, { status: 204 });
  }),
  route('put', '/api/direct/uploads/{import_id}/{index}', async ({ params, request }) => {
    const s = state();
    const id = Number(params.import_id);
    const token = new URL(request.url).searchParams.get('token');
    if (!token || s.tokens.get(id) !== token) return fail(401, 'This upload link is not valid');
    const row = s.imports.find((i) => i.id === id);
    if (!row || row.status !== 'uploading') return fail(409, 'This upload is not waiting for data');
    const index = Number(params.index);
    const parts = Math.ceil(row.size / PART_SIZE);
    if (!(index >= 0 && index < parts)) return fail(400, 'Part out of range');
    const size = (await request.arrayBuffer()).byteLength;
    const expected = index === parts - 1 ? row.size - index * PART_SIZE : PART_SIZE;
    if (size !== expected) return fail(400, `Part ${index} must be ${expected} bytes`);
    row.received = Math.min(row.size, row.received + size);
    return new Response(null, { status: 204 });
  }),

  // --- exports -----------------------------------------------------------------------------------
  route('get', '/api/exports', () => {
    const denied = unsigned();
    if (denied) return denied;
    const now = Date.now();
    return json(
      '/api/exports',
      'get',
      state()
        .exports.map(exportProgressed)
        .filter((e) => !e.expires_at || new Date(e.expires_at).getTime() > now)
        .slice(0, 5),
    );
  }),
  route('post', '/api/exports', () => {
    const denied = unsigned();
    if (denied) return denied;
    const s = state();
    const running = s.exports
      .map(exportProgressed)
      .find((e) => e.status === 'queued' || e.status === 'running');
    if (running) return json('/api/exports', 'post', running, { status: 201 });
    const row: Export = {
      id: s.nextExport++,
      status: 'queued',
      size: null,
      error: null,
      created_at: new Date().toISOString(),
      finished_at: null,
      expires_at: null,
      download_url: null,
    };
    s.exports.unshift(row);
    s.exportStarted.set(row.id, Date.now());
    return json('/api/exports', 'post', row, { status: 201 });
  }),
  route('get', '/api/direct/exports/{export_id}', ({ params, request }) => {
    const id = Number(params.export_id);
    if (new URL(request.url).searchParams.get('token') !== `demo-download-${id}`)
      return fail(401, 'This download link has expired');
    const row = state().exports.find((e) => e.id === id);
    if (!row || exportProgressed(row).status !== 'done') return fail(404, 'Export not found');
    return new Response(EMPTY_ZIP, {
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="zankup-export-${id}.zip"`,
      },
    });
  }),
];
