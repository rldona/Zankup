import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { queryClient } from './api';
import { App } from './App';
import { locale } from './i18n';
import './index.css';
import { createRouter, prefetchScreens } from './router';
import { installTheme } from './theme';

installTheme();
document.documentElement.lang = locale;

const root = document.getElementById('root');
if (!root) throw new Error('Missing #root element');

/**
 * With VITE_API_MOCK on in development (`pnpm dev:mock`), the API is answered in the browser by the
 * mock handlers (src/mocks) before the app starts; otherwise it goes to the real one. Vite drops this
 * branch from a production build.
 */
async function mockApi() {
  if (import.meta.env.DEV && import.meta.env.VITE_API_MOCK === '1') {
    const { startMocks } = await import('./mocks/browser');
    await startMocks();
  }
}

/** Once the first screen is drawn and the browser is idle, the other screens' files are fetched. */
function whenIdle(task: () => void) {
  if ('requestIdleCallback' in window) window.requestIdleCallback(task, { timeout: 4000 });
  else setTimeout(task, 2000);
}

void mockApi().then(() => {
  createRoot(root).render(
    <StrictMode>
      <App
        queryClient={queryClient}
        router={createRouter()}
        offline={import.meta.env.VITE_API_MOCK !== '1'}
      />
    </StrictMode>,
  );
  whenIdle(prefetchScreens);
});
