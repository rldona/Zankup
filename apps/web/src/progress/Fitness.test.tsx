import { addDays, isoDay, rangePeriod, weekAt } from '@zankup/core';
import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from '../App';
import { server } from '../mocks/node';
import { json, route } from '../mocks/route';
import { routes } from '../router';

const today = isoDay(new Date());

/** A month of fitness, from 40 to 52, with a ride every third day. */
function fitnessDays(from: string) {
  const out = [];
  for (let day = from, i = 0; day <= today; day = addDays(day, 1), i++)
    out.push({ day, load: i % 3 === 0 ? 80 : 0, ctl: 40 + (12 * i) / 30, atl: 50, tsb: -5 });
  return out;
}

/** Weeks of effort: 250, 250, 268, 300 and 400 this week (above its range of 182-364). */
const WEEK_LOADS = [250, 250, 268, 300, 400];

/** A bucket of the API's volume, with its effort. */
const bucket = (start: string, load: number) => {
  const totals = { count: 1, distance_m: 10000, moving_s: 3600, ascent_m: 50, load };
  return { start, totals, by_sport: { ride: totals } };
};

const RIDE = {
  id: 9,
  sport: 'ride',
  name: 'Evening ride',
  load: 120,
  distance_m: 30000,
  moving_s: 5400,
  start: `${today}T17:00:00Z`,
};

/** The answers the cards are read from, and every request made (path and query). */
function mockApi() {
  const calls: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    calls.push(`${url.pathname}?${url.searchParams}`);
  });
  const query = (request: Request) => new URL(request.url).searchParams;
  server.use(
    route('get', '/api/stats/fitness', ({ request }) =>
      json('/api/stats/fitness', 'get', fitnessDays(query(request).get('from') ?? today)),
    ),
    route('get', '/api/stats/volume', ({ request }) => {
      if (query(request).get('unit') === 'week') {
        const count = WEEK_LOADS.length;
        return json(
          '/api/stats/volume',
          'get',
          WEEK_LOADS.map((load, i) => bucket(weekAt(count - 1 - i, today), load)),
        );
      }
      return json('/api/stats/volume', 'get', [bucket(today, 120)]);
    }),
    route('get', '/api/stats/estimates', () =>
      json('/api/stats/estimates', 'get', [
        {
          kind: 'race_5k',
          value: 1500,
          method: 'riegel',
          day: today,
          sources: [],
          history: [
            { day: addDays(today, -60), value: 1560 },
            { day: addDays(today, -20), value: 1530 },
            { day: addDays(today, -3), value: 1500 },
          ],
        },
        {
          kind: 'race_10k',
          value: 3120,
          method: 'riegel',
          day: today,
          sources: [],
          history: [{ day: addDays(today, -60), value: 3120 }],
        },
      ]),
    ),
    route('get', '/api/stats/calendar', ({ request }) => {
      const q = query(request);
      return json(
        '/api/stats/calendar',
        'get',
        (q.get('from') ?? '') <= today && today < (q.get('to') ?? '')
          ? [{ day: today, tsb: null, activities: [RIDE] }]
          : [],
      );
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

describe('Fitness, Relative effort and race predictions (OS-1003)', () => {
  afterEach(() => {
    server.events.removeAllListeners();
    localStorage.clear();
  });

  it('shows the three cards on Progress with their figures', async () => {
    mockApi();
    renderAt('/progress');
    const fitness = await screen.findByRole('link', { name: 'Fitness, Score 52' });
    expect(within(fitness).getByText('Last month')).toBeInTheDocument();
    expect(within(fitness).getByText('12')).toBeInTheDocument(); // ▲ 12 points in the month
    const effort = await screen.findByRole('link', { name: 'Relative Effort: 400' });
    expect(within(effort).getByText('Above')).toBeInTheDocument();
    expect(within(effort).getByText('Last week: 300')).toBeInTheDocument();
    const predictions = await screen.findByRole('link', { name: 'Performance predictions: 5 km 25:00' });
    expect(within(predictions).getByText('1min 0s')).toBeInTheDocument(); // 26:00 a month ago
  });

  it('opens Fitness from its card, with the change and the day’s activity, and remembers the range', async () => {
    const calls = mockApi();
    const router = renderAt('/progress');
    await userEvent.click(await screen.findByRole('link', { name: 'Fitness, Score 52' }));
    expect(router.state.location.pathname).toBe('/progress/fitness');
    expect(await screen.findByRole('heading', { level: 1, name: 'Fitness' })).toBeInTheDocument();
    expect(await screen.findByText('in the last 30 days')).toBeInTheDocument();
    expect(screen.getByText('+12 pt')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Evening ride/ })).toHaveAttribute(
      'href',
      '/activities/9',
    );

    await userEvent.click(screen.getByRole('tab', { name: '3M' }));
    expect(screen.getByRole('tab', { name: '3M' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByText('in the last 90 days')).toBeInTheDocument();
    expect(localStorage.getItem('zankup-fitness-range')).toBe('3m');
    const { from } = rangePeriod('3m', new Date());
    expect(calls).toContain(`/api/stats/fitness?from=${from}&to=${addDays(today, 1)}`);
  });

  it('picks another day with the keyboard', async () => {
    mockApi();
    renderAt('/progress/fitness');
    const chart = await screen.findByRole('slider', { name: 'Fitness' });
    chart.focus();
    await userEvent.keyboard('{Home}');
    await waitFor(() => expect(screen.getByText(/^from .+ to .+$/)).toBeInTheDocument());
    expect(screen.getByText('No change')).toBeInTheDocument();
  });

  it('shows the week of effort on its status’s panel, with its activities', async () => {
    mockApi();
    renderAt('/progress/effort');
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Above your weekly range' }),
    ).toBeInTheDocument();
    expect(screen.getByText('400', { selector: '.fxhuge' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: /Evening ride/ })).toHaveAttribute(
      'href',
      '/activities/9',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Previous week' }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Steady training' })).toBeInTheDocument();
  });

  it('shows the races today and one of them over a month', async () => {
    mockApi();
    renderAt('/progress/predictions');
    expect(
      await screen.findByText('Your current predictions against those of 30 days ago.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '10 km 52:00' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '5 km 25:00' }));
    expect(screen.getByRole('radio', { name: '1M' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: '5 km' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('Current predictions')).toBeInTheDocument();
    expect(screen.getByText('in the last 30 days')).toBeInTheDocument();
  });
});
