import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { routes } from './router';

const ME = { id: 1, email: 'me@example.com', display_name: 'Runner', totp_enabled: true, weight_kg: 72.5 };
const night = {
  source: 'garmin',
  start_utc: '2026-11-08T00:00:00Z',
  end_utc: '2026-11-08T07:30:00Z',
  utc_offset_s: 3600,
  asleep_s: 7 * 3600,
  awake_s: 1800,
  light_s: 4 * 3600,
  deep_s: 3600,
  rem_s: 2 * 3600,
  score: 86,
  others: [],
};
const DAYS = [
  {
    day: '2026-11-08',
    sleep: night,
    resting_hr: { value: 49, source: 'garmin', others: [] },
    hrv: { value: 58, source: 'garmin', others: [] },
    spo2: null,
    respiration: null,
    steps: null,
    tsb: 2,
    readiness: { score: 74, hrv: 80, resting_hr: 65, sleep: 86, form: 63 },
  },
];
const WEIGHT = [
  { day: '2026-10-01', weight_kg: 74 },
  { day: '2026-11-01', weight_kg: 73 },
  { day: '2026-11-06', weight_kg: 72.5 },
];

type Handler = (request: Request) => Response | Promise<Response>;

function mockApi(extra: Record<string, Handler> = {}) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  const handlers: Record<string, Handler> = {
    '/api/auth/me': () => Response.json(ME),
    '/api/wellness/days': () => Response.json(DAYS),
    '/api/wellness/nights/2026-11-08': () => Response.json({ day: '2026-11-08', chosen: night, stages: [] }),
    '/api/weight': () => Response.json(WEIGHT),
    'PUT /api/weight': () => Response.json({ day: '2026-11-08', weight_kg: 71.8 }),
    'DELETE /api/weight/2026-11-06': () => new Response(null, { status: 204 }),
    ...extra,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request | string, init?: RequestInit) => {
      const request =
        typeof input === 'string' ? new Request(new URL(input, 'http://localhost'), init) : input;
      const url = new URL(request.url);
      const text = request.method === 'GET' ? '' : await request.clone().text();
      calls.push({ method: request.method, path: url.pathname, body: text ? JSON.parse(text) : null });
      const handler = handlers[`${request.method} ${url.pathname}`] ?? handlers[url.pathname];
      return handler ? handler(request) : Response.json({ detail: 'Not Found' }, { status: 404 });
    }),
  );
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('weight over time (OS-1009)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-08T12:00:00'));
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('shows the Weight card on Health with the latest and its change in 30 days, and opens its page', async () => {
    mockApi();
    const router = renderAt('/health');
    const card = await screen.findByRole('link', { name: 'Weight, 72.5 kg' });
    expect(within(card).getByText('72.5 kg')).toBeInTheDocument();
    expect(within(card).getByText('−0.5 kg in 30 days')).toBeInTheDocument();
    await userEvent.click(card);
    await waitFor(() => expect(router.state.location.pathname).toBe('/health/weight'));
    expect(await screen.findByRole('heading', { name: 'Weight', level: 1 })).toBeInTheDocument();
    // The history, newest first, with the step from the one before.
    const rows = screen.getAllByRole('button', { name: /^Edit weight:/ });
    expect(rows.map((r) => r.getAttribute('aria-label'))).toEqual([
      'Edit weight: November 6, 2026, 72.5 kg',
      'Edit weight: November 1, 2026, 73 kg',
      'Edit weight: October 1, 2026, 74 kg',
    ]);
    expect(within(rows[0] as HTMLElement).getByText('−0.5')).toBeInTheDocument();
    // The way back to Health above the page, beside the tab bar's.
    expect(document.querySelector('.crumb')).toHaveAttribute('href', '/health');
  });

  it('asks for the first weighing when there is none', async () => {
    mockApi({ '/api/weight': () => Response.json([]) });
    renderAt('/health');
    expect(await screen.findByText(/You haven't added a weight yet/)).toBeInTheDocument();
  });

  it('adds a weighing from the dialog with PUT /api/weight', async () => {
    const calls = mockApi();
    renderAt('/health/weight');
    await userEvent.click(await screen.findByRole('button', { name: 'Add weight' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add weight' });
    const input = within(dialog).getByLabelText('Weight (kg)');
    await userEvent.clear(input);
    await userEvent.type(input, '71,8');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PUT',
        path: '/api/weight',
        body: { day: '2026-11-08', weight_kg: 71.8 },
      }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('deletes a weighing after a confirmation', async () => {
    const calls = mockApi();
    renderAt('/health/weight');
    await userEvent.click(await screen.findByRole('button', { name: 'Delete: November 6, 2026' }));
    const confirm = await screen.findByRole('dialog', { name: 'Delete' });
    expect(within(confirm).getByText('Delete the weight of November 6, 2026?')).toBeInTheDocument();
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));
    await waitFor(() =>
      expect(calls.map((c) => `${c.method} ${c.path}`)).toContain('DELETE /api/weight/2026-11-06'),
    );
  });
});

describe('whose each health figure is (OS-1011)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('marks the sleep score and Readiness as worked out by Zankup', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-08T12:00:00'));
    mockApi();
    renderAt('/health');
    await screen.findByRole('heading', { name: 'Last night' });
    const marks = screen.getAllByRole('img', { name: 'Score worked out by Zankup' });
    expect(marks).toHaveLength(2);
    const score = screen.getByText('Sleep score').closest('article');
    expect(
      score && within(score).getByRole('img', { name: 'Score worked out by Zankup' }),
    ).toBeInTheDocument();
    const readiness = screen.getByRole('heading', { name: 'Readiness' }).closest('article');
    expect(
      readiness && within(readiness).getByRole('img', { name: 'Score worked out by Zankup' }),
    ).toBeInTheDocument();
    // The hours asleep carry their source's mark instead.
    const duration = screen.getByText('Sleep duration').closest('article');
    expect(duration && within(duration).getByRole('img', { name: 'From Garmin' })).toBeInTheDocument();
  });
});
