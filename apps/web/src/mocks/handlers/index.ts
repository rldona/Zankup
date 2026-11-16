/**
 * Every mock handler of the API, by domain. A request with no handler fails the test (in Vitest) or
 * is warned about in the console (in the browser), so a missing mock shows at once.
 */
import { accountHandlers } from './account';
import { activitiesHandlers } from './activities';
import { authHandlers } from './auth';
import { healthHandlers } from './health';
import { progressHandlers } from './progress';

export const handlers = [
  ...authHandlers,
  ...activitiesHandlers,
  ...progressHandlers,
  ...healthHandlers,
  ...accountHandlers,
];
