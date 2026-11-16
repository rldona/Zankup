/** The mock API in the tests (Vitest, jsdom): the same handlers as in the browser. */
import { setupServer } from 'msw/node';

import { handlers } from './handlers';

export const server = setupServer(...handlers);
