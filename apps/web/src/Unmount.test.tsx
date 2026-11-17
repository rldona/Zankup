import { QueryClient } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { activityDetail, activitySummary } from './mocks/activityFixtures';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

const n = 61;
const RUN = activityDetail({
  id: 7,
  start_utc: '2026-11-07T08:00:00Z',
  duration_s: 1800,
  moving_s: 1750,
  distance_m: 6000,
  avg_hr: 150,
  has_gps: true,
  streams: { series: ['t', 'dist', 'hr'], sample_count: n },
});

describe('leaving an activity', () => {
  it("does not crash when the activity's charts go away", async () => {
    server.use(
      route('get', '/api/activities', () =>
        json('/api/activities', 'get', { items: [activitySummary(RUN)], next_cursor: null }),
      ),
      route('get', '/api/activities/{activity_id}', ({ params }) =>
        params.activity_id === '7' ? json('/api/activities/{activity_id}', 'get', RUN) : undefined,
      ),
      route('get', '/api/activities/{activity_id}/streams', () =>
        json('/api/activities/{activity_id}/streams', 'get', {
          t: Array.from({ length: n }, (_, i) => i * 10),
          dist: Array.from({ length: n }, (_, i) => i * 30),
          hr: Array.from({ length: n }, () => 140),
        }),
      ),
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const router = createMemoryRouter(routes, { initialEntries: ['/activities/7'] });
    render(<App queryClient={queryClient} router={router} />);
    await waitFor(() => expect(document.querySelector('.chartcard svg')).not.toBeNull());

    await act(() => router.navigate('/settings'));
    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.queryByText(/Unexpected Application Error/)).not.toBeInTheDocument();
    expect(errors.mock.calls.flat().join(' ')).not.toMatch(/reading 'off'/);
    errors.mockRestore();
  });
});
