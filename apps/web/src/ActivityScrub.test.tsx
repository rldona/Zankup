import { QueryClient } from '@tanstack/react-query';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { Profiler } from 'react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { activityContext, activityDetail, activitySummary } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

/**
 * Scrubbing an activity's chart redraws only what follows the cursor (OS-1013, as the app's #321):
 * the map's dot and the replay's position, not the page, its sections or the other charts. (jsdom has
 * no WebGL, so the map is its fallback here; the dot reads the same store as the seek bar.)
 */

const renders = vi.hoisted(() => ({ page: 0, charts: 0 }));

// The page's own render: the activity's page is the only one on it that reads a media query.
vi.mock('./media', async (importOriginal) => {
  const media = await importOriginal<typeof import('./media')>();
  return {
    ...media,
    useMedia: (query: string) => {
      renders.page += 1;
      return media.useMedia(query);
    },
  };
});

// Each chart as a stand-in that counts its renders and moves the cursor as ECharts' pointer does.
vi.mock('./detail/ChartCard', () => ({
  ChartCard: ({ chart, onHover }: { chart: { label: string }; onHover: (i: number | null) => void }) => {
    renders.charts += 1;
    return (
      <article
        aria-label={chart.label}
        onPointerMove={(e) => onHover(Number(e.currentTarget.dataset.at ?? 0))}
      >
        <h2>{chart.label}</h2>
      </article>
    );
  },
}));

const n = 61;
const STREAMS = {
  t: Array.from({ length: n }, (_, i) => i * 10),
  dist: Array.from({ length: n }, (_, i) => i * 50),
  alt: Array.from({ length: n }, (_, i) => 100 + (i % 20)),
  hr: Array.from({ length: n }, (_, i) => (i < 30 ? 120 : 160)),
  lat: Array.from({ length: n }, (_, i) => 40.4 + i * 0.0004),
  lon: Array.from({ length: n }, (_, i) => -3.7 + i * 0.0003),
};
const RUN = activityDetail({
  id: 7,
  has_gps: true,
  streams: { series: ['t', 'dist', 'alt', 'hr', 'lat', 'lon'], sample_count: n },
});

function mockApi() {
  server.use(
    route('get', '/api/activities', () =>
      json('/api/activities', 'get', { items: [activitySummary(RUN)], next_cursor: null }),
    ),
    route('get', '/api/activities/{activity_id}', ({ params }) =>
      params.activity_id === '7' ? json('/api/activities/{activity_id}', 'get', RUN) : undefined,
    ),
    route('get', '/api/activities/{activity_id}/streams', () =>
      json('/api/activities/{activity_id}/streams', 'get', STREAMS),
    ),
    route('get', '/api/activities/{activity_id}/context', () =>
      json('/api/activities/{activity_id}/context', 'get', activityContext()),
    ),
  );
}

describe('scrubbing an activity chart (OS-1013)', () => {
  it('moves the replay position without redrawing the page or the other charts', async () => {
    mockApi();
    let commits = 0;
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <Profiler id="app" onRender={() => (commits += 1)}>
        <App
          queryClient={queryClient}
          router={createMemoryRouter(routes, { initialEntries: ['/activities/7'] })}
        />
      </Profiler>,
    );
    const hr = await screen.findByRole('article', { name: 'Heart rate' });
    const seek = await screen.findByRole('slider', { name: 'Position in the activity' });
    const before = { ...renders, commits };
    const moves = 30;
    for (let i = 1; i <= moves; i++) {
      hr.dataset.at = String(i);
      act(() => {
        fireEvent.pointerMove(hr);
      });
    }
    // The replay's position followed the cursor: sample 30 is 5 minutes in.
    expect(seek).toHaveValue('300');
    expect(within(seek.parentElement ?? document.body).getByText('5:00')).toBeInTheDocument();
    // Before the store (OS-1013) each move redrew the page: 30 renders of it here, and 120 of the
    // charts without the React Compiler. Now only the seek bar commits, once a move.
    expect(renders.page - before.page).toBe(0);
    expect(renders.charts - before.charts).toBe(0);
    expect(commits - before.commits).toBe(moves);
  });
});
