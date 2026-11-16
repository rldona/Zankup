# apps/web

The PWA for desktop and mobile: React + Vite + TypeScript, deployed on Vercel. The design comes from [`docs/prototypes/`](../../docs/prototypes/index.html); the app skeleton arrives in OS-201.

From the repository root (pnpm comes with Node through corepack: `corepack enable`, or prefix commands with `corepack`):

```bash
pnpm install
pnpm --filter @zankup/web dev           # http://localhost:5173, /api proxied to the local backend on :8000
pnpm --filter @zankup/web dev:mock      # the same, with a mock API in the browser: no backend, no sign-in
pnpm --filter @zankup/web test          # vitest + Testing Library
pnpm --filter @zankup/web lint          # eslint (typescript-eslint strict, React hooks)
pnpm --filter @zankup/web format:check  # prettier
pnpm --filter @zankup/web build         # type check + production build in dist/
```

TypeScript stays on 6.0 until typescript-eslint supports 7.

## The mock API (MSW)

`pnpm --filter @zankup/web dev:mock` starts the web with [MSW](https://mswjs.io/) answering every `/api/*`
call in the browser, so any screen can be worked on and seen at once, with no backend, no session and
nobody's real data. Without the switch (`VITE_API_MOCK=1`), `dev` goes to the real API as before; a
production build has none of it (the worker and the handlers are only loaded in mock mode).

- **The data:** a made-up athlete (`src/mocks/db.ts`) with three years of runs, rides, strength and
  walks dated from today, and everything derived from them: streams, laps and splits, the activity's
  context, stats, Progress, goals, estimates, health and sleep, weight, the Coach's texts, settings.
  It comes from a fixed seed, so it is the same on every start; what you change (edits, gear, goals,
  weighings, settings) lasts until you reload.
- **Signed in from the start.** Signing out works; to sign in again use any e-mail, password `demo`
  and code `123456`, or "Sign in as owner (dev)".
- **The handlers** (`src/mocks/handlers/*`, by domain) are typed by the API's OpenAPI: `json(path,
method, body)` checks every answer against `packages/core/src/api/schema.ts`, so when the backend
  changes a field, the typecheck shows which mock is out of date (`pnpm generate:api` first).
- **A call with no handler** is warned about in the console (in the browser) and fails the test
  (in Vitest): add it to its domain's file.

### In the tests

Vitest uses the same handlers (`src/mocks/node.ts`, started in `src/test/setup.ts`), and the data is
reset after every test. A test that needs an exact answer overrides just that call:

```ts
import { server } from './mocks/node';
import { json, route } from './mocks/route';

server.use(route('get', '/api/week', () => json('/api/week', 'get', week)));
```

To check what was sent, listen to MSW's request events (`server.events.on('request:start', …)`).
Don't stub `fetch` in a test: it bypasses the mock API.
