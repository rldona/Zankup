import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { fail, json, route, type Res } from './mocks/route';
import { routes } from './router';

const SETTINGS: Res<'/api/settings', 'get'> = {
  theme: 'system',
  language: null,
  units: 'metric',
  hr_max: 190,
  hr_rest: 50,
  hr_max_estimate: 188,
  zone_model: 'max',
  lthr: null,
  ftp: null,
  privacy_zones: [],
  notifications: {
    activity: true,
    record: true,
    sync_error: true,
    garmin_key: true,
    estimate: true,
    import: true,
    health_reauth: true,
    weekly: true,
  },
  ai: { enabled: true, summaries: true, weekly: true },
};
const STATUS: Res<'/api/ai/status', 'get'> = {
  available: true,
  enabled: true,
  summaries: true,
  weekly: true,
  model: 'claude-haiku-5-5',
  budget: { month_cost_usd: 0.42, limit_usd: 5, warning: false, exceeded: false },
  sends: ['sport, date and distance'],
  never_sends: ['coordinates and routes'],
};
const SUMMARY: Res<'/api/activities/{activity_id}/summary', 'get'> = {
  activity_id: 7,
  language: 'en',
  suggested_title: 'Steady 10 km with a 5 km record',
  summary: 'A steady 10.0 km at 5:50 min/km, a little faster than usual.',
  highlights: ['5 km record'],
  model: 'claude-haiku-5-5',
  created_at: '2026-11-04T09:00:00Z',
};
const REPORT: Res<'/api/ai/weekly', 'get'>[number] = {
  id: 1,
  week_start: '2026-11-02',
  status: 'done',
  language: 'en',
  headline: 'A solid week',
  summary: 'You ran 32.0 km over four sessions.',
  recommendation: 'Keep the volume and add one easy day.',
  highlights: ['32 km', '5 km record'],
  error: null,
  created_at: '2026-11-09T06:00:00Z',
  finished_at: '2026-11-09T06:20:00Z',
};

const statusIs = (status: Res<'/api/ai/status', 'get'>) =>
  route('get', '/api/ai/status', () => json('/api/ai/status', 'get', status));

/** The fixtures above for the AI; the activities (activity 7 among them) are the demo athlete's. */
function useFixtures() {
  server.use(
    route('get', '/api/settings', () => json('/api/settings', 'get', SETTINGS)),
    statusIs(STATUS),
    route('get', '/api/ai/tokens', () => json('/api/ai/tokens', 'get', [])),
    route('get', '/api/ai/weekly', () => json('/api/ai/weekly', 'get', [REPORT])),
  );
}

/** Records every request: method, path and JSON body (null for GET). */
function record() {
  const calls: { method: string; path: string; body: unknown }[] = [];
  server.events.on('request:start', async ({ request }) => {
    const text = request.method === 'GET' ? '' : await request.clone().text();
    calls.push({
      method: request.method,
      path: new URL(request.url).pathname,
      body: text ? (JSON.parse(text) as unknown) : null,
    });
  });
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

const ndjson = (events: object[]) =>
  new Response(
    new ReadableStream({
      start(controller) {
        const encoder = new TextEncoder();
        for (const event of events) controller.enqueue(encoder.encode(JSON.stringify(event) + '\n'));
        controller.close();
      },
    }),
    { headers: { 'Content-Type': 'application/x-ndjson' } },
  );

describe('phase 7: the AI assistant', () => {
  afterEach(() => {
    server.events.removeAllListeners();
    vi.unstubAllGlobals();
  });

  it("shows the Coach in Home's header only when this server has a model and the switch is on", async () => {
    useFixtures();
    renderAt('/');
    // In Home's header (on a phone) and at the foot of the rail.
    const header = within(await screen.findByRole('main'));
    expect(await header.findByRole('link', { name: 'Coach' })).toHaveAttribute('href', '/coach');
    expect(await screen.findAllByRole('link', { name: 'Coach' })).toHaveLength(2);
    server.use(statusIs({ ...STATUS, available: false, model: null, budget: null }));
    renderAt('/coach');
    expect(
      await screen.findByText('Zankup Coach turns on as soon as you save an Anthropic key above.'),
    ).toBeInTheDocument();
  });

  it('answers a question as it streams, with the tools it used and links to activities', async () => {
    useFixtures();
    server.use(
      route('post', '/api/ai/coach', () =>
        ndjson([
          { type: 'tool', name: 'search_activities', status: 'running' },
          { type: 'tool', name: 'search_activities', status: 'done' },
          { type: 'text', text: 'You ran **16.0 km** in two runs' },
          {
            type: 'text',
            text: ', the longest being /activities/7.\n\n- 10.0 km on Wednesday\n- 6.0 km on Monday',
          },
          { type: 'done', tools: ['search_activities'], usage: {} },
        ]),
      ),
    );
    const calls = record();
    renderAt('/coach');
    expect(await screen.findByRole('heading', { name: "Coach's weekly report" })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'A solid week' })).toBeInTheDocument();
    expect(screen.getByText(/Keep the volume/)).toBeInTheDocument();
    const box = screen.getByRole('textbox', { name: 'Ask the coach…' });
    await userEvent.type(box, 'How much did I run this month?{Enter}');
    expect(await screen.findByText(/You ran 16.0 km in two runs/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Activity 7' })).toHaveAttribute('href', '/activities/7');
    expect(screen.getByText('10.0 km on Wednesday')).toBeInTheDocument();
    const sent = calls.find((c) => c.path === '/api/ai/coach');
    expect(sent?.body).toEqual({ messages: [{ role: 'user', content: 'How much did I run this month?' }] });
    await userEvent.click(screen.getByRole('button', { name: 'New conversation' }));
    expect(screen.getByText('How am I doing for a 10K?')).toBeInTheDocument();
  });

  it('tells the user when the budget is spent instead of asking', async () => {
    useFixtures();
    server.use(
      statusIs({ ...STATUS, budget: { month_cost_usd: 5, limit_usd: 5, warning: true, exceeded: true } }),
    );
    renderAt('/coach');
    expect(await screen.findByText(/This month's Zankup Coach budget is spent/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('shows the summary on the activity and lets the user write it and take the title', async () => {
    let summary: typeof SUMMARY | null = null;
    useFixtures();
    server.use(
      route('get', '/api/activities/{activity_id}/summary', () =>
        summary ? json('/api/activities/{activity_id}/summary', 'get', summary) : fail(404, 'No summary yet'),
      ),
      route('post', '/api/activities/{activity_id}/summary', () => {
        summary = SUMMARY;
        return json('/api/activities/{activity_id}/summary', 'post', SUMMARY);
      }),
    );
    const calls = record();
    renderAt('/activities/7');
    expect(await screen.findByRole('heading', { name: 'Zankup Coach' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ask for a summary' }));
    expect(await screen.findByText(/A steady 10.0 km/)).toBeInTheDocument();
    expect(screen.getByText('5 km record')).toBeInTheDocument();
    expect(screen.getByText('Steady 10 km with a 5 km record')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Use this title' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({
        name: 'Steady 10 km with a 5 km record',
      }),
    );
    // A written summary stays as it is: no button to write it again.
    expect(screen.queryByRole('button', { name: 'Ask for a summary' })).not.toBeInTheDocument();
  });

  it('"Tell me more" writes the longer report and shows the recommendation apart', async () => {
    const REPORT_ROW: Res<'/api/activities/{activity_id}/report', 'post'> = {
      activity_id: 7,
      language: 'en',
      headline: 'A steady threshold run.',
      effort: 'Relative effort above your usual.',
      story: '10.0 km at 5:50 min/km.',
      month: '32 km so far this month.',
      verdict: 'A session that adds up.',
      next: 'Tomorrow, **40 easy minutes** in zone 2.',
      model: 'claude-haiku-5-5',
      created_at: '2026-11-04T09:00:00Z',
    };
    useFixtures();
    server.use(
      route('get', '/api/activities/{activity_id}/summary', () =>
        json('/api/activities/{activity_id}/summary', 'get', SUMMARY),
      ),
      route('post', '/api/activities/{activity_id}/report', () =>
        json('/api/activities/{activity_id}/report', 'post', REPORT_ROW),
      ),
    );
    const calls = record();
    renderAt('/activities/7');
    await userEvent.click(await screen.findByRole('button', { name: 'Tell me more' }));
    expect(await screen.findByText('A steady threshold run.')).toBeInTheDocument();
    expect(screen.getByText('For your next session')).toBeInTheDocument();
    expect(screen.getByText('40 easy minutes').tagName).toBe('STRONG'); // its **bold**
    expect(calls.filter((c) => c.path === '/api/activities/7/report')).toHaveLength(1);
  });

  it('has the AI switches, the explanation and the MCP tokens in Settings', async () => {
    let tokens: Res<'/api/ai/tokens', 'get'> = [];
    useFixtures();
    server.use(
      route('get', '/api/ai/tokens', () => json('/api/ai/tokens', 'get', tokens)),
      route('post', '/api/ai/tokens', () => {
        tokens = [
          {
            id: 3,
            name: 'Claude Desktop',
            prefix: 'zk_abcde',
            created_at: '2026-11-09T10:00:00Z',
            last_used_at: null,
          },
        ];
        const [created] = tokens;
        if (!created) throw new Error('no token');
        return json(
          '/api/ai/tokens',
          'post',
          { ...created, token: 'zk_abcdefghij', url: '/api/mcp' },
          { status: 201 },
        );
      }),
      route('patch', '/api/settings', async ({ request }) => {
        const body = await request.json();
        return json('/api/settings', 'patch', { ...SETTINGS, ai: { ...SETTINGS.ai, ...body.ai } });
      }),
      route('delete', '/api/ai/tokens/{token_id}', () => {
        tokens = [];
        return new Response(null, { status: 204 });
      }),
    );
    const calls = record();
    renderAt('/settings/ai');
    expect(await screen.findByRole('heading', { name: 'Zankup Coach' })).toBeInTheDocument();
    expect(await screen.findByText('coordinates and routes')).toBeInTheDocument();
    expect(screen.getByText(/\$0\.42 of \$5\.00 spent this month/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Use Zankup Coach' }));
    await waitFor(() =>
      expect(calls.find((c) => c.method === 'PATCH' && c.path === '/api/settings')?.body).toEqual({
        ai: { enabled: false, summaries: true, weekly: true },
      }),
    );
    await userEvent.type(screen.getByRole('textbox', { name: 'Token name' }), 'Claude Desktop');
    await userEvent.click(screen.getByRole('button', { name: 'Create token' }));
    expect(await screen.findByDisplayValue('zk_abcdefghij')).toBeInTheDocument();
    expect(screen.getByText('Copy it now: it is shown only once.')).toBeInTheDocument();
    expect(await screen.findByText('Claude Desktop', { selector: 'b' })).toBeInTheDocument();
    vi.stubGlobal('confirm', () => true);
    await userEvent.click(screen.getByRole('button', { name: 'Revoke' }));
    await waitFor(() =>
      expect(calls.some((c) => c.method === 'DELETE' && c.path === '/api/ai/tokens/3')).toBe(true),
    );
    expect(await screen.findByText('No tokens yet.')).toBeInTheDocument();
  });

  it('the Coach as on the app: two examples, and its note behind the (i) (OS-915)', async () => {
    useFixtures();
    renderAt('/coach');
    expect(await screen.findByText('Questions you could ask:')).toBeInTheDocument();
    const chat = screen.getByRole('region', { name: 'Zankup Coach' });
    expect(
      within(chat)
        .getAllByRole('button')
        .filter((b) => b.textContent?.endsWith('?')),
    ).toHaveLength(2);
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'About the coach' }));
    expect(screen.getByRole('note')).toHaveTextContent(/can be wrong/);
  });
});

describe('connecting an AI assistant by OAuth', () => {
  afterEach(() => {
    server.events.removeAllListeners();
    vi.unstubAllGlobals();
  });

  const query =
    '?response_type=code&client_id=abc&redirect_uri=' +
    encodeURIComponent('https://claude.ai/api/mcp/auth_callback') +
    '&code_challenge=' +
    'c'.repeat(43) +
    '&code_challenge_method=S256&state=xyz';

  it('says who asks and, allowed, sends the code back to the assistant', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    server.use(
      route('get', '/api/oauth/client', () => json('/api/oauth/client', 'get', { name: 'Claude' })),
      route('post', '/api/oauth/approve', () =>
        json('/api/oauth/approve', 'post', {
          redirect: 'https://claude.ai/api/mcp/auth_callback?code=k&state=xyz',
        }),
      ),
    );
    const calls = record();
    renderAt(`/oauth/authorize${query}`);
    expect(await screen.findByRole('heading', { name: 'Claude wants to access your Zankup' })).toBeVisible();
    expect(screen.getByText('If you allow it, Claude will be able to, on your behalf:')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Allow' }));
    await waitFor(() =>
      expect(assign).toHaveBeenCalledWith('https://claude.ai/api/mcp/auth_callback?code=k&state=xyz'),
    );
    const approve = calls.find((c) => c.path === '/api/oauth/approve');
    expect(approve?.body).toMatchObject({ client_id: 'abc', state: 'xyz', code_challenge_method: 'S256' });
  });

  it('refuses a link that is not a valid request', async () => {
    server.use(route('get', '/api/oauth/client', () => fail(400, 'no')));
    renderAt(`/oauth/authorize${query}`);
    expect(await screen.findByText(/not valid or has expired/)).toBeVisible();
  });
});
