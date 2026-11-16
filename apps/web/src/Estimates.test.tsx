import { QueryClient } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';
import { server } from './mocks/node';
import { json, route } from './mocks/route';
import { routes } from './router';

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<App queryClient={queryClient} router={router} />);
  return router;
}

describe('Estimates (OS-913)', () => {
  it("has its own page, which You's old Estimates tab leads to", async () => {
    server.use(
      route('get', '/api/stats/estimates', () =>
        json('/api/stats/estimates', 'get', [
          { kind: 'ftp', value: 192, method: 'ftp_20min', day: '2026-11-08', sources: [], history: [] },
          { kind: 'race_10k', value: 3211, method: 'riegel', day: '2026-11-08', sources: [], history: [] },
        ]),
      ),
    );
    const router = renderAt('/profile?tab=estimates');
    await waitFor(() => expect(router.state.location.pathname).toBe('/estimates'));
    expect(await screen.findByRole('heading', { level: 1, name: 'Estimates' })).toBeInTheDocument();
    expect(await screen.findByText('192 W')).toBeInTheDocument();
    expect(screen.getByText('53:31')).toBeInTheDocument(); // the 10 km prediction
  });

  it('says how much each estimate moved in three months, and opens its history', async () => {
    const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString().slice(0, 10);
    server.use(
      route('get', '/api/stats/estimates', () =>
        json('/api/stats/estimates', 'get', [
          {
            kind: 'ftp',
            value: 192,
            method: 'ftp_20min',
            day: ago(10),
            sources: [{ id: 7, name: null, sport: 'ride', day: ago(10) }],
            // 183 W before the three months, 192 W now: +9 W.
            history: [
              { day: ago(200), value: 170 },
              { day: ago(120), value: 183 },
              { day: ago(40), value: 188 },
              { day: ago(10), value: 192 },
            ],
          },
          {
            kind: 'race_10k',
            value: 3211,
            method: 'riegel',
            day: ago(10),
            sources: [],
            // Faster by 1:14 in the three months: good news, with a minus.
            history: [
              { day: ago(150), value: 3285 },
              { day: ago(10), value: 3211 },
            ],
          },
        ]),
      ),
    );
    renderAt('/estimates');
    const ftp = await screen.findByRole('button', { name: /FTP/ });
    expect(ftp).toHaveTextContent('+9 W in 3 months');
    expect(within(ftp).getByText('+9 W in 3 months')).toHaveClass('delta', 'good');
    expect(screen.getByRole('button', { name: /10 km/ })).toHaveTextContent('−1:14 in 3 months');
    // The card opens its history, with the activities behind it.
    await userEvent.click(ftp);
    expect(ftp).toHaveAttribute('aria-expanded', 'true');
    const detail = screen.getByRole('region', { name: 'FTP' });
    expect(within(detail).getByRole('link')).toHaveAttribute('href', '/activities/7');
  });

  it("shows the demo athlete's estimates from the mock API", async () => {
    renderAt('/estimates');
    expect(await screen.findByRole('button', { name: /FTP/ })).toHaveTextContent(/\d+ W/);
    expect(screen.getByRole('button', { name: /10 km/ })).toHaveTextContent(/\d+:\d\d/);
  });
});
