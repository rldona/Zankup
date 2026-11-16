import { addDays } from '@zankup/core';
import { QueryClient } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../App';
import { server } from '../mocks/node';
import { json, route } from '../mocks/route';
import { routes } from '../router';

const THIS_WEEK = '2026-11-09';

const totals = (moving_s: number, distance_m: number, ascent_m: number) => ({
  count: 1,
  moving_s,
  distance_m,
  ascent_m,
  load: 0,
});

/** A bucket per day or week of the range asked: this week busier than the others. */
function volume(url: URL) {
  const step = url.searchParams.get('unit') === 'day' ? 1 : 7;
  const to = url.searchParams.get('to') ?? THIS_WEEK;
  const buckets = [];
  for (let start = url.searchParams.get('from') ?? THIS_WEEK; start < to; start = addDays(start, step)) {
    const run = start === THIS_WEEK ? totals(15_420, 28_970, 1421) : totals(3600, 10_000, 100);
    buckets.push({ start, totals: run, by_sport: { run } });
  }
  return buckets;
}

const RUN = {
  id: 7,
  name: 'Morning run',
  sport: 'run',
  load: 50,
  distance_m: 10_000,
  moving_s: 3000,
  ascent_m: 80,
  start: '2026-11-10T07:00:00Z',
};

function calendar(url: URL) {
  const days = [];
  const to = url.searchParams.get('to') ?? THIS_WEEK;
  for (let day = url.searchParams.get('from') ?? THIS_WEEK; day < to; day = addDays(day, 1))
    days.push({
      day,
      tsb: null,
      activities:
        day === '2026-11-10'
          ? [RUN]
          : day === '2026-11-04'
            ? [
                { ...RUN, id: 8, name: 'Easy ride', sport: 'ride' },
                { ...RUN, id: 9, name: 'Gym', sport: 'strength', distance_m: null },
              ]
            : [],
    });
  return days;
}

function mockApi() {
  server.use(
    route('get', '/api/stats/volume', ({ request }) =>
      json('/api/stats/volume', 'get', volume(new URL(request.url))),
    ),
    route('get', '/api/stats/calendar', ({ request }) =>
      json('/api/stats/calendar', 'get', calendar(new URL(request.url))),
    ),
    route('get', '/api/stats/streak', () =>
      json('/api/stats/streak', 'get', {
        weeks: 5,
        activities: 12,
        since: '2026-10-12',
        first_activity: '2026-06-03',
      }),
    ),
  );
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('Volume, Series and the Training log (OS-1002)', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-11T12:00:00'));
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    mockApi();
  });
  afterEach(() => vi.useRealTimers());

  it("shows this week's volume on the card and opens the detail page", async () => {
    const router = renderAt('/progress');
    const card = (await screen.findByText('4h 17min')).closest('article') as HTMLElement;
    expect(within(card).getByRole('heading', { name: 'This week' })).toBeInTheDocument();
    expect(within(card).getByText('28.97 km')).toBeInTheDocument();
    expect(within(card).getByText('Last 12 weeks')).toBeInTheDocument();

    await userEvent.click(within(card).getByRole('button', { name: /Run/ }));
    await userEvent.click(within(card).getByRole('link', { name: 'See your progress in more detail' }));
    expect(router.state.location.pathname).toBe('/progress/volume');
    expect(router.state.location.search).toBe('?sport=run');

    // Run is measured by distance: 11 weeks of 10 km and this one's 28.97 km, against 12 weeks of 10 km.
    expect(await screen.findByRole('heading', { name: 'Total distance' })).toBeInTheDocument();
    expect(await screen.findByText('139.0 km')).toBeInTheDocument();
    expect(screen.getByText('▲ 16 %')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Nice work! You logged 139.0 km in the last three months, 16 % more than the period before (120.0 km).',
      ),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '7D' }));
    expect(await screen.findByText('vs the 7 days before')).toBeInTheDocument();
  });

  it('opens the month of the streak from the Series card', async () => {
    renderAt('/progress');
    await userEvent.click(await screen.findByRole('button', { name: 'Streaks: 5 weeks' }));
    const month = await screen.findByRole('dialog', { name: 'Streaks' });
    expect(within(month).getByText('November 2026')).toBeInTheDocument();
    expect(within(month).getByText('5 weeks')).toBeInTheDocument();
    expect(within(month).getByText('Streak activities').nextElementSibling).toHaveTextContent('12');
    expect(await within(month).findByRole('link', { name: '10: Morning run' })).toHaveAttribute(
      'href',
      '/activities/7',
    );
    expect(within(month).getByRole('button', { name: 'Next month' })).toBeDisabled();
  });

  it('opens the Training log from its card, with a bubble per day', async () => {
    const router = renderAt('/progress');
    const card = await screen.findByRole('link', { name: /^Training log: This week/ });
    await userEvent.click(card);
    expect(router.state.location.pathname).toBe('/progress/log');
    expect(await screen.findByRole('heading', { name: 'Training log' })).toBeInTheDocument();
    expect(screen.getByText('9 - 15 NOV')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /^2026-11-10: 0:50/ })).toBeInTheDocument();

    // A day with two activities lists them.
    await userEvent.click(await screen.findByRole('button', { name: /^2026-11-04: 1:40/ }));
    const day = await screen.findByRole('dialog', { name: '2 activities' });
    expect(within(day).getByRole('link', { name: /Easy ride/ })).toHaveAttribute('href', '/activities/8');
  });
});
