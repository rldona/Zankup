import { activitiesQuery, queryKeys } from '@zankup/core';
import { QueryClient } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { delay, http } from 'msw';
import { afterEach, describe, expect, it, onTestFinished, vi } from 'vitest';

import { api } from './api';
import { App } from './App';
import { REST_MS } from './detail/prefetch';
import { activityDetail, activitySummary } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { fail, json, route } from './mocks/route';
import { routes } from './router';

/** The web's own speed (OS-1013), as the app's #320: what is kept first, and pages fetched on intent. */

const ME = { id: 1, email: 'me@example.com', display_name: 'Runner', totp_enabled: true };
const SETTINGS = { theme: 'system', language: 'en', units: 'metric', hr_max: 190, hr_rest: null };
const RUN = activityDetail({ id: 7, streams: { series: ['t', 'hr'], sample_count: 2 } });

/** The paths the app asks for, in order. */
function recordCalls() {
  const calls: string[] = [];
  const listener = ({ request }: { request: Request }) => {
    calls.push(new URL(request.url).pathname);
  };
  server.events.on('request:start', listener);
  onTestFinished(() => server.events.removeListener('request:start', listener));
  return calls;
}

function mockApi() {
  server.use(
    route('get', '/api/activities', () =>
      json('/api/activities', 'get', { items: [activitySummary(RUN)], next_cursor: null }),
    ),
    route('get', '/api/activities/{activity_id}', ({ params }) =>
      params.activity_id === '7' ? json('/api/activities/{activity_id}', 'get', RUN) : undefined,
    ),
    route('get', '/api/activities/{activity_id}/streams', () =>
      json('/api/activities/{activity_id}/streams', 'get', { t: [0, 10], hr: [120, 130] }),
    ),
  );
  return recordCalls();
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

const asked = (calls: string[]) => calls.filter((c) => c.startsWith('/api/activities/7'));

describe('prefetching an activity on intent (OS-1013)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetches the activity, its context and its series once the pointer rests on its card', async () => {
    const calls = mockApi();
    renderAt('/activities');
    const card = (await screen.findByText('Morning run')).closest('a');
    if (!card) throw new Error('no card');
    // Only the card's timer: the mock API's own (setImmediate…) keep running on the real clock.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
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
  it('shows the kept feed while the session is checked, even when the check fails for now', async () => {
    let answer: () => void = () => {};
    const answered = new Promise<void>((resolve) => (answer = resolve));
    server.use(
      // The session check answers late, and with a server error; nothing else answers at all.
      route('get', '/api/auth/me', async () => {
        await answered;
        return fail(500, 'Server error');
      }),
      http.all('*', () => delay('infinite')),
    );
    const calls = recordCalls();
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
    await vi.waitFor(() => expect(calls).toContain('/api/auth/me'));
    await act(async () => answer());
    await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
    expect(screen.getByText('Morning run')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
