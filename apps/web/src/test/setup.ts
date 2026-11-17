import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { resetDb } from '../mocks/db';
import { server } from '../mocks/node';

// The mock API (src/mocks) answers every test's requests; a test changes an answer with
// `server.use(...)`. A request with no handler fails the test, so a missing mock shows at once.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  resetDb();
});
afterAll(() => server.close());

// A busy machine (CI, or the dev server and a browser beside the suite) can take more than Testing
// Library's 1 s to draw a page with its charts: `findBy…` and `waitFor` wait up to 10 s, so a slow
// render is not a failure. A test that never finds what it looks for still fails.
configure({ asyncUtilTimeout: 10_000 });

// Vitest runs without globals, so Testing Library cannot register its automatic cleanup.
afterEach(cleanup);
