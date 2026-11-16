/**
 * The mock API in the browser (`pnpm dev:mock`): MSW's service worker answers /api/* with the demo
 * data, so every screen works locally without a backend, a session or anyone's data. Only imported
 * when VITE_API_MOCK is on in development, so none of it reaches a production build.
 */
import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

export async function startMocks() {
  const worker = setupWorker(...handlers);
  await worker.start({
    // A request with no mock is warned about in the console and goes to the network.
    onUnhandledFrame: 'warn',
    quiet: true,
  });
  // A hard reload (Cmd+Shift+R) skips every service worker, so the page's calls would go to the
  // backend proxy instead of the mocks: reload once, normally, so the worker controls the page.
  if (!navigator.serviceWorker.controller && !sessionStorage.getItem('zankup.mockReload')) {
    sessionStorage.setItem('zankup.mockReload', '1');
    location.reload();
    await new Promise(() => undefined); // nothing else starts before the reload
  }
  sessionStorage.removeItem('zankup.mockReload');
}
