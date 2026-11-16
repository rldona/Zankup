import { activitiesQuery, queryKeys } from '@zankup/core';
import { QueryClient } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from './api';
import { App } from './App';
import { REST_MS } from './detail/prefetch';
import { routes } from './router';

/** The web's own speed (OS-1013), as the app's #320: what is kept first, and pages fetched on intent. */

const ME = { id: 1, email: 'me@example.com', display_name: 'Runner', totp_enabled: true };
const SETTINGS = { theme: 'system', language: 'en', units: 'metric', hr_max: 190, hr_rest: null };
const RUN = {
  id: 7,
  name: 'Morning run',
  sport: 'run',
  sub_sport: null,
  start_utc: '2026-11-12T08:00:00Z',
  timezone: '+01:00',
  duration_s: 600,
  moving_s: 590,
  distance_m: 3000,
  ascent_m: 40,
  avg_hr: 140,
  avg_power: null,
  has_gps: false,
  route: null,
  photo: null,
  photos: 0,
  streams: { series: ['t', 'hr'], sample_count: 2 },
};

function mockApi() {
  const calls: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request | string, init?: RequestInit) => {
      const request =
        typeof input === 'string' ? new Request(new URL(input, 'http://localhost'), init) : input;
      const path = new URL(request.url).pathname;
      calls.push(path);
      const data = {
        '/api/auth/me': ME,
        '/api/settings': SETTINGS,
        '/api/activities': { items: [RUN], next_cursor: null },
        '/api/activities/7': RUN,
        '/api/activities/7/streams': { t: [0, 10], hr: [120, 130] },
      }[path];
      return data === undefined
        ? Response.json({ detail: 'Not Found' }, { status: 404 })
        : Response.json(data);
    }),
  );
  return calls;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

const asked = (calls: string[]) => calls.filter((c) => c.startsWith('/api/activities/7'));

describe('prefetching an activity on intent (OS-1013)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('fetches the activity, its context and its series once the pointer rests on its card', async () => {
    const calls = mockApi();
    renderAt('/activities');
    const card = (await screen.findByText('Morning run')).closest('a');
    if (!card) throw new Error('no card');
    vi.useFakeTimers();
    // Passing over the card asks for nothing.
    fireEvent.pointerOver(card, { pointerType: 'mouse' });
    fireEvent.pointerOut(card, { pointerType: 'mouse' });
    await act(() => vi.advanceTimersByTimeAsync(REST_MS * 2));
    expect(asked(calls)).toEqual([]);
    // Resting on it does.
    fireEvent.pointerOver(card, { pointerType: 'mouse' });
    await act(() => vi.advanceTimersByTimeAsync(REST_MS));
    vi.useRealTimers();
    await vi.waitFor(() =>
      expect(new Set(asked(calls))).toEqual(
        new Set(['/api/activities/7', '/api/activities/7/context', '/api/activities/7/streams']),
      ),
    );
  });

  it('fetches at once when a finger touches the card', async () => {
    const calls = mockApi();
    renderAt('/activities');
    const card = (await screen.findByText('Morning run')).closest('a');
    if (!card) throw new Error('no card');
    fireEvent.touchStart(card);
    await vi.waitFor(() => expect(asked(calls)).toContain('/api/activities/7'));
  });
});

describe('what is kept draws first (OS-1013)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the kept feed while the session is checked, even when the check fails for now', async () => {
    let answer: (response: Response) => void = () => {};
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const path = new URL(typeof input === 'string' ? input : input.url, 'http://localhost').pathname;
        // The session check answers late, and with a server error; nothing else answers at all.
        if (path === '/api/auth/me') return new Promise<Response>((resolve) => (answer = resolve));
        return new Promise<Response>(() => {});
      }),
    );
    // What IndexedDB restored on opening (OS-211), from a visit a day ago: all of it is asked again.
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const kept = { updatedAt: Date.now() - 24 * 60 * 60 * 1000 };
    queryClient.setQueryData(queryKeys.me, ME, kept);
    queryClient.setQueryData(queryKeys.settings, SETTINGS, kept);
    queryClient.setQueryData(
      activitiesQuery(api).queryKey,
      { pages: [{ items: [RUN], next_cursor: null }], pageParams: [undefined] },
      kept,
    );
    render(
      <App
        queryClient={queryClient}
        router={createMemoryRouter(routes, { initialEntries: ['/activities'] })}
      />,
    );
    expect(await screen.findByText('Morning run')).toBeInTheDocument();
    expect(document.querySelector('[aria-busy="true"]')).toBeNull();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
    await act(async () => answer(Response.json({ detail: 'Server error' }, { status: 500 })));
    await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
    expect(screen.getByText('Morning run')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
