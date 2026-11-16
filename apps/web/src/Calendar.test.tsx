import { QueryClient } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

const activity = (id: number, sport: string, distance_m: number | null, moving_s: number) => ({
  id,
  sport,
  name: null,
  load: 40,
  distance_m,
  moving_s,
  start: '2026-11-03T08:00:00Z',
});

/** November 2026 from Monday 26 October: five weeks, the second with a run and a ride. */
function month() {
  const days = [];
  const start = new Date(2026, 9, 26, 12);
  for (let i = 0; i < 42; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const activities =
      day === '2026-11-03'
        ? [activity(1, 'run', 10000, 3000)]
        : day === '2026-11-05'
          ? [activity(2, 'ride', 30000, 3600), activity(3, 'strength', null, 1800)]
          : [];
    days.push({ day, tsb: null, activities });
  }
  return days;
}

describe('Training calendar', () => {
  it("puts each week's distance first, its time and activities under it, and a bar a day", async () => {
    server.use(route('get', '/api/stats/calendar', () => json('/api/stats/calendar', 'get', month())));
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <App
        queryClient={queryClient}
        router={createMemoryRouter(routes, { initialEntries: ['/stats/calendar?month=2026-11'] })}
      />,
    );

    const bars = await screen.findAllByRole('img', { name: 'Moving time each day' });
    expect(bars).toHaveLength(6);
    const [empty, full] = bars as [HTMLElement, HTMLElement];
    const week = full.closest('.calweek') as HTMLElement;
    expect(week).toHaveTextContent(/40[.,]00 km/);
    expect(week).toHaveTextContent('2 h 20 min · 3 activities');
    // Thursday stacks the ride and the strength session; the empty days have no bar.
    const days = within(full).getAllByTitle(/·/);
    expect(days[3]?.querySelectorAll('i')).toHaveLength(2);
    expect(days[0]?.querySelectorAll('i')).toHaveLength(0);
    // A week without activities says so with a dash.
    expect(empty.closest('.calweek')).toHaveClass('empty');
    expect(empty.closest('.calweek')).toHaveTextContent('—');
  });
});
