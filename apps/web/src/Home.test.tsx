import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';
import { db } from './mocks/db';
import { server } from './mocks/node';
import { json, route, type Res } from './mocks/route';
import { routes } from './router';

type Activity = Res<'/api/activities', 'get'>['items'][number];
const DAY = 24 * 60 * 60 * 1000;
const at = (daysAgo: number) => new Date(Date.now() - daysAgo * DAY).toISOString();
const isoDay = (daysAgo: number) => {
  const d = new Date(Date.now() - daysAgo * DAY);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
/** The machine's UTC offset on that date, as the API gives it: "+01:00". */
const offset = (date: Date) => {
  const minutes = -date.getTimezoneOffset();
  const abs = Math.abs(minutes);
  return `${minutes < 0 ? '-' : '+'}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
};
const activity = (id: number, name: string, daysAgo: number): Activity => ({
  id,
  name,
  sport: 'run',
  sub_sport: null,
  source: 'garmin',
  start_utc: at(daysAgo),
  // The machine's own offset, as "today" is: with none, the day would be UTC's (wrong after midnight).
  timezone: offset(new Date(Date.now() - daysAgo * DAY)),
  duration_s: 3600,
  moving_s: 3500,
  distance_m: 10000,
  ascent_m: 80,
  avg_hr: 150,
  avg_power: null,
  has_gps: false,
  route: null,
  photo: null,
  photos: 0,
});

/** The feed, the day's steps, an FTP of 280 W at 70 kg and a fresh form; the rest is the demo athlete's. */
function useFixtures(items: Activity[]) {
  db.user.weight_kg = 70;
  server.use(
    route('get', '/api/activities', () => json('/api/activities', 'get', { items, next_cursor: null })),
    route('get', '/api/wellness/days', ({ request }) =>
      // As the API: health days stop before `end` (exclusive), so asking up to today leaves today out.
      (new URL(request.url).searchParams.get('end') ?? '') <= isoDay(0)
        ? json('/api/wellness/days', 'get', [])
        : json('/api/wellness/days', 'get', [
            {
              day: isoDay(0),
              sleep: null,
              resting_hr: null,
              hrv: null,
              spo2: null,
              respiration: null,
              steps: { value: 4321, source: 'garmin', others: [] },
              tsb: null,
              readiness: null,
            },
          ]),
    ),
    route('get', '/api/stats/estimates', () =>
      json('/api/stats/estimates', 'get', [
        { kind: 'ftp', value: 280, method: 'curve', day: isoDay(0), sources: [], history: [] },
      ]),
    ),
    route('get', '/api/stats/fitness', () =>
      json('/api/stats/fitness', 'get', [{ day: isoDay(0), load: 0, ctl: 49, atl: 36, tsb: 13 }]),
    ),
  );
}

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<App queryClient={queryClient} router={createMemoryRouter(routes, { initialEntries: ['/'] })} />);
}

describe('Home (OS-904)', () => {
  it("shows today's activities, the day's health, the last seven days and the metrics", async () => {
    useFixtures([activity(2, 'Evening run', 0), activity(1, 'Morning run', 0)]);
    renderHome();
    expect(await screen.findByRole('heading', { name: 'Today' })).toBeInTheDocument();
    // Anchored: the week's dots are links named "<day>: <activity>" too, and a fixed fixture's run can
    // fall in this week depending on today's date.
    expect(screen.getByRole('link', { name: /^Evening run/ })).toHaveAttribute('href', '/activities/2');
    expect(screen.getByRole('link', { name: /^Morning run/ })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Last 7 days' })).toBeInTheDocument();
    expect(screen.getByText('Run · 2')).toBeInTheDocument();
    expect(await screen.findByText('4 W/kg')).toBeInTheDocument(); // 280 W at 70 kg
    expect(await screen.findByText('Fresh')).toBeInTheDocument();
  });

  it("draws This week as the app's: the sport, a dot for a second one, the day's number, the usual week", async () => {
    const week = (moving_s: number): Res<'/api/week', 'get'>['this_week'] => ({
      week_start: isoDay(7),
      count: 3,
      distance_m: 30000,
      moving_s,
      ascent_m: 200,
    });
    useFixtures([]);
    server.use(
      route('get', '/api/week', () =>
        json('/api/week', 'get', {
          today: isoDay(0),
          days: [
            {
              date: isoDay(1),
              activities: [
                { id: 7, sport: 'run', name: 'Morning run' },
                { id: 8, sport: 'ride', name: 'Hill ride' },
              ],
            },
            { date: isoDay(0), activities: [] },
            { date: isoDay(-1), activities: [] },
          ],
          this_week: week(5400),
          previous: [week(10800), week(10800)],
          streak_weeks: 0,
          records: [],
        }),
      ),
    );
    renderHome();
    const card = (await screen.findByRole('heading', { name: 'This week' })).closest('section');
    if (!card) throw new Error('no week card');
    // Yesterday: the run's icon in its circle, a dot for the ride, and both named.
    const done = within(card).getByRole('link', { name: /Morning run, Hill ride/ });
    expect(done).toHaveAttribute('href', '/activities/7');
    expect(done.querySelector('.second')).toHaveStyle({ '--sport': 'var(--ride)' });
    // Today and tomorrow: the day's number, today ringed and tomorrow faded.
    const today = card.querySelector('li.today');
    expect(today).toHaveTextContent(String(new Date().getDate()));
    expect(today).toHaveTextContent('Today');
    expect(card.querySelector('li.future .dot')).toHaveTextContent(
      String(new Date(Date.now() + DAY).getDate()),
    );
    // No streak to show; half a usual week of training time.
    expect(within(card).queryByText(/in a row/)).not.toBeInTheDocument();
    expect(within(card).getByRole('progressbar', { name: 'Training time' })).toHaveAttribute(
      'aria-valuenow',
      '50',
    );
    expect(within(card).getByText(/50\s?%/)).toBeInTheDocument();
  });

  it("pages through the day's activities with dots below when there are several", async () => {
    useFixtures([activity(2, 'Evening run', 0), activity(1, 'Morning run', 0)]);
    renderHome();
    const dots = await screen.findAllByRole('button', { name: /^Activity \d of 2$/ });
    expect(dots).toHaveLength(2);
    expect(dots[0]).toHaveAttribute('aria-current', 'true');
    expect(dots[1]).not.toHaveAttribute('aria-current');
    await userEvent.click(dots[1] as HTMLElement);
    expect(dots[1]).toHaveAttribute('aria-current', 'true');
    expect(dots[0]).not.toHaveAttribute('aria-current');
  });

  it('leaves out a last day older than yesterday, but keeps the health card', async () => {
    useFixtures([activity(1, 'Old run', 3)]);
    renderHome();
    // The health card's steps row (the metrics' steps tile shows the same number).
    await waitFor(() => expect(screen.getAllByText('4,321').some((el) => el.closest('.hrow'))).toBe(true));
    expect(screen.queryByRole('heading', { name: 'Today' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Yesterday' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Old run/ })).not.toBeInTheDocument();
  });
});
