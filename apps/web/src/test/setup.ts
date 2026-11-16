import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
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

// Vitest runs without globals, so Testing Library cannot register its automatic cleanup.
afterEach(cleanup);
