# Zankup roadmap

Development plan by phases, with small tickets and deliverables that are usable from the end of each phase.

References: [architecture.md](architecture.md) · [findings.md](findings.md) · [prototypes](prototypes/index.html)

## Overview

| Phase | Goal | Usable deliverable | Milestone |
|---|---|---|---|
| **0 · Foundations** | Repository, CI and environments ready | Empty deployment on Railway and Vercel | — |
| **1 · Backend on Railway** | Sync and store activities in the cloud | API with your activities, synced automatically every 15 min | **M1: "my data lives in Zankup"** |
| **2 · PWA MVP** | See activities on desktop and mobile | Installable PWA with feed, detail with map, search and Sync button, in English and Spanish | **M2: "I use Zankup instead of Strava to see my activities"** |
| **3 · Advanced metrics** | The "Premium" metrics, built by us | Complete stats screen | **M3: "I know how fit I am"** |
| **4 · Notifications, history and privacy** | Notifications, the full history and safe sharing | Push, Strava ZIP import, privacy zones | **M4: "Zankup replaces Strava"** |
| **5 · Android app (React Native)** | Expo app on the shared code, background sync and FCM (stack confirmed by the OS-501 proof of concept at the start of phase 2) | App on the Play Store (internal testing) | **M5: Android app** |
| **6 · Multi-source health** | Sleep, HRV and daily data from Garmin and Google Health (Fitbit Air), without duplicates | Health dashboard | **M6: "I see my recovery, wherever it comes from"** |
| **7 · AI assistant** | Summaries, analysis and natural-language questions with a small LLM (Claude Haiku 5.5) | Per-activity summary, weekly report and "Coach" chat | **M7: "I ask Zankup how I'm doing"** |
| **8 · Several people, one Zankup** | More than one person on an instance, each with their own data and keys, and a first run that guides them | Welcome, sample-data screens and per-user keys | **M8: "someone else starts using my Zankup"** |
| **9 · Web and app in step** | The web catches up with the app, adapted to a large screen, and the app takes what it lacks from the web | Home, activity charts, Goals, Stats, You and Settings on the web as on the app | **M9: "the web is as good as the app"** |
| **10 · The web catches up again** | What the app gained after M9 (Progress, the activity laid out as Strava's, weight, Stats by its period) reaches the web, adapted to a large screen | Progress, the new activity page and weight on the web | **M10: "the web has Progress too"** |
| **11 · Native Android app** | The Android app rewritten from scratch in Kotlin and Compose, on the same API and the React Native app's screen structure; the React Native app kept until the cutover | Zankup Next beside the current app, then replacing it as `com.zankup.app` | **M11: "Zankup is native on my phone"** |

**Size estimate:** S ≈ half a day · M ≈ 1–2 days · L ≈ 3–5 days. Rough totals: M1 ≈ 20–25 working days (phases 0 and 1), M2 ≈ another 30–35.

**Tracking:** every ticket is a GitHub issue with `phase-N`, `size-S/M/L` and `type` labels and a milestone per phase; dependencies are linked in each issue. This file remains the plan; the issues are the work.

**Product model:**
- **Open source and self-hosted.** Each person deploys their own instance (Railway + Vercel, or Docker on their own server) and uses **their own keys**: Garmin Personal API, Google OAuth, Google Health and, optionally, Anthropic. That keeps every instance within the Garmin Personal API's personal-use terms, and nobody else's health data lives on our servers.
- **Licences:** `packages/metrics` (the metrics engine) under **MIT**, so anyone can reuse it; everything else under **AGPL-3.0**, so hosted forks stay open.
- **No payments for now.** Nothing in the design should close the door to a hosted version later: every table carries `user_id` from the start, even though each deployment has a single user.
- **Brand applied.** The product is **Zankup**; the name, mark, wordmark and colours are in the [brand guide](brand/README.md). The reference instance runs on **zankup.com** (PWA and API proxy on Vercel) and **api.zankup.com** (API on Railway, for the Android app). A self-hosted instance can keep its `*.vercel.app` and `*.up.railway.app` URLs or use its own domain.

**Working rules:**
- Related tickets are built together: one block = one branch = one PR with tests (decided 8 Nov 2026,
  to have fewer PRs and merges; each merge to `main` is a paid production build on Vercel).
- Commits, PRs, branches, docs and code comments are written in English.
- Every phase ends with something deployed and usable.
- Anything not critical for the phase milestone moves to the next phase.

---

## Already done (discovery phase)

| Ticket | What | Result |
|---|---|---|
| OS-D01 | Research data sources (Strava, Garmin, phone, USB, intermediaries) | [findings.md](findings.md) |
| OS-D02 | Sync prototype with the Garmin Personal API | `backend/garmin_sync.py` (was `sync/`), 72 activities downloaded |
| OS-D03 | Validate that the API's FIT is the complete original | Byte-for-byte identical to "Export File" |
| OS-D04 | Architecture (Railway + Vercel + Android) | [architecture.md](architecture.md) |
| OS-D05 | Desktop and mobile prototypes with real data (full history: 1,819 activities since 2011) | [prototypes/](prototypes/index.html) |

---

## Decisions before starting

These are owner decisions, tracked as issues with the `decision` label.

- **OS-D07 · Choose the licence and repository visibility** (S) · **decided**
  - MIT for `packages/metrics`, AGPL-3.0 for the rest of the repository, public repository (see "Product model" above). Applied in OS-001.
  - *Done when:* `LICENSE` (AGPL-3.0) is at the root, `packages/metrics/LICENSE` (MIT) is in place and the repository is public.
- **OS-D08 · Create the service accounts for your own instance** (S)
  - Railway, Vercel, Resend, Google Cloud and Anthropic (optional). Expected cost at the start: about $5–10/month. The same list becomes the self-hosting guide (OS-224).
  - *Done when:* every account exists and its keys are stored only in Railway's variables and the local `.env`.

The production domain (OS-D06) is done: `zankup.com` → Vercel (`www` redirects to it), `api.zankup.com` → Railway. DNS is at the registrar (GoDaddy); the records are listed in `docs/architecture.md`.

## Phase 0 · Foundations

**Goal:** any change reaches production with a `git push`.

- **OS-001 · Repository and structure** (S)
  - pnpm-workspaces monorepo: `backend/`, `apps/web/`, `apps/mobile/` (empty until phase 5), `packages/core/`, `packages/metrics/`, `docs/` and `tools/`. `README`, licences (OS-D07: AGPL-3.0 at the root, MIT in `packages/metrics/`) and `.gitignore` (including `.env`, `data/`, `strava/`, `docs/prototypes/data.js` and the sample files with personal data).
  - Migrate what already exists: `sync/garmin_sync.py` → `backend/` (as the seed of OS-103), `tools/build_proto_data.py` stays in `tools/`, prototypes stay in `docs/prototypes/`. Translate code comments and messages to English.
  - *Done when:* the structure is in place, nothing personal or secret is tracked, and the existing scripts still run from their new location.
- **OS-002 · Backend tooling** (S) · depends on OS-001
  - Python 3.12+, `uv`, `ruff`, `mypy` and `pytest`, with `pre-commit`.
  - *Done when:* `uv run pytest` and `ruff check` pass locally.
- **OS-003 · Web tooling** (S) · depends on OS-001
  - Vite + React + TypeScript, `eslint`, `prettier`, `vitest` and pnpm.
  - *Done when:* `pnpm dev`, `pnpm test` and `pnpm build` work.
- **OS-008 · Shared package `packages/core`** (S) · depends on OS-001 and OS-003
  - Strict TypeScript. API client generation from OpenAPI, formatting and domain utilities (the prototype's), design tokens in JSON and the `en`/`es` i18n catalogues. Tests with vitest. The PWA uses it from day one.
- **OS-009 · Local development environment** (S) · depends on OS-002 and OS-003
  - `docker compose` with Postgres (and the API in watch mode), `.env.example` with every variable, and `make dev` / `make test` (or `just`) to start everything with one command. Short "Getting started" section in the README.
  - *Done when:* a fresh clone runs the API, the database and the web app locally in under 10 minutes following the README.
- **OS-010 · Anonymised test fixtures** (M) · depends on OS-001
  - A `tools/anonymise_fit` script that produces test FITs from real ones: coordinates shifted to a neutral location, user profile (weight, height, sex, resting HR) removed, serial numbers and IDs replaced, timing and metrics kept. Fixtures for run, strength, indoor ride with power and an old TCX/GPX.
  - Recorded HTTP responses (VCR cassettes) for the Garmin and Google APIs are scrubbed of tokens, keys, emails and real IDs before being committed, with a CI check that fails if a cassette contains an `Authorization` header or coordinates outside the neutral area.
  - *Done when:* every test uses only anonymised fixtures and the CI check is active.
- **OS-004 · CI on GitHub Actions** (S) · depends on OS-002 and OS-003
  - Lint, tests and build for the backend and the web on every PR.
  - *Done when:* a PR shows green checks.
- **OS-005 · Railway project** (S) · depends on OS-002
  - API service with a Dockerfile, Postgres, a `/data` volume and environment variables (`GARMIN_PERSONAL_API_KEY`, `JWT_SECRET`, `DATABASE_URL`). Automatic deploys from `main`.
  - *Done when:* `GET /api/health` responds on the Railway URL.
- **OS-006 · Vercel project** (S) · depends on OS-003 and OS-005
  - Build of `apps/web/`, `vercel.json` with `rewrites /api/* → Railway` and per-PR previews.
  - *Done when:* the empty web app calls `/api/health` through the proxy.
- **OS-007 · Rotate the Garmin key** (S)
  - Delete the key that was pasted into the chat, create a new one and store it only on Railway and in the local `.env`.
  - *Done when:* the old key returns 401.

## Phase 1 · Backend on Railway → **M1**

**Goal:** activities sync on their own and are stored, parsed and served by the API.

- **OS-101 · `zankup` package and configuration** (S) · depends on OS-002
  - Structure `zankup/{api,sync,fit,db,core}`. Configuration with `pydantic-settings` and structured logging.
  - The metric algorithms live in **`packages/metrics`** (Python package `zankup-metrics`, MIT): pure functions over normalised streams and summaries, with no dependency on the database, the API or the network, so other projects can use them. The backend imports it.
- **OS-102 · Database schema and migrations** (M) · depends on OS-101
  - SQLAlchemy + Alembic. Tables `activities`, `laps`, `streams`, `sync_runs`, `settings`, `users` and `devices` (model in [architecture.md](architecture.md)).
  - Every user-owned table has a `user_id` column and every query filters by it, even with a single user per instance. It costs almost nothing now and avoids a painful migration if a hosted version ever exists.
  - *Done when:* `alembic upgrade head` creates the schema locally and on Railway.
- **OS-103 · Garmin Personal API client** (M) · depends on OS-101 and OS-010
  - Turn `garmin_sync.py` into a module: cached token (in the database, not a file), paginated list, ZIP download and FIT extraction, retries on 429.
  - *Done when:* tests with recorded responses (VCR, scrubbed as in OS-010) cover the token, the list and the download.
- **OS-104 · Original FIT store** (S) · depends on OS-101
  - Immutable `/data/raw/{activityId}.fit`, with its `sha256` stored. An interface that allows switching to object storage later.
- **OS-105 · FIT parser → normalised model** (M) · depends on OS-102 and OS-010
  - Session, laps, 1 Hz streams (lat, lon, distance, altitude, HR, cadence ×2, speed, power, temperature) and device. Semicircles → degrees.
  - *Done when:* tests with the anonymised run, strength and ROUVY fixtures (OS-010) match Garmin's numbers.
- **OS-106 · Ingestion: store activity, laps and streams** (M) · depends on OS-104 and OS-105
  - Idempotent by `activityId`. Compressed streams. Simplified route for thumbnails and `bbox`.
- **OS-107 · Basic metrics on import** (M) · depends on OS-106
  - Smoothed pace and speed, per-km splits and best efforts (400 m … marathon), validated against the Strava numbers in [findings.md](findings.md). GPS speed spikes are filtered out before computing records.
- **OS-108 · Sync orchestrator** (M) · depends on OS-103, OS-106 and OS-115
  - `sync_run` with status and progress, incremental since the last sync, and a lock so two never run at once.
  - *Done when:* `python -m zankup.sync` imports what is new and a second run does nothing.
- **OS-109 · Cron on Railway** (S) · depends on OS-108
  - Every 15 min. Done as a scheduler thread next to the worker (a Railway Cron Job cannot reach the API's volume), queuing one sync job per time slot.
  - *Done when:* a new activity from the watch shows up in the database with no intervention.
- **OS-110 · Authentication** (L) · depends on OS-102
  - One user per instance. Sign-in with password + TOTP; passkeys come with OS-405. `httpOnly` session cookie for the web and access + refresh tokens for Android. Rate limiting.
  - *Done when:* every route except `/health` and `/auth/*` returns 401 without a session.
- **OS-111 · Activities API** (M) · depends on OS-107 and OS-110
  - `GET /api/activities` (cursor pagination), `GET /api/activities/{id}`, `/streams` (gzip) and `/fit`. OpenAPI published.
- **OS-112 · Sync API** (S) · depends on OS-108 and OS-110
  - `POST /api/sync` and `GET /api/sync/{id}` with progress.
- **OS-113 · Initial Garmin history load** (S) · depends on OS-108
  - `sync --since YYYY-MM-DD` command, with pauses, to bring in everything on Garmin Connect.
- **OS-114 · Observability and backups** (S) · depends on OS-005
  - Structured logs in Railway, an email alert (Resend) if sync fails 3 times in a row, and daily backups of Postgres and the volume. Error tracking with Sentry is in the backlog.
  - *Done when:* a backup has been restored into a fresh database and volume and the app works with it (restore tested, not just backup).
- **OS-115 · Job queue and worker** (M) · depends on OS-102
  - `jobs` table in Postgres (with `SKIP LOCKED`), retries with backoff and a worker that is the same container started in worker mode on Railway. Sync and ingestion become jobs.
  - *Done when:* importing 70 activities does not block the API and a failure retries on its own.

**M1 is done when** activities from the watch appear on their own in the Railway API, with basic metrics, in under 15 minutes.

## Phase 2 · PWA MVP → **M2**

**Goal:** use Zankup every day on desktop and mobile.

**Status (9 Nov 2026):** done, built in four blocks (detail; settings, language and profile; sync and
offline; accounts, search, strength, self-hosting and quality). Left for later: the "Deploy on
Railway" template button (it is published from a Railway account), the Stats cards that need the
metrics engine (phase 3), gear, and importing and exporting (phase 4).

- **OS-501 · React Native performance proof of concept** (M) · depends on OS-008 and OS-111
  - **Runs first in this phase**, right after M1, so the React Native vs Kotlin decision is made before the PWA and `packages/core` take shape. The rest of the Android app stays in phase 5.
  - In 2–3 days, only the activity detail in Expo: MapLibre map, profile drawn with Skia, cursor synced with Reanimated and section selection, with real data from the API.
  - *Done when:* on a mid-range phone cold start is under 1.5 s, dragging runs at 60 fps and memory is reasonable, and the decision (React Native or Kotlin) is written down in `docs/architecture.md`. If Kotlin wins, `packages/core` keeps only what the PWA needs and phase 5 is re-planned.

- **OS-201 · App skeleton** (M) · depends on OS-003 and OS-006
  - Router, TanStack Query, API client generated from OpenAPI and the design-token structure (full themes in OS-214).
- **OS-223 · Internationalisation (English and Spanish)** (M) · depends on OS-201 and OS-008
  - i18next (`react-i18next`) with the shared catalogues in `packages/core/i18n/{en,es}.json`, ICU messages for plurals and variables, and `Intl` formatting for dates, numbers, durations and paces.
  - **English by default.** On first visit, the browser language is used if supported, otherwise English. Settings → Language switches between English and Spanish, stored in `localStorage` (no flicker) and in the user's settings on the backend (follows you across devices and reaches the Android app).
  - Units (metric or imperial) are a separate setting from the language.
  - Server-generated text (emails, push notifications, share cards, AI summaries) uses the stored language. Activity names and descriptions are never translated.
  - The public screens (sign-in, sign-up, recovery) detect the language before the session exists.
  - *Done when:* every screen is translated in both languages, there are no hard-coded strings (lint rule) and CI fails if `es` is missing a key that exists in `en`.
- **OS-202 · Responsive layout** (M) · depends on OS-201
  - Desktop with a sidebar and two columns. Mobile with a top bar and a bottom tab bar (Feed · Search · Sync · Stats · You). Styled scrollbars and mobile safe areas.
- **OS-214 · Theme system** (M) · depends on OS-201
  - Design tokens in a single source file (JSON, in `packages/core`) that generates the CSS variables and the React Native app theme. Themes System, Light, Dark, OLED Black, High contrast, Forest, Ocean and Sunset. Picker with previews in Settings.
  - No flicker on load (inline script that applies the saved theme before painting), preference synced with the backend, and `prefers-color-scheme` and `prefers-contrast` respected.
  - Map (light or dark style) and charts adapted to each theme.
  - *Done when:* switching themes leaves no hard-coded colour visible and every theme passes WCAG AA contrast in CI.
- **OS-203 · Sign-in screen** (M) · depends on OS-110 and OS-201
  - Prototype design: on desktop, a brand panel on the left with the claim "Your sports and health data together." and, as a second line, "Sync with Garmin, import your Strava history and follow your form without depending on anyone" and gentle animated waves (Canvas 2D, no library, still with `prefers-reduced-motion`), and the form on the right; on mobile, a single column. Links to "Create your account" (OS-221) and "Forgot it?" (OS-222).
  - Step 1: email and password (show/hide, "remember this device for 30 days"). Step 2: 6-digit TOTP code with auto-advance, pasting the whole code and a link to use a recovery code. "Continue with Google" (OS-220). "Sign in with a passkey" (OS-405).
  - Clear errors that never reveal whether the email or the password was wrong, rate limiting with increasing delays and the right `autocomplete` values (`username`, `current-password`, `one-time-code`) for password managers.
  - **The sign-in screen shows no user data** (no numbers or routes): it is the only thing visible without a session.
  - Every route redirects to sign-in without a session. "Sign out" is in Settings and in the profile.
  - *Done when:* no data can be seen without a session (checked with end-to-end tests) and the password + TOTP flow works with a password manager.
- **OS-221 · Initial sign-up (first time)** (M) · depends on OS-110 and OS-203
  - Only exists while there is no account: one instance, one user. Sign-up closes afterwards.
  - Step 1: **setup code** + name, email and password (at least 12 characters, strength meter, check against breached passwords with Have I Been Pwned's *k-anonymity* API), and acceptance of how data is handled. Also "Sign up with Google" (OS-220).
  - **Setup code:** the server generates it on first boot and writes it to its Railway logs. Without it the account cannot be created, so nobody who finds the URL can register before you. It expires once used.
  - Step 2: enable TOTP with the QR code and the text key, verifying a code. Step 3: 8 recovery codes (copy or download), confirming they have been saved. Final: "All set" with next steps (connect Garmin, import Strava, theme and zones).
  - *Done when:* after the account is created, `/signup` returns "sign-up closed" and the setup code no longer works.
- **OS-222 · Password recovery** (M) · depends on OS-221
  - "Forgot it?" → email → the same message whether or not the account exists → single-use link that expires in 30 minutes → new password **plus the TOTP code or a recovery code** (someone with access only to your email cannot get in) → all other sessions are closed and a notification email is sent.
  - Random 32-byte token stored only as a hash, rate limiting per IP and per account, and resend at most every 60 s.
  - Transactional email with **Resend** (the free tier is enough), each instance with its own Resend account. Without a custom domain, Resend's test sender only delivers to the account owner's address, which is exactly the single user of the instance; a verified domain (SPF and DKIM) is optional. Emails in the user's language.
  - Plan B if everything is lost: the server command `zankup reset-password`.
- **OS-405 · Passkeys (WebAuthn)** (M) · depends on OS-110 and OS-D06
  - Passkey registration and sign-in, verified with the device's fingerprint or face recognition and synced by the password manager (Google or iCloud). The backend comes first; the PWA uses it in the sign-in screen (OS-203) and in Profile → Sign-in methods.
  - The relying party ID is the instance's domain (`zankup.com`), which must stay stable: changing it invalidates every passkey. `/.well-known/assetlinks.json` on that domain lets **the same passkey work in the Android app** (OS-503).
  - A passkey replaces both the password and the TOTP code (it is already two factors: the device and the fingerprint or PIN).
- **OS-220 · Sign-in with a Google account** (M) · depends on OS-110 and OS-203
  - "Continue with Google" with OpenID Connect (authorization code flow with PKCE and `state`/`nonce`), requesting only `openid email profile`. The backend validates the ID token (issuer, audience, expiry, `email_verified`) and compares it with the linked Google `sub`: **only your account can get in**, even though other people have Google accounts.
  - Linked and unlinked from Profile → Account and sources → Sign-in methods. Unlinking requires another active method (password with 2FA or passkey).
  - Signing in with Google does not ask for the TOTP code: it counts as strong verification because Google already applies its own two-step verification.
  - Same Google Cloud project as the Google Health API (OS-603), but with its own OAuth client and no health scopes.
  - Self-hosted: each instance creates its own OAuth client in Google Cloud, with its own domain (`https://zankup.com/api/auth/google/callback` for the reference instance) or its `*.vercel.app` URL as the redirect (steps in OS-224).
  - *Note:* Google requires fixed redirect URLs. It works in production and locally, but not on Vercel previews (changing URLs), where you sign in with a password.
  - *Done when:* signing in with your Google account works and any other account gets a generic rejection.
- **OS-204 · Feed** (M) · depends on OS-111 and OS-202
  - Sport chips, cards with a route thumbnail, list grouped by month with its totals, and infinite scroll.
- **OS-217 · "Your week" header and highlights** (M) · depends on OS-204
  - **On the home screen, above the feed:** the 7 days of the week (activity with the sport icon, today marked, future days), streak of consecutive weeks and the week's numbers (activities, km, time, effort). Home only has this, the sport chips, the feed grouped by month and a link to advanced search.
  - **In Stats, "Now" section:** highlights carousel with training status and the last-4-weeks bar, weekly load vs your average, weekly goal with projection, estimated VO₂max and FTP, and recent records. Cards that depend on the engine (phase 3) appear when their data exists.
- **OS-219 · Advanced search screen** (L) · depends on OS-204
  - Its own "Search" section (sidebar on desktop and a tab on mobile). Home has no advanced filters, only a link to this screen.
  - Search by name, description or place. Filters: sports (several at once), period (year, month, last 12 months or date range), distance, duration, minimum elevation, average HR, gear, "with record", "with description", "with GPS track" and source (Garmin or Strava). Active filters appear as chips that can be removed with one tap.
  - Sort by date, distance, duration, elevation, effort or speed. Results summary (activities, km, hours, elevation). Table on desktop and cards on mobile, paginated.
  - On mobile, filters open in a bottom sheet with a "Show N results" button.
  - In the API: `GET /api/activities/search` with every filter, sorting and a cursor; indexes on date, sport and distance, and Postgres full-text search (`simple` + `unaccent`, valid for English and Spanish names). The search state lives in the URL so it can be shared and navigated back to.
  - *Done when:* any filter combination over 1,800+ activities responds in under 200 ms and the URL keeps the search.
- **OS-218 · Profile screen** (M) · depends on OS-203 and OS-111
  - Header with avatar, name, location, streak and device. The year's numbers. Tabs: Summary (year by sport, all-time history, records, physical data), Goals, Estimates (with their explanation and source activity), Gear, Account and sources (Garmin, Google Health, import, export, sign-in methods, sessions).
  - Edit profile (name, location, date of birth, sex, weight, height). On mobile it is the "You" tab and gives access to Settings.
- **OS-205 · Activity detail: header and numbers** (S) · depends on OS-204
- **OS-206 · Route map** (M) · depends on OS-205
  - MapLibre with our own style. Colour by pace, HR, power or altitude. Start and finish. Fallback without WebGL.
- **OS-215 · Chart foundation** (M) · depends on OS-214
  - ECharts with a theme generated from the tokens of each Zankup theme. Our own SVG components for static charts (route thumbnail, sparklines, zone bar). Animations that respect `prefers-reduced-motion`, lazy loading off-screen and accessible summaries.
  - *Done when:* there is a component catalogue with an example of each kind (static, passive and active) in every theme.
- **OS-207 · Profile and charts synced with the map** (M) · depends on OS-206 and OS-215
- **OS-216 · Select a section on the profile** (S) · depends on OS-207
  - Drag over the profile to pick a section: shows its distance, time, pace, HR and elevation, and highlights it on the map.
- **OS-208 · Laps, best efforts and zones in the detail** (S) · depends on OS-205
- **OS-209 · Sync button with progress** (S) · depends on OS-112 and OS-202
  - Progress sheet polling every 2 s. The feed refreshes when it finishes.
- **OS-210 · Installable PWA** (S) · depends on OS-201
  - Manifest, icons and splash screen with `vite-plugin-pwa`.
- **OS-211 · Offline mode** (M) · depends on OS-210 and OS-204
  - Service worker for the app, IndexedDB for viewed activities and their streams, map tile cache and an "offline" notice.
- **OS-212 · Basic settings** (S) · depends on OS-203
  - Language, units, max/resting HR, Garmin key status and expiry, devices with an open session.
- **OS-213 · Quality** (M) · depends on OS-204…OS-209
  - End-to-end tests (Playwright) of the main flows in both languages, Lighthouse on mobile (≥ 90 for performance and PWA) and basic accessibility.
- **OS-225 · Strength training detail** (M) · depends on OS-105 and OS-205
  - The parser reads Garmin's set data from the FIT (exercise, reps, weight, duration and rest per set). The detail shows sets grouped by exercise, total volume (reps × weight) and a front/back body map with the muscle groups worked, as in the prototype's strength screen.
  - *Done when:* the anonymised strength fixture shows the same sets, reps and weights as Garmin Connect.
- **OS-226 · Import the full Garmin history from the app** (M) · depends on OS-113, OS-112 and OS-221
  - Today the history load (OS-113) needs the Railway CLI, SSH and a terminal: fine for the owner, not for anyone else. A button in the last step of sign-up ("All set") and on the Sync screen starts it as a background job, and the app shows its progress (window X of Y, downloaded, still to import). Closing the tab does not stop it; running it again resumes.
  - Backend: `POST /api/sync` accepts `{"history": true}` (or a start date) and queues the same windowed load. Two worker changes: a long load must not block the imports it queues (a second worker thread, or imports interleaved between downloads), and a job that is alive must not be taken for stale after 15 minutes (heartbeat on `locked_at`).
  - *Done when:* a new instance with only a Garmin key gets its whole history by pressing one button, with progress visible and no terminal.
- **OS-224 · Self-hosting kit** (M) · depends on OS-005, OS-006 and OS-009
  - A production `docker compose` (API, worker, Postgres, volume and the built PWA behind one origin) for your own server, and a **"Deploy on Railway" template** plus Vercel instructions for the cloud path.
  - `docs/self-hosting.md`, step by step and in English: getting your own Garmin Personal API key, creating the Google OAuth client (OS-220), Resend, and later the Google Health client (OS-603) and the optional Anthropic key (OS-701). Every variable in `.env.example` is explained there.
  - Updates: how to upgrade an instance (migrations run on start) and how to back it up and restore it (OS-114).
  - *Done when:* someone other than you deploys a working instance from the guide alone, without help, in under an hour.

**M2 is done when** you open the installed PWA on your phone, see today's activity with its map, everything you have already viewed works without coverage, the whole app can be switched between English and Spanish, and another person can deploy their own instance with the self-hosting guide.

## Phase 3 · Advanced metrics → **M3**

**Status (9 Nov 2026):** done. Built in one branch: metrics in `packages/metrics` 0.2.0, the engine in
`backend/src/zankup/engine`, the stats API and screens. OS-323, which needed the Strava import, was
finished with phase 4: Stats → "How these metrics compare".

**Goal:** the "Premium" metrics, computed with your settings and free for everyone. The algorithms are implemented in `packages/metrics` (MIT), tested against reference values and documented one by one (formula, inputs and limits), so the package is useful on its own. The **historical computation engine** "cooks" the whole history in the background: estimates, curves, progress and events (see [architecture.md](architecture.md#historical-computation-engine-cooking-the-data)).

- **OS-313 · Incremental computation engine** (L) · depends on OS-115 and OS-107
  - Importing an activity chains its metrics, the affected days, the curves, the estimates and the progress, recomputing only what depends on it. Computed tables carry `metrics_version`.
  - *Done when:* a new activity updates fitness, records and estimates in under 30 s, and no screen computes anything when it opens.
- **OS-314 · Full recomputation and versioning** (M) · depends on OS-313
  - When settings (HR, zones, FTP) or an algorithm version change, everything is reprocessed from the original FITs in the background, with progress visible in the app. Idempotent.
- **OS-301 · Configurable zone model** (M) · depends on OS-212
  - HR (% of max, reserve or threshold), pace and power (FTP). Changing it recomputes the whole history in the background.
- **OS-302 · Relative effort (TRIMP)** (S) · depends on OS-301
- **OS-303 · Fitness, fatigue and form (CTL/ATL/TSB)** (M) · depends on OS-302 and OS-313
  - `daily_load` table with incremental recomputation and the `GET /api/stats/fitness` API.
- **OS-304 · Personal records and their progression** (M) · depends on OS-107
  - All-time and per-season records, and a "🏆 record" notice on import.
- **OS-305 · Grade-adjusted pace (GAP)** (M) · depends on OS-105
  - Minetti model with smoothed barometric altitude.
- **OS-306 · Power curve** (S) · depends on OS-105
  - Per activity and all-time (rides and ROUVY).
- **OS-307 · Race prediction** (S) · depends on OS-304
- **OS-308 · Volume and trends** (S) · depends on OS-111
  - Per day, week, month and year, and per sport, precomputed by the engine to respond instantly with years of history.
- **OS-309 · Matched routes** (L) · depends on OS-106
  - Group activities by route similarity and compare times.
- **OS-310 · Personal heatmap** (M) · depends on OS-106
- **OS-311 · Stats screen** (M) · depends on OS-303…OS-308 and OS-320
  - "Now" highlights, fitness, volume, records, prediction and power curve, as in the prototype.
- **OS-312 · New metrics in the activity detail** (S) · depends on OS-302, OS-305 and OS-306
- **OS-315 · Best-effort curves per period** (M) · depends on OS-313
  - Pace, power and HR for durations from 5 s to 2 h, over 42 days, 90 days, season and all time.
- **OS-316 · Performance estimates** (L) · depends on OS-315
  - Estimated FTP, threshold and critical pace, threshold HR (LTHR), observed max HR, VO₂max (VDOT) and predictions from 5K to marathon. Each one with method, confidence and source activities, so the app can explain where it comes from ("from your best 20 min on 12 October").
  - *Done when:* the estimates are validated against your real data (for example, FTP vs the ROUVY power curve) and recompute on their own.
- **OS-317 · Progress and comparisons** (M) · depends on OS-313
  - Comparison with the same period last year, trend of each estimate, aerobic efficiency (pace at equal HR), decoupling and matched routes getting faster.
- **OS-318 · Goals and streaks** (M) · depends on OS-313 and OS-218
  - Km or hours goals per week, month or year, with an end-of-period projection and streaks of active weeks.
- **OS-319 · Engine events** (S) · depends on OS-313
  - "New record", "your estimated FTP is up", "high load ramp". Consumed by notifications (OS-401) and the AI (phase 7).
- **OS-321 · Stats by period** (M) · depends on OS-308 and OS-219
  - A period selector (all time, last 12 months, year, month) that applies to the whole screen: summary compared with the same period last year, fitness and freshness for the period, volume by year, month or day (in hours or km), best efforts of the period vs all-time records, and the period's power curve over the all-time one.
  - `GET /api/stats/*?from=&to=` endpoints served from the engine's precomputed tables (OS-313).
- **OS-322 · Year-over-year progress** (S) · depends on OS-321
  - Cumulative distance per day of the year, one line per year (the selected one highlighted), per sport. Hovering compares every year on the same date.
- **OS-323 · Validation against Strava's values** (M) · depends on OS-406, OS-302, OS-303, OS-305 and OS-324
  - The Strava export's `activities.csv` carries Strava's own Relative Effort, calories and grade-adjusted distance for every activity (stored as references by OS-406). The engine compares them with ours: correlation (R²), mean absolute error, a scatter chart and the activities that differ the most, with links to them.
  - Shown in Stats → "How these metrics compare", for anyone who imported a Strava export. Each metric keeps its own documented formula (TRIMP, Minetti, Keytel): the comparison measures how close they are, it does not train a model to imitate Strava.
  - Optional "Strava-like effort scale": a linear fit of minutes per HR zone against the imported Relative Effort, computed in each instance from that person's own data, with the coefficients visible. Nothing trained on one person's data ships with the code.
  - For development, `tools/metrics_benchmark` runs `packages/metrics` over a local Strava export and prints only aggregate numbers. Its output is never committed; CI keeps using the anonymised fixtures (OS-010).
  - *Done when:* on the real history, best efforts match exactly, grade-adjusted distance reaches R² ≥ 0.98, effort and calories have their R² and error measured and shown, and every number in the screen links to the activities behind it.
- **OS-324 · Calorie estimate** (S) · depends on OS-301
  - Keytel's formula (from HR, weight, age and sex) with a MET-based fallback when there is no HR, in `packages/metrics`. Used for activities without device calories (GPX, TCX, old imports); the device's value from the FIT always wins.
- **OS-325 · Personal segments** (L) · depends on OS-216 and OS-309
  - Save any stretch selected on an activity's profile (OS-216) as a segment with a name. The engine finds it in every past and future activity that goes through it (route matching on the simplified track, then exact start and end on the streams) and stores each effort.
  - Segment screen: map, elevation profile, your efforts over time with the trend, your best and the conditions of each attempt (HR, power, date). On the activity detail, the segments it went through and how it compared with your best. No leaderboards: only your own efforts.
  - *Done when:* a segment created from one activity is found in the rest of the history in the background, and a new activity through it shows its time and position among your efforts.
- **OS-326 · Training calendar** (M) · depends on OS-303 and OS-308
  - Week and month views with one bubble per activity sized by its load and coloured by sport, weekly totals in the margin and the form (TSB) for each day. Click → activity; drag across days → summary of the selection.
- **OS-320 · Dynamic stats charts** (L) · depends on OS-215, OS-315 and OS-316
  - Following the catalogue in [architecture.md](architecture.md#charts): fitness and freshness (animated on load and after a sync, per-day cursor, click → activities), volume with period transitions, best-effort curves comparing periods, progression of records and estimates, animated goals.

**M3 is done when** the stats screen shows today's fitness and form, your estimates (FTP, threshold pace, predictions) with their explanation and your progress, all precomputed and updated automatically after each activity.

## Phase 4 · Notifications, history and privacy → **M4**

**Goal:** not missing anything from Strava.

**Status (9 Nov 2026):** done in one branch, OS-401 to OS-404 and OS-406 to OS-414 (OS-405, passkeys,
was done in phase 1; its Android link waits for phase 5). Checked on the real Strava export (1,816
activities, 15 years): every file reads, Garmin's activities are merged instead of duplicated, and gear,
photos, weather and the privacy zone come across. The TCX/GPX distance and elevation were calibrated
against 87 FIT files (distance median 0.995, elevation gain 0.95).

- **OS-401 · Web notifications** (M) · depends on OS-210 and OS-108
  - VAPID keys, per-device subscription and alerts for new activity, record and sync error, in the user's language.
- **OS-402 · Notification preferences** (S) · depends on OS-401
- **OS-403 · Garmin key expiry warning** (S) · depends on OS-401
  - Notification 14 days before and a warning in settings.
- **OS-404 · FIT and ZIP importer** (M) · depends on OS-106
  - Upload of loose FITs and Garmin ZIPs. Deduplication by start time and device.
- **OS-406 · Strava ZIP import** (M) · depends on OS-404
  - Tested with the real export: 1,816 activities (2011–2026), 1,635 FIT, 127 TCX and 53 GPX files, compressed or not. Reads `activities.csv` (104 columns, UTC times) and decompresses files on the fly. Strava's TCX files have whitespace before the XML declaration, which has to be stripped.
  - Deduplicates against Garmin by start time (±2 min): Garmin's FIT wins, and the name, description, gear, photos and weather are kept from Strava. Also imports what exists only on Strava (for example, strength sessions recorded with another app).
  - Smoothed distance and elevation for GPX and TCX. No best efforts from GPX-only distance. Strava's Relative Effort and grade-adjusted distance are stored as references to validate our metrics.
- **OS-407 · Server-side privacy zones** (M) · depends on OS-111
  - Trim when serving and exporting anything shareable, configured in settings.
- **OS-408 · Share an activity** (M) · depends on OS-407
  - Signed, expiring link, or an image. Privacy applied.
- **OS-409 · Edit an activity** (S) · depends on OS-111
  - Name, description, type and gear. The original FIT is never touched.
- **OS-410 · Gear and mileage** (S) · depends on OS-409 and OS-406
  - Shoes and bikes, with a mileage warning. Preloaded from `bikes.csv`, `shoes.csv` and the gear on each activity in the Strava export.
- **OS-411 · Export all your data** (S) · depends on OS-104
  - ZIP with the original FITs and JSON.
- **OS-412 · Photos in activities** (S) · depends on OS-406 and OS-205
  - The photos from the Strava export are stored next to their activity on the volume; you can also add or remove photos. Gallery in the detail and a thumbnail on the feed card. EXIF location is removed before anything is shared.
- **OS-413 · Year and month in review** (M) · depends on OS-321 and OS-408
  - A recap of each month and each year: totals, records, longest and hardest activities, most-used gear and progress against the previous period, as a sequence of cards. Each card can be shared as an image, with privacy zones applied and no route unless you choose to.
- **OS-414 · Activity replay** (S) · depends on OS-207
  - Animated 2D playback of the route on the map with the cursor moving along the profile and the charts, at 10×–100× speed, with pause and seeking. Respects `prefers-reduced-motion`.

**M4 is done when** your whole history is in Zankup, notifications reach you and you can share an activity without revealing where you live.

## Phase 5 · Android app (React Native + Expo) → **M5**

**Goal:** a better mobile experience (background, reliable notifications, Health Connect, widget) reusing `packages/core`.

**Status (9 Nov 2026):** built in one branch, OS-502 to OS-509, and checked on an Android 16 emulator
(sign-in with password + TOTP, the fingerprint releasing the session, feed, detail with the synced
cursor, stats, search, settings, opening offline and recovering when back online). Waiting on accounts
only the owner can create: Firebase (notifications) and an Android OAuth client (Google sign-in). The
Play Store release (OS-510) is parked: builds are installed straight on the owner's phone.

That first pass stopped short of the PWA: four tabs, a basic detail and settings. Parity comes in blocks
(OS-511 onwards). Block 1, navigation and the missing screens (OS-511 to OS-515), is built: a Sync
tab, the You tab as a hub, and Health, Coach, Gear and Import on the phone. Block 2, the full activity
detail (OS-516 to OS-518), and block 3, the full stats, training screens and reviews (OS-519 to
OS-521), are built too, and so is block 4, the full settings and profile (OS-522 and OS-523). Block 5,
signing in (OS-524 to OS-526), brings the PWA's sign-in, the first account on a new instance and
passkeys to the phone. Linking Google and connecting Google Health still need the browser.

The performance proof of concept (OS-501) runs at the start of phase 2. This phase assumes React Native won; if Kotlin did, it is re-planned.

- **OS-502 · Expo project in the monorepo** (M) · depends on OS-501
  - `apps/mobile` with Expo Router, the new architecture and Hermes. EAS Build and EAS Update. CI with lint, tests and build. Theme generated from the `packages/core` tokens (OS-214), plus Android dynamic colour. i18n with the shared catalogues and `expo-localization`.
- **OS-503 · Authentication in the app** (L) · depends on OS-502, OS-110 and OS-220
  - **First sign-in**, with two options now and a third with OS-405:
    - **Native Google** with Android's Credential Manager (the app's own `modules/google-id`) and the instance's nonce. The app sends the ID token to `POST /api/auth/google/mobile` and the backend applies the same linked-account-only rule.
    - **Password + TOTP.**
    - *Later:* **passkey** with `react-native-passkey`, the same one as the PWA, thanks to the `assetlinks.json` published on the instance's URL.
  - **Day to day with the fingerprint:** the refresh token is stored in `expo-secure-store` with `requireAuthentication`, bound to Android's secure keystore and released only after biometric verification (`expo-local-authentication`). Opening the app means touching the sensor.
  - **Strong biometrics by default** (class 3). On many Samsung phones face recognition is weak (class 1/2) and cannot release keys, so the fingerprint is used there. A setting allows weak biometrics only as an app lock.
  - If the enrolled fingerprints change, there are several failures or the token expires, Google or the password is required again.
  - Setup: an Android OAuth client in Google Cloud with the package name and the SHA-1 fingerprints of the EAS and Play Store certificates.
  - Self-hosted: on first launch the app asks for the instance URL (`https://api.zankup.com`, your own domain or your `*.up.railway.app`), so one published app works with any instance.
  - *Done when:* on your S26 you sign in the first time with Google and afterwards open the app with just your fingerprint, and an unlinked Google account is rejected.
- **OS-504 · Feed, "Your week" header and detail** (L) · depends on OS-503
  - Parity with the PWA using the `packages/core` hooks. FlashList, native MapLibre and Skia charts.
- **OS-505 · Offline mode** (M) · depends on OS-504
  - TanStack Query's persistent cache and `expo-sqlite` for streams.
- **OS-506 · Background sync** (S) · depends on OS-505
  - `expo-background-task` (WorkManager underneath).
- **OS-507 · Notifications with FCM** (M) · depends on OS-401 and OS-503
  - `expo-notifications`. On the backend, sending through FCM in addition to Web Push.
- **OS-508 · Stats, search and profile** (M) · depends on OS-311, OS-219, OS-218 and OS-504
- **OS-509 · Home-screen widget** (M) · depends on OS-505
  - Latest activity and today's form, with `react-native-android-widget` or an Expo module in Kotlin.
- **OS-510 · Play Store internal testing release** (S) · depends on OS-504…OS-507 · **parked**
  - With EAS Submit. Parked on 9 Nov 2026: debug or release builds are installed on the phone instead.
- **OS-511 · Navigation on par with the PWA** (S) · depends on OS-504
  - A Sync tab (sync now, whole history, progress, latest runs) and the You tab as a hub to Health, Coach, Gear, Import and Settings.
- **OS-512 · Health dashboard on the phone** (M) · depends on OS-606 and OS-511
  - Last night with its hypnogram, today's figures with their sources and the 30/90/365-day trends, drawn with Skia.
- **OS-513 · Coach and weekly report on the phone** (M) · depends on OS-704, OS-705 and OS-511
  - The NDJSON stream read with `expo/fetch`; links to activities open their detail.
- **OS-514 · Manage gear on the phone** (S) · depends on OS-410 and OS-511
- **OS-515 · Import files from the phone** (M) · depends on OS-404, OS-406 and OS-511
  - Android's file picker and the same upload in parts as the PWA, read part by part from the file.
- **OS-516 · Full activity figures and editing on the phone** (M) · depends on OS-504
  - Every figure of the PWA, device, gear and weather; edit name, description, sport and gear; add and remove photos.
- **OS-517 · Map coloured by metric and replay on the phone** (M) · depends on OS-504
  - The route in pieces coloured over the shared ramp, its legend, replay at 10×/30×/100× and saving a section as a segment.
- **OS-518 · Zones, best efforts, laps, power and strength on the phone** (M) · depends on OS-504
  - HR and power zones, the power curve, best efforts, laps, splits, route rank, segments, strength sets and the body map (its shapes now live in `packages/core`).
- **OS-519 · Full stats on the phone** (L) · depends on OS-508
  - Period picker, "Now" (form, load, goals, events, estimates with method and sources), summary, fitness, volume by sport, curves, records with progression, year over year, efficiency and the comparison with Strava.
- **OS-520 · Training calendar, heatmap, routes and segments on the phone** (M) · depends on OS-519
- **OS-521 · Month and year in review on the phone** (S) · depends on OS-519
- **OS-522 · Full settings on the phone** (M) · depends on OS-508
  - A section per screen: general, heart rate and zones (with recomputing), notifications by kind and a test, the AI assistant and MCP tokens, privacy zones on a map, health sources (priority, deleting a source's data), exporting your data, Garmin and the signed-in devices.
- **OS-523 · Full profile on the phone** (M) · depends on OS-508
  - Editing who you are, the year's numbers, by sport this year and all time, records, physical data, goals you can add and delete, estimates, and the account (sign-in methods, passkeys listed and removed, Google unlinked).
- **OS-524 · Sign-in on par with the PWA on the phone** (M) · depends on OS-503
  - The PWA's look and errors, the password shown or hidden, the code in six boxes that submit themselves, an expired challenge sending you back, and "forgot it?".
- **OS-525 · First account setup on the phone** (M) · depends on OS-221 and OS-503
  - Account, two-step verification opened in the authenticator app on the same phone, recovery codes and loading the history. `POST /api/setup/totp` takes `client: "android"` and returns the app's tokens.
- **OS-526 · Passkeys on the phone** (M) · depends on OS-405 and OS-503
  - Signing in and adding one with `react-native-passkey`. Release builds are signed with the owner's own key, published in `/.well-known/assetlinks.json` on the relying party's domain, whose `android:apk-key-hash` origin goes in `WEBAUTHN_ORIGINS`.
- **OS-527 · Home and the phone's navigation** (M) · depends on OS-511
  - Tabs: Home, Activities, Health, Goals and You. Home with This week, the last day with activities, the last seven days and the metrics; Sync, the Coach, Search and Notifications in its header; Material Symbols as the icon set.

**M5 is done when** the Android app replaces the PWA on your phone, with reliable notifications and background sync.

## Phase 6 · Multi-source health → **M6**

**Goal:** sleep, HRV, resting HR and daily data from all your sources in a single model, without duplicates.

**Status (9 Nov 2026):** built in one branch, OS-601 to OS-610. The dashboard was checked with sample
data from two sources; Garmin's health endpoints were checked against the real Personal API (this watch
records no sleep or HRV). Connecting Google Health in production needs the owner to enable the API and
its scopes in Google Cloud (docs/self-hosting.md). OS-609 and OS-610 are evaluations, written up in
docs/findings.md.

- **OS-601 · Health data model** (M) · depends on OS-102
  - Tables `sleep_sessions` (stages), `daily_health` (resting HR, HRV, SpO₂, respiration, steps), `sources` and `source_priority`. Every data point stores its source and its `external_id`.
- **OS-602 · Garmin: sleep, HRV and daily data from the Personal API** (M) · depends on OS-601 and OS-103
- **OS-603 · Google Health API connector (Fitbit Air)** (L) · depends on OS-601
  - Google OAuth 2.0 with minimal read-only scopes (sleep, HR, HRV, SpO₂, activity). Tokens encrypted in the database. Incremental sync.
  - Self-hosted: each instance uses its own OAuth client, added to the self-hosting guide (OS-224).
  - *Note:* a Google OAuth app in "Testing" mode issues refresh tokens that expire after 7 days. It has to be checked whether that is acceptable for personal use (re-authorising every week) or whether Google's verification for health scopes is needed.
- **OS-604 · Google Health webhooks** (M) · depends on OS-603
  - Endpoint on Railway that receives new-data notifications, with signature verification. The cron remains as a fallback.
- **OS-605 · Priority rules and deduplication between sources** (M) · depends on OS-602 and OS-603
  - Examples: sleep → Fitbit Air if you wore it that night; activities → Garmin; resting HR → the source with the most hours of data. Configurable in settings.
- **OS-606 · Health dashboard** (M) · depends on OS-605
  - Trends of sleep (stages and score), HRV, resting HR and "readiness" combined with the form (TSB) from phase 3.
- **OS-607 · Reading Health Connect from the Android app** (M) · depends on OS-502
  - With `react-native-health-connect`.
  - A local alternative to Google's API for the native app.
- **OS-608 · Import from the watch over USB** with a desktop tool or a local script (S)
- **OS-609 · Evaluate offline map downloads** for routes (M)
- **OS-610 · Evaluate recording activities with the phone's GPS** on Android (L)

**M6 is done when** the health dashboard shows last night's sleep and HRV, even when they come from different devices, without duplicates.

## Phase 7 · AI assistant → **M7**

**Goal:** Zankup explains your data in natural language, with a small and cheap LLM via API, without sending more data than necessary.

It can start as soon as M3 is done (it needs the metrics), in parallel with phases 4 to 6.

**Status (9 Nov 2026):** built in one branch, OS-701 to OS-709, with a fake SDK client in the tests:
nothing in CI calls Anthropic. The backend is `zankup.ai` (client, context, tools, summary, weekly,
coach, eval), the API `/api/ai/*`, `/api/activities/{id}/summary` and `/api/mcp`. Without
`ANTHROPIC_API_KEY` every AI feature is hidden; with it, the Coach appears in the PWA's navigation,
each recent activity gets a summary once the engine has its metrics, and Monday's report goes through
the Batch API. Pending on the owner's side: an Anthropic key in Railway and a first run of
`python -m zankup.ai.eval` against real data to tune the prompts (OS-707 ships the set and the
grounding check, not a baseline). The Coach's stream goes through the Vercel proxy: if the proxy
buffers it the answer arrives whole, which is acceptable at this length.

- **OS-701 · LLM client in the backend** (S) · depends on OS-101
  - Official `anthropic` SDK with `claude-haiku-5-5`. Each instance brings its own key in the Railway variables; **without a key, every AI feature is hidden** and the rest of the app works the same. Retries and typed errors. Token and cost logging per call in the `llm_calls` table.
- **OS-702 · Data minimisation** (M) · depends on OS-701
  - A layer that builds the LLM context **without coordinates or profile data**: only aggregated numbers (distance, times, zones, TRIMP, fitness, records, sleep). With tests that guarantee no latitude or longitude ever leaves.
- **OS-703 · Automatic summary of each activity** (M) · depends on OS-702 and OS-304
  - 2–3 sentences on import, in the user's language: what it was, how it compared with usual and any record. With structured output (`output_config.format`): `{suggested_title, summary, highlights[]}`. Can be turned off.
- **OS-704 · Weekly report** (M) · depends on OS-703 and OS-303
  - Every Monday: volume, load, fitness and form, sleep and a recommendation. Generated in batch (Batch API, half price) and sent as a notification.
- **OS-705 · "Coach": natural-language questions** (L) · depends on OS-702
  - Chat in the PWA. The model does not receive the whole history: it uses **tools** that call our own API (`search_activities`, `fitness_stats`, `records`, `daily_health`) and answers with numbers and links to activities. Streamed responses.
- **OS-706 · Prompt caching and cost control** (S) · depends on OS-705
  - Stable, cached system prompt and tool definitions. Monthly spending cap with a warning. Measure `cache_read_input_tokens`.
- **OS-707 · Quality evaluation** (M) · depends on OS-703 and OS-705
  - A set of 30–50 real activities and questions with expected answers, in both languages. It checks that the quoted numbers match the database (nothing invented). Run before changing the prompt or the model.
- **OS-708 · Privacy and transparency** (S) · depends on OS-702
  - A global "use AI" switch in settings, an explanation of what data is sent, and nothing stored with the provider that is not needed.
- **OS-709 · Read-only MCP server** (S) · depends on OS-705 and OS-702
  - Exposes the Coach's tools (`search_activities`, `fitness_stats`, `records`, `daily_health`) as an MCP server so you can ask any AI assistant about your data. Read-only, behind a personal token created in Settings and revocable, with the same minimisation as OS-702 (no coordinates).
  - Works without an Anthropic key: the assistant you use brings its own model.

**M7 is done when** every new activity arrives with its summary, the weekly report arrives on Monday and the Coach answers questions like "how am I doing for a 10K?" with correct numbers.

## Phase 8 · Several people, one Zankup → **M8**

**Goal:** more than one person can use the same instance (api.zankup.com), each with their own data, their own service keys and a first run that tells them exactly what to do, without the owner paying for anyone else's AI or Garmin.

**Status (10 Nov 2026): in design, not started.** It replaces the "one user per instance" decision (architecture.md, principle 5) that the owner made at the start and now wants to change. Every user-owned table already has `user_id` and every query filters by it (OS-D04), so the data model is ready; what changes is who can sign up, which settings belong to whom and what a new person sees.

**Agreed so far:**
- **Each user brings their own keys:** Garmin's Personal API key, Anthropic (the Coach) and Resend (e-mail alerts) become per-user instead of instance keys. The owner pays only for hosting: Railway compute, database, storage and backups, which grow slowly with each person.
- **Sign-up and sign-in with e-mail and password, a passkey or Google**, as in LoverCast. E-mail sign-up needs the account e-mails (verifying the address, resetting the password), which the instance sends with the owner's Resend (its free tier is plenty to start); each user's own alerts can still go out with their own key.
- **Google OAuth:** sign-in asks only for basic scopes (`openid email profile`), which go to production with at most a light brand verification, as in LoverCast with Supabase. Cloud Google Health asks for sensitive health scopes, which need Google's full verification: other people use **Health Connect** on their phone instead, and cloud Google Health stays for the owner while the OAuth client is in Testing (its test users are added by hand, up to 100).
- **An admin role** for the instance-wide settings (server keys and defaults, backups, alerts, sync interval…); everyone else sees only their own settings.
- **A Welcome** that separates what is required from what is optional, with instructions and links for each required step.
- **Sample-data screens:** until a section has real data it shows the app's structure with realistic sample data, faded, labelled "Example" and completely inactive, with an "Enable now" button that explains how to turn it on. Never a blank page or a row of zeros.

**Open:** who can sign up: an invitation from the owner, a list of allowed e-mails, or open sign-up.

**Distribution (10 Nov 2026, most likely):** the LoverCast model: **one shared server (api.zankup.com) with many users**, and the Android app talking only to it (`OTHER_INSTANCES = false` in `SignIn.tsx`). Its notifications then reach every user through the owner's Firebase project, with no relay between instances. Self-hosting stays possible for whoever builds their own app and Firebase project, but it is not the product.

- **OS-801 · Decision: several users per instance** (S) · decision
  - Update architecture.md (principle 5 and "No payments") and self-hosting.md: one shared server with many users as the product, what is per user and what is per instance, the admin role, who pays for what. Settle the open question on who can sign up.
- **OS-802 · Admin role and the instance's settings** (M) · depends on OS-801
  - An `is_admin` flag (the owner, from setup). `/api/instance` and the instance keys become admin-only; the Settings sections that hold them (Zankup Coach model and budget, backups, alerts, sync interval, Google Health switch) show only to the admin, on the app and the web. The scheduler stops assuming a single owner (`owner_email`) and works per user.
- **OS-803 · Service keys per user** (L) · depends on OS-802
  - Garmin, Anthropic and Resend keys move from `instance_keys` to per-user keys, encrypted the same way, with the instance's keys as the owner's (and, where the admin allows it, a fallback). Garmin's sync, health and expiry alerts, the Coach, summaries, the weekly report and the alerts all resolve the key of the user they work for. A migration moves today's instance keys to the owner.
- **OS-804 · Sign-up with e-mail and password, a passkey or Google** (M) · depends on OS-801
  - A new person creates their account with e-mail and password (with the address verified by e-mail, and the code step as today), a passkey or Google (the account is created from the verified e-mail and `sub`), according to the rule chosen in OS-801 (invitation, allowed e-mails or open). Account e-mails (verification, password reset) go out with the instance's own Resend. Today an unlinked Google account is rejected with a message meant for the owner ("link it in your profile"); the message for someone without access should say so plainly. Rate limits on sign-up.
- **OS-805 · The Coach per user** (S) · depends on OS-803
  - With the user's own Anthropic key: their own monthly budget and model, their own MCP tokens; without a key, the Coach stays hidden for that user only.
- **OS-806 · E-mail alerts per user** (S) · depends on OS-803
  - Garmin sync failures, key expiry and the weekly report by e-mail go out with the user's own Resend key to their own address; without it they arrive as app and web notifications only.
- **OS-807 · Health sources for other people** (S) · depends on OS-802
  - Health Connect on the phone as the main source for everyone; Garmin health with the user's own key; cloud Google Health offered only to the admin (or to the OAuth client's test users) until its verification is worth doing. The Health screen and its settings say which sources each person can use.
- **OS-808 · Welcome** (L) · depends on OS-803 and OS-804
  - A first run, on the app and the web, before the rest of the app:
    1. **You** (required): name and photo (from Google when signing in with it), birth date, weight, max HR and sports. Without them there are no zones, load or reliable estimates.
    2. **Your activities** (required, at least one): the Garmin Personal API key (where to get it in Garmin Connect, with the link; what to copy and paste; when it expires and how to renew it), importing FIT, GPX or TCX files (where each platform lets you download them), or the Strava export (how to request it on strava.com, how long it takes and uploading the ZIP).
    3. **Optional**, clearly marked and each with what it adds: Zankup Coach (Anthropic key), e-mail alerts (Resend key), health (Health Connect, Garmin, Google Health).
  - Anything optional can be skipped and turned on later from Settings; what is required keeps the first-steps card (OS-810) until it is done.
- **OS-809 · Sample-data screens** (L) · depends on OS-808
  - Until a section has real data it shows the app with realistic sample data: a week with a couple of runs and a ride, a night's sleep lanes, HRV and readiness charts, a goal halfway, VO₂ max and FTP. Faded and labelled **"Example"** so it is never taken for real data, and **completely inactive, with no logic**: static pictures that cannot be tapped (no opening the sample activity, no chart readout) and that call no API.
  - Over each section, what it will hold ("Your activities will show here", "Here you'll see your sleep") and an **Enable now** button, the only thing that responds, which opens a dialog with that section's steps and links (connect Health Connect, add the Garmin key, import, "you need runs with heart rate"…).
  - As soon as a section gets real data its sample goes; the others stay until they are turned on. Home, Activities, Health, Stats, Goals and the metrics cards, on the app and the web.
- **OS-810 · First steps on Home** (M) · depends on OS-808
  - A card that lists what is left (first activity, health connected, physical data, first goal…) and ticks itself off as each one happens; it goes when everything required is done.
- **OS-811 · Running for several people** (M) · depends on OS-802
  - Syncs, health fetches, metrics and backups per user without one person's history slowing everyone else (fair queues, limits per user), storage per user visible to the admin, and the edge's 429s (Railway) kept in mind: the apps already fall back to what they kept.
- **OS-812 · Privacy for people who are not the owner** (M) · depends on OS-804
  - A privacy policy page (what is stored, where, who can see it: the admin cannot read anyone else's data in the apps), self-service export and account deletion for every user (they exist for the owner), and deleting a user removes their files, photos and keys.
- **OS-813 · Activity files and photos in object storage** (M) · depends on OS-801
  - The original FIT, GPX and TCX files and the photos (today on the API's Railway volume: about 0.6 GB for the owner's whole history, against a 5 GB volume on the Hobby plan) move to S3-compatible storage, most likely Cloudflare R2: several times cheaper per GB than a Railway volume, no egress fees (photos and files are read often) and no size cap. The raw store and the photo store get an S3 implementation behind their current interfaces, existing files are migrated, and the API no longer depends on its disk, so it can run more than one replica. Postgres stays on Railway (about 0.4 GB for the owner; the per-second series are its bulk).
  - Rough numbers: one person with years of history ≈ 1 GB in total; a new one grows a few MB a month.

**M8 is done when** someone the owner lets in signs up with e-mail, a passkey or Google, goes through the Welcome, sees the app with sample data and an "Enable now" on each section, brings in their activities with their own Garmin key or an import, and never sees the owner's data or settings.

---

## Phase 9 · Web and app in step → **M9**

**Goal:** the web catches up with the app. Since November 2026 every screen was polished on the app first (Home, the activity's charts, Goals, Stats, You, Settings, the Coach, gear, skeletons, sign-in), so the web is now behind. The web gets the same features and the same visual language, **adapted to a large screen rather than copied**: wider layouts, hover where the phone has a tap, keyboard and mouse, side panels instead of bottom sheets.

**Status (13 Nov 2026): done** (#243 to #256), followed by phase 10 for what the app gained afterwards. Planned on 10 Nov from a screen-by-screen comparison of `apps/web` and `apps/mobile`. It is parity only as far as it makes sense: some things belong to one side.

**Stays on the app only:** Health Connect, the home-screen widget, background sync, the device's fingerprint for the session, the keyboard and edge-to-edge handling, and the offline notice as a pill over the tab bar (the web keeps its own banner).

**Stays on the web only:** the advanced search page with every filter (the app gets the useful filters, OS-919), the chart catalogue (`/catalogue`, a development page), the public shared-activity page (`/s/…`), downloading the original FIT/GPX/TCX file, the desktop split of list and detail, and the MCP token management (also on the app, but set up from a computer).

**From the web to the app:** a few gaps run the other way and are part of this phase (OS-919).

- **OS-901 · Decision: what each side keeps for itself** (S) · decision
  - Write in architecture.md, under "Clients", the list above (app only, web only, both) and the rule for new work: a feature lands on both clients in the same block of work unless it is on one of those lists. The web's layout rule for large screens: content up to about 1100 px wide, two columns where the phone stacks cards (charts side by side, settings with their list on the left), hover replaces the phone's tap for chart readouts.
- **OS-902 · The app's visual language on the web** (M) · depends on OS-901
  - The shared card title inside every card with its chips or figure on the right (as `CardTitle` on the app), the same spacing above and below a chart and its legend, pill-shaped buttons with a quieter secondary style, the gap between a field's label and its control, and the hairline borders of the tiles. A single place in `index.css` (and tokens in `packages/core` where they are shared) instead of per-screen tweaks. Every screen afterwards uses it.
- **OS-903 · Navigation like the app** (M) · depends on OS-901
  - The web's sections follow the app's tabs: **Home, Activities, Health, Goals, You**, in the sidebar on desktop and in the tab bar on mobile, with Stats, Search, the Coach and Settings reached as on the app (from You, the Home header and the Activities header) while keeping their URLs. The feed moves from `/` to `/activities`, with the list beside the detail on desktop as today; old links keep working.
- **OS-904 · Home** (L) · depends on OS-902 and OS-903
  - The app's Home at `/`: this week with the streak, the last day with activities shown only when it is today or yesterday (one card per activity, side by side on desktop instead of swiped), that day's steps, heart rate and HRV (the health card stays even without activities), the last seven days by sport with the health averages, and the metrics (VO₂ max, FTP with W/kg, today's steps, training load). The Coach and notifications in the header. On desktop the widgets sit in a grid.
- **OS-905 · Loading skeletons** (S) · depends on OS-902
  - The shapes of what is coming (feed cards, activity, Health, Stats, You) while data loads, as on the app, instead of "Loading…"; and the screens fall back to what was kept when the server is unreachable, without a skeleton over data that is already there.
- **OS-906 · The latest activity, on arrival** (M) · depends on OS-903
  - When a new activity has synced since the last visit (at most three days old), a panel on arrival, like the app's bottom sheet: its name and description to edit, its photos with the map beside them, the gear, OK and "Advanced editing". A dialog on desktop, a sheet on mobile; it shows once per activity, and the first visit only remembers.
- **OS-907 · Activity: each chart on its own card** (L) · depends on OS-902
  - The unified profile becomes one card per metric (altitude, heart rate, pace or speed, power, cadence…), each with its heading, its own readout and a footer of centred figures (average, max, NP and VI for power, ascent and descent, best pace, GAP…). On desktop the readout follows the mouse and a click pins it; on touch a tap shows it and a tap on it or its line hides it, as the app does. The section switch over the cards, with the section's figures and "save as segment" below them. Power in the light violet of every theme, the speed axis that does not clip on a descent (`rangeOf`, moved to `packages/core` from the app's `track.ts`), and the cursor shared with the map.
- **OS-908 · Activity: the map's controls on the map** (M) · depends on OS-907
  - As on the app: play and pause on the map, and an options button that opens what the route is coloured by (with its legend) and the replay speed, so nothing else sits around the map. The header under the map: sport icon, name, date, device and gear, then the figures in two columns and the Coach's summary, which folds with a chevron and remembers it.
- **OS-909 · Activity: deleting it and the order of its sections** (S) · depends on OS-907
  - "Delete activity" in the edit dialog, with a confirmation, using `DELETE /api/activities/{id}` (a Garmin activity is not brought back by the next sync). The sections in the app's order: on a ride the heart-rate zones follow the power curve and zones, on a run the splits follow the heart-rate zones; the description and best efforts at the end; every section with the shared card title.
- **OS-910 · Gear: the habitual one** (S) · depends on OS-902
  - Marking a pair of shoes or a bike as habitual (one per kind), shown first with the accent border and a "Habitual" badge, and used for new activities of that kind. You's "Gear" tab, which still says "soon", becomes the gear page.
- **OS-911 · Goals** (M) · depends on OS-903
  - Goals as their own section, as on the app: the pencil turns on editing (each goal can be changed or deleted) and the + adds one; a goal can be edited, not only deleted. Clicking a goal card opens and closes its evolution, the daily cumulative chart against the target (`GoalChart` on the app), with room between the text and the chart. The form groups each label with its options, with room between the blocks.
- **OS-912 · Stats: the period and the cards** (M) · depends on OS-902
  - The period as on the app: last 12 months, a year moved with arrows, a run of months from one to another, or all time. The cards as defined on the app: the volume and best-efforts chips on the title line, the volume total on the right, the aerobic-efficiency chart twice as tall, the summary against last year at the end, and a menu for the reviews, calendar, heatmap, routes and segments. Estimates leave Stats for their own page (OS-913) and goals for Goals (OS-911).
- **OS-913 · Estimates as their own page** (S) · depends on OS-912
  - The app's Estimates screen: the main ones (VO₂ max, FTP, threshold pace…) one card each, then the race predictions, reached from You. Replaces the panel in Stats and You's "Estimates" tab.
- **OS-914 · You and Settings** (M) · depends on OS-903 and OS-910
  - You as on the app: who you are (editable), a menu to Stats, Estimates, the Coach, Gear, Import, Account and Settings, then this year's summary with the physical data behind an eye that hides them (and remembers it). Settings in the app's sections (general, heart rate and zones, notifications, AI, privacy, health, your data, account), each with its own page; on desktop the list of sections on the left and the open one on the right. Account holds the sign-in methods, passkeys, Google, Garmin, sessions and signing out.
- **OS-915 · The Coach** (S) · depends on OS-902
  - As on the app: it opens at the top; a compact weekly report (chips, the recommendation in a soft box, "Read the full report"); "Write last week's report" only when there is none; two example questions under "Suggestions of questions you can ask"; the "the Coach can be wrong" note behind an info icon in the header; a taller, more visible question field; no intro text.
- **OS-916 · Notifications page** (S) · depends on OS-904
  - The bell in Home's header opens what the engine noticed lately (new records, estimates going up, risky load jumps), each opening its activity, as the app's Notifications screen. It replaces the events list in Stats.
- **OS-917 · Health: what the app added** (S) · depends on OS-902
  - Fetching the health history from Garmin from the Health settings (`POST /api/wellness/history`), the period in a menu in the header, the charts over the whole period with gaps where a day has no data, the chart titles inside the cards, and the sync button in the header.
- **OS-918 · Sign-in** (S) · depends on OS-902
  - The app's order: Google first, then the passkey, an "or", then e-mail and password with "Forgot it?"; the e-mail button quieter than Google's; the inputs and buttons styled apart; more room between the greeting and the first button.
- **OS-919 · What the app is missing from the web** (M) · depends on OS-901
  - The other direction, as far as it makes sense on a phone: the useful advanced-search filters in Activities (period, distance, time, elevation, with record, with description, with GPS), sorting the results and their summary; the share image (the 1080 × 1350 card of OS-408) next to the link when sharing an activity; and the "Now" figures (form, this week's load against the average) where they fit on Stats.
- **OS-920 · Parity check** (S) · depends on every other ticket of the phase
  - Go through both clients screen by screen, on a phone and on a desktop browser, with both languages and every theme, and note anything left; the web tests cover the new screens; architecture.md and the memory of what each side keeps are up to date.

**M9 is done when** someone who uses the app every day opens the web on a computer and finds the same Home, activity, Health, Goals, Stats, You and Settings, laid out for the large screen, with nothing the app has that the web is missing except what OS-901 keeps for the app.

---

## Phase 10 · The web catches up again → **M10**

**Goal:** what the Android app gained after M9 reaches the web. From 13 to 16 Nov 2026 (#257 to #328) the app got a Progress tab as Strava's, the activity page laid out as Strava's for rides, runs and strength, weight over time, Stats limited to what follows its period, and a round of fixes and performance work; almost all of it landed only in `apps/mobile`. The rule of phase 9 still holds: the same features and visual language, **adapted to a large screen rather than copied** (hover where the phone has a tap, dialogs or side panels where it has bottom sheets, grids where it stacks cards).

**Status (16 Nov 2026): done** in five PRs: OS-1015 (#331 on the app, #332 on the web), Progress (#333), weight, Stats and Health (#334), the activity page (#335) and charts and speed (#336); the parity check (OS-1014) was made on zankup.com with real data and its fixes are in #337. The React Compiler was tried on the web and left out: it made the main chunk 29 % larger. Built from the merged PRs #257 to #328 and a search of `apps/web` for each feature's endpoints and components.

**Already on the web:** the strength workout's body map as its cover and the richer Sets panel (#257, #260), the Coach's "Tell me more" report and its shorter, bolded version (#266, #268), the Strava and Strava dark themes with the `page` token (#276, #277, #279), each health figure's source mark (#325, the Google part), and the shared formatting fixes in `packages/core` (cached `Intl` formatters, #321).

**Stays on the app only** (added to OS-901's list): the launcher icon variants and the mint one (#281, #292, #328), the Android ripple and its tap delay (#319), the haptic clicks on charts (`modules/haptic-click`), the icon font and React Native's image cache (#322), and the bottom panel's drag (#269, #270).

- **OS-1001 · Progress as a section** (M) · depends on OS-903
  - Progress replaces Goals in the sidebar and the tab bar (Home, Activities, Progress, Health, You), as the app's tab order (#281); `/goals` keeps working and opens the Goals card's page. `/progress` lays out the app's cards in a grid on desktop (two or three columns) and one column on mobile, in the app's order: Volume, Series beside the Training log, Fitness, Relative effort, race predictions, Goals, Training zones, Skills, Best times, Monthly activities and Data sources. Each card opens its own page (`/progress/volume`, `/progress/fitness`…). The shared chip (one size, #261) and the cards' skeletons.
- **OS-1002 · Volume, Series and the Training log** (M) · depends on OS-1001
  - **Volume:** this week by sport and the last 12 weeks; its page measures time, distance, elevation or activities over 7 days to a year, against the span before or the same span years back, with the week's activities and a sentence on how it went. **Series:** the weekly streak in its flame (`GET /api/stats/streak`), and its month as a dialog with each day's sport and the band through the streak. **Training log:** weeks as bubbles, with filters, months and search.
- **OS-1003 · Fitness, Relative effort and race predictions** (M) · depends on OS-1001
  - **Fitness** (CTL, 1 month to 2 years, remembered) on Strava's scale (#259), its chart as tall as the page with the tightest round axis (#260, #324) and the day picked by hovering, "N activities" for a day with several. **Relative effort:** the weekly TRIMP against the range from the three weeks before. **Race predictions** (5 km to marathon, today or over months), the line as each week's prediction (#259), the range selector at the bottom (#283).
- **OS-1004 · Training zones, Skills and Best times** (L) · depends on OS-1001
  - **Training zones** (`GET /api/stats/zones`): HR in reds, pace in blues, power in purples (#281); each sport its own zones, a run opening on pace and a ride on power (#323); thick bars with the time beside them; 7D–1Y and the sentence on what moved most; a pencil to the zone settings. **Skills:** the radar of the best power over 12 durations, ringed by sprint, attack and climb, the watts on the ring underlined (#308), 8 weeks or all time; clicking a duration opens its nine levels for the rider's weight (`levelWatts`, #306), a dialog on desktop. **Best times** (`GET /api/stats/bests`, `/bests/recent`): the card with the last twelve months' three medals (#300), opening on running 10 km (#303); the page with run distances, ride power by duration and the longest ride, the record, the chart of one slot per best that scrolls in step with the list (#299, #305; drag or the wheel on desktop), bests by year and the top 10.
- **OS-1005 · Monthly activities, Data sources and Goals in Progress** (S) · depends on OS-1001
  - **Monthly activities:** this month's cumulative time over last month's, by sport (card and page). **Data sources:** the devices of the last 30 days (`GET /api/stats/devices`) and an "Apps and devices" page. **Goals:** the rings card and the list whose charts stay open, with the add and edit form; goals about elevation (`ascent`, #258).
- **OS-1006 · The activity page in Strava's order** (L) · depends on OS-907
  - `GET /api/activities/{id}/context` on the web (loaded beside the activity, not after it, as #320 does on the app). A **ride** (#271): Fitness before and after, Matched activities (with their page), Relative effort, Goals, Results (medals and segment places), the power chart with work, intensity and load, the power curve, power zones and HR zones interleaved with the charts. A **run** (#272): the same blocks, then the training analysis, splits, pace with moving and elapsed time and the fastest split, pace zones (bounds from the critical speed in the context), HR and its zones, cadence, elevation. A **strength workout** (#273): the exercises at a glance first (sets, reps, weight), relative effort, goals, heart rate and its zones, then the full sets. On desktop the blocks can sit in two columns beside the charts.
- **OS-1007 · The activity's charts and splits as the app's** (M) · depends on OS-1006
  - The elevation in grey behind power, HR, speed or pace and cadence; a grade-adjusted pace chart after pace (Minetti, #274); HR on rides; the altitude chart named Elevation. Zones as thick bars with the time beside them, not stacked (#283). **Training analysis** card (runs): the splits' bars in pace blue under a see-through elevation, the two fastest darker (#308, #309), and its page (`/activities/:id/analysis`) where hovering picks a split, shown in a box ("Split 1 · 5:29 /km · Zone 3 · Pace") and in the list below (`splitAt`, `elevationProfile`). **Splits** as Strava's table (Km · Pace · bar · Elev. · HR), no splits card on rides (#310).
- **OS-1008 · Goals reached** (S) · depends on OS-1006
  - The activity that took a goal past its target (`goals_reached` in the feed and the context): a capsule on its feed card and a banner above the Coach on its page (#274, #275).
- **OS-1009 · Weight over time** (M) · depends on OS-902
  - Health gets the Weight card after today's figures (latest weight, its change in 30 days, a 90-day line; with no weighing, it asks for the first), and `/health/weight` the page (#294): the line chart, 1M/3M/6M/1Y/All, the history newest first with the step from the one before, add, edit and delete (`GET/PUT /api/weight`, `DELETE /api/weight/{day}`). Changing the weight on the profile records today's weighing and refreshes the history (#297).
- **OS-1010 · Stats by its period** (S) · depends on OS-912
  - As #318: Stats keeps only what follows its period. Records become **Bests of the period** (each opening its best time, and a link to all of them); **Year by year** leaves for its own page (`/stats/years`), the chart filling the page with the years listed under it, a click on a year highlighting it; **how they compare with Strava** moves to Settings → Import. The summary first (#314), the period chips on the left (#288), months from last month to this one (#285), best efforts opening on power (#286) and aerobic efficiency under volume (#287). The same chips on the left in the reviews and the heatmap (#289, #290).
- **OS-1011 · Health: whose each figure is** (S) · depends on OS-902
  - The sleep score and Readiness are worked out by Zankup: they carry the Zankup mark in their corner, as the other figures carry their source's (#325), with "Score worked out by Zankup" as its title.
- **OS-1012 · Charts that slide to new values** (S) · depends on OS-902
  - As #317: when a filter changes, bars grow or shrink from their old height and lines move from the old line to the new one (never up from zero), on every chart that a filter changes (Fitness, Volume, predictions, zones, Stats). ECharts animates data updates already; check each chart keeps its series and keys between filters so it morphs instead of redrawing, and that its axis ends at the tightest round value (#324, `tightScale` moved to `packages/core`).
- **OS-1013 · The web's own speed** (M) · depends on OS-901
  - The web's counterpart of #320 and #321: the activity and its context fetched when the pointer rests on a feed card or a day (prefetch on hover and on touch start), the screens shown from the kept cache without waiting for the network, the React Compiler in the Vite build, and the map's cursor redrawing only its dot while a chart is scrubbed. Measured before and after with the browser's performance panel on a mid-range phone and on desktop (load, opening an activity, scrubbing a chart).
- **OS-1015 · A strength workout's type** (S) · depends on OS-902
  - As #331 on the app: a strength workout without recorded exercises can be given a type (Rowing, Hyrox, GRIT, Bodypump, Functional, Upper-body strength, Lower-body strength, Core), each with its muscles (`WORKOUT_MUSCLES` in core), so the body map always shows: as the activity's cover and on its feed card. The type's chips in the latest activity's panel and in Edit, for strength only, the last one chosen first; choosing one names the activity after it while it keeps the name it came with; the type after the date on the card and the page. The API (`workout_type`) is already there. First of the phase, as it was the last on the app.
- **OS-1014 · Parity check** (S) · depends on every other ticket of the phase
  - As OS-920: both clients side by side, screen by screen, on a phone and on a desktop browser, both languages and every theme (Strava and Strava dark included); the web tests and the e2e flows cover Progress, the new activity page and weight; OS-901's lists in architecture.md are up to date.

**Suggested blocks** (one branch and one PR each, per the working rules): Progress and its cards (OS-1001 to OS-1005); the activity page (OS-1006 to OS-1008); weight, Stats and Health (OS-1009 to OS-1011); charts and speed (OS-1012, OS-1013); then the check (OS-1014). OS-1015 goes first, on its own, as it is small and fresh from the app. The first two are independent of each other and can be built at the same time.

**M10 is done when** someone who opened Progress, an activity and their weight on the app this week finds the same on the web, laid out for the large screen, and the only differences are the ones OS-901 keeps for each side.

## Phase 11 · Native Android app (Kotlin + Compose) → **M11**

**Goal:** the Android app rewritten as a 100 % native app, from scratch, in Kotlin and Jetpack Compose. The React Native app works well; the reason is the product, not a failed measurement (OS-501 still stands): a native app feels and behaves as Android does (Material 3, predictive back, haptics, Glance widget, Credential Manager, Keystore, WorkManager and Health Connect with no bridge), and it is the app Zankup wants to be.

**Planned on 18 Nov 2026** from `apps/mobile` as it is after M10 (about 45 screens and 24,000 lines of TypeScript) and the API.

**Status (18 Nov 2026):** block 0 is built (OS-1101 to OS-1105): the [screen map](android-screen-map.md), `apps/android` with the API client generated from the OpenAPI document, the design system with every theme and its screenshot tests, and the texts in English and Spanish. Zankup Next installs on the S26 beside the React Native app (release APK 1.1 MB, cold start to its first screen 134 ms) and reaches the instance's API. Block 1 is built too (OS-1110 to OS-1112): signing in with password and code, Google or a passkey and creating the first account, the session behind the fingerprint with the lock, and the five tabs with every screen of the map as a typed route; checked on the S26 with the owner's account (password and code, the fingerprint, cancelling it and unlocking, signing out). Google needs its Android OAuth clients for `com.zankup.next` and `.debug`, and passkeys the new `assetlinks.json` deployed. Next: block 2, data, offline, sync and notifications.

**What is reused, and what is not:**
- **Reused:** the **server**, for all the data and every feature (sync, metrics, AI, notifications, health, import…), with its OpenAPI document as the contract; and the **screen structure of the React Native app** (`apps/mobile/app`: the tabs, every stacked screen, what each one shows and in what order), which is the functional spec.
- **Not reused:** no code from `apps/mobile`, its Kotlin modules or `packages/core` is imported, generated, wrapped or translated line by line. The theme, the translations, the domain logic the screens compute (formatting, zones, splits, scales…), the charts and the map are written anew for Android. The backend should not need changes; if a screen needs one, it is its own ticket and serves both apps.

**Working rules for the phase:**
- **The React Native app stays** in `apps/mobile` and on the phone until the cutover (OS-1191) and its deprecation (OS-1192). During the phase it gets fixes; any feature it gains is added to the screen map (OS-1101) the same day, so the native app catches up with it.
- **Three apps side by side on the phone:** Zankup (React Native, `com.zankup.app`), Zankup Dev (React Native, `com.zankup.app.dev`) and **Zankup Next** (native, `com.zankup.next`), signed with the owner's same key. Zankup Next needs its own Android OAuth client (Google sign-in), its own app in the Firebase project (notifications) and its package in `assetlinks.json` (passkeys); the `android:apk-key-hash` origin in `WEBAUTHN_ORIGINS` is the same, since the key is.
- One block = one branch = one PR, as in every phase.

**Stack:** Kotlin 2, Jetpack Compose with Material 3, type-safe Navigation Compose, Hilt, coroutines and Flow, OkHttp + Retrofit with kotlinx.serialization, Room, DataStore, Paging 3, WorkManager, Firebase Cloud Messaging, Credential Manager (Google and passkeys), `androidx.biometric` with the Android Keystore, the MapLibre Android SDK, charts drawn with Compose `Canvas`, Coil, Jetpack Glance, the Health Connect client, Baseline Profiles and Macrobenchmark. Tests with JUnit, Turbine, Compose UI tests and screenshot tests (Roborazzi).

### Block 0 · Decision and foundations

- **OS-1101 · The decision and the screen map** (S)
  - The decision in `docs/architecture.md` (why native now, what OS-501 measured, what is and is not reused). The **screen map**: every route of `apps/mobile/app` (tabs Home, Activities, Progress, Health and You; the activity and its analysis; Progress's pages; stats, calendar, heatmap, reviews, routes and segments; health and weight; gear, import, sync, notifications, Coach, assistants; settings by section; sign-in and setup), with what it shows, its endpoints and its interactions. It is the checklist of the phase and of the parity check (OS-1190).
- **OS-1102 · The Gradle project** (M) · depends on OS-1101
  - `apps/android`, outside the pnpm workspace: a version catalog, modules `app`, `core:network`, `core:data`, `core:domain`, `core:designsystem` and one `feature:*` per area. minSdk 26 (Health Connect), the latest targetSdk, edge-to-edge. `release` as `com.zankup.next` ("Zankup Next") until the cutover and `debug` as `com.zankup.next.debug` ("Zankup Next Dev", with the component catalogue), so both fit on the phone; release signed with `~/.config/zankup/android/signing.properties`. ktlint (detekt does not support Kotlin 2.4 yet), Android Lint, unit and screenshot tests and `assembleRelease` in `.github/workflows/android-native.yml`; `make android` and `make android-release` (build and `adb install`).
  - *Done when:* an empty Zankup Next with the Zankup icon installs beside the React Native app and CI is green.
- **OS-1103 · The API client** (M) · depends on OS-1102
  - Kotlin models and Retrofit interfaces generated from the backend's `openapi.json` (openapi-generator, kotlinx.serialization) on every build, so nothing generated is committed and it cannot go stale; `make generate-api` refreshes the document as before. The instance URL as base URL, the access token and its refresh in an OkHttp interceptor, errors mapped to one sealed type, NDJSON streaming for the Coach.
- **OS-1104 · The design system** (L) · depends on OS-1102
  - Written for Compose: the colour schemes of every theme (System, Light, Dark, OLED Black, High contrast, Forest, Ocean, Sunset, Strava and Strava dark) plus dynamic colour, typography, shapes, spacing, sport and zone colours, and the components every screen uses (cards, chips, bottom sheets, skeletons, empty and error states, top bars, list rows, segmented periods). Material Symbols as the icon set, the ZANKUP wordmark as an `ImageVector`. A catalogue screen in debug builds and screenshot tests of every component in light and dark.
- **OS-1105 · Languages** (S) · depends on OS-1102
  - `strings.xml` in English and Spanish with plurals, per-app language (Android 13+) and locale-aware formatting; Lint fails on hard-coded text and a test fails when an English string has no Spanish one.

### Block 1 · Session and navigation

- **OS-1110 · Instance and sign-in** (L) · depends on OS-1103 and OS-1104
  - The instance URL checked against `/api/health`; password + TOTP (the code in six boxes that submit themselves, the expired challenge, "forgot it?"); Google through Credential Manager with the instance's nonce (`/api/auth/google/mobile/nonce`, then `POST /api/auth/google/mobile`); passkeys through Credential Manager; the first account on a new instance (account, TOTP opened in the authenticator app, recovery codes, loading the history).
- **OS-1111 · The fingerprint** (M) · depends on OS-1110
  - The refresh token encrypted with a Keystore key that needs class 3 biometrics (`BiometricPrompt` with a `CryptoObject`), destroyed when the enrolled fingerprints change; refreshed with `rotate: false`. Weak biometrics only as an app lock, as a setting.
  - *Done when:* on the S26 you sign in once with Google and afterwards open Zankup Next with just your fingerprint.
- **OS-1112 · Navigation** (M) · depends on OS-1104
  - The five tabs in the React Native app's order, every stacked screen of the screen map as a typed route, `zankup://activity/<id>` deep links, predictive back, and the headers as the React Native app has them: Home's (the Coach when the AI is on, Notifications) and Activities' (Sync, Calendar, the search bar).

### Block 2 · Data, offline, sync and notifications

- **OS-1120 · Repositories and offline** (L) · depends on OS-1103
  - Room as the source of truth (feed, activities, the latest 60 series, profile, settings), DataStore for preferences; screens shown from what is kept and refreshed in the background; the offline notice and recovering when back online; signing out deletes everything.
- **OS-1121 · Background sync** (S) · depends on OS-1120 and OS-1111
  - A periodic WorkManager job (~30 min) that refreshes the feed and the week, keeps the latest series and updates the widget; with the fingerprint it waits for the next opening, as today.
- **OS-1122 · Notifications** (M) · depends on OS-1110
  - Firebase Messaging, the token registered on its device (`PUT /api/notifications/fcm`), one channel per kind, a tap opening what it is about.
- **OS-1123 · The domain logic on the phone** (M) · depends on OS-1102
  - What the screens compute themselves, written in Kotlin with its own unit tests: durations, distances, pace and speed by locale and units, zones, the split under the cursor, grade-adjusted pace, effort ranges, cumulative series, the tightest round axis, power levels for the rider's weight.

### Block 3 · Home, Activities and the activity

- **OS-1130 · Home** (M) · depends on OS-1112 and OS-1120
  - This week, the last day with activities, the last seven days and the metrics.
- **OS-1131 · The feed and search** (M) · depends on OS-1120
  - Paging 3, each card with its map or the strength workout's body map, goals reached, and search.
- **OS-1132 · The map** (L) · depends on OS-1104
  - MapLibre Android: the route coloured by metric with its legend, the dot following the charts' cursor, replay at 10×/30×/100×, the map controls and saving a section as a segment.
- **OS-1133 · The charts** (L) · depends on OS-1104
  - Lines and areas with the elevation behind, bars, zones as thick bars, the power curve and the radar; the cursor scrubbed with the finger and haptic clicks, at the screen's rate (120 Hz); values sliding to new ones when a filter changes. Built once, used by every screen.
- **OS-1134 · The activity, by sport** (L) · depends on OS-1132, OS-1133 and OS-1123
  - The blocks in the React Native app's order for rides, runs and strength (`/api/activities/{id}/context` loaded beside it): figures, fitness before and after, matched activities, relative effort, goals, results, power, pace, HR, cadence and elevation charts with their zones, laps and splits, best efforts, segments, the sets and the body map, the training analysis page, weather, device and gear.
- **OS-1135 · Editing and sharing an activity** (M) · depends on OS-1134
  - Name, description, sport, gear and a strength workout's type; adding and removing photos (the photo picker); the share card.

### Block 4 · Progress and stats

- **OS-1140 · Progress and its cards** (L) · depends on OS-1133 and OS-1120
  - Volume, Series, the Training log, Fitness, Relative effort, race predictions, Goals (with adding and editing), training zones, Skills, Best times, Monthly activities and Data sources, each with its page.
- **OS-1141 · Stats, the calendar and the reviews** (M) · depends on OS-1140
  - Stats by its period, year by year, estimates, the calendar, the heatmap and the month and year in review.
- **OS-1142 · Routes and segments** (S) · depends on OS-1132

### Block 5 · Health

- **OS-1150 · Health** (M) · depends on OS-1133 and OS-1120
  - Last night with its hypnogram, today's figures with their sources, Readiness, the 30/90/365-day trends, and weight with its page (add, edit, delete).
- **OS-1151 · Health Connect** (M) · depends on OS-1150
  - The same read permissions and history as today, sent to the API as the React Native app does.

### Block 6 · You, the Coach and settings

- **OS-1160 · You and the profile** (M) · depends on OS-1112 and OS-1120
  - Who you are (editable), the year's numbers, by sport, records, physical data, estimates and the account (sign-in methods, passkeys, Google).
- **OS-1161 · Settings** (L) · depends on OS-1160
  - Every section of the React Native app: general and themes, heart rate and zones with recomputing, notifications by kind and a test, the AI assistant and MCP tokens, privacy zones on a map, health sources, exporting your data, Garmin, the instance and the signed-in devices.
- **OS-1162 · The Coach and the AI** (M) · depends on OS-1103
  - The activity's summary, the weekly report and the Coach streamed as it is written, its links opening the activity; the assistants.
- **OS-1163 · Gear, import, sync and notifications** (M) · depends on OS-1120
  - Gear; importing files picked on the phone and uploaded in parts (`PUT /api/direct/uploads/{import_id}/{index}` is not in the OpenAPI document: it is added there, or called by hand); the Sync screen (now, whole history, progress, latest runs); the notifications inbox.

### Block 7 · What only Android has

- **OS-1170 · The home-screen widget** (M) · depends on OS-1121
  - Jetpack Glance: the latest activity and today's form, opening the activity.
- **OS-1171 · Icons, shortcuts and small touches** (S) · depends on OS-1112
  - The launcher icon variants (activity aliases), app shortcuts (Sync, the latest activity, the Coach), the Quick Settings tile to sync, themed icon.

### Block 8 · Quality, cutover and deprecation

- **OS-1180 · Performance** (M) · depends on Blocks 3 to 6
  - Baseline Profiles and Macrobenchmark for cold start, opening an activity, scrubbing a chart and scrolling the feed, plus memory and APK size, on the S26 and on a mid-range phone. Zankup Next must match or beat OS-501's figures and the React Native app's on the same phone.
- **OS-1181 · Accessibility** (S) · depends on Blocks 3 to 6
  - TalkBack on every screen, the largest font scale, 48 dp targets and the High contrast theme.
- **OS-1190 · Parity check** (M) · depends on every ticket above
  - Both apps side by side, every row of the screen map, both languages and every theme; the differences that stay are written down.
- **OS-1191 · The cutover** (S) · depends on OS-1190
  - The native app becomes `com.zankup.app`, with a `versionCode` above the React Native app's and the same key, so it installs over it as an update (you sign in once more: the React Native app's keystore data cannot be read). Zankup Next and its OAuth client and Firebase app are removed.
- **OS-1192 · Deprecating the React Native app** (S) · depends on OS-1191 and two weeks of daily use
  - `apps/mobile` and `apps/mobile-poc` removed with their Expo dependencies, scripts and CI jobs; `packages/core` keeps only what the web uses; README, `architecture.md` and this roadmap (phase 5 superseded by phase 11) updated.

**Suggested blocks** (one branch and one PR each): foundations (OS-1101 to OS-1105); session and navigation (OS-1110 to OS-1112); data (OS-1120 to OS-1123); the map and the charts (OS-1132, OS-1133), first and on their own, as they are the hardest and every later screen uses them; Home, the feed and the activity (OS-1130, OS-1131, OS-1134, OS-1135); Progress and stats (OS-1140 to OS-1142); health (OS-1150, OS-1151); You, the Coach and settings (OS-1160 to OS-1163); Android only (OS-1170, OS-1171); then quality, the check, the cutover and the deprecation. Once data and the charts are in, Progress, Health and You are independent of each other.

**Rough size:** 8 S, 18 M and 8 L, ≈ 50–80 working days (about 65); the map, the charts and the activity alone are about 12.

**M11 is done when** the native app has replaced the React Native one on your phone as `com.zankup.app`, every row of the screen map is there, it is at least as fast, and `apps/mobile` is gone.

---

## Backlog

Not planned in any phase. Each one comes back when its reason to wait goes away.

- **Route builder and sending routes to the watch** (L)
  - Draw a route on the map with elevation and surface, save it and export it as GPX or FIT course. Sending it straight to the watch depends on whether the Garmin Personal API accepts uploads; to be checked.
- **Structured workouts and training plans** (L)
  - Interval workouts by pace, HR or power, and multi-week plans built on the engine's estimates. Same open question about sending them to the watch.
- **3D route view** (M)
  - MapLibre with terrain elevation for the activity and replay (OS-414).
- **Error tracking with Sentry** (S)
  - Backend (FastAPI) and web (React) SDKs with data scrubbing and no IP addresses, in its own Sentry organisation (EU region). Comes back when structured logs and the sync-failure alert (OS-114) are no longer enough, for example once other people run their own instances.
- **Brand: apply the Zankup mark and icons** (#116)
  - Name, mark and wordmark are decided (see the [brand guide](brand/README.md)). What remains is implementation: the mark as a single source of truth in `packages/core`, PWA icons and manifest, Expo adaptive icon and splash, and removing the traced mountain mark from the prototypes. The product rename is a separate task (guide, section 7.2) that waits for the domain and trademark checks.

## Recommended starting order

1. **OS-D07 → OS-D08** (owner decisions), **OS-007** (rotate the key), then **OS-001**, **OS-002/OS-003**, **OS-008 → OS-010** and **OS-004 → OS-006** (foundations).
2. **OS-101 → OS-109**: the critical path for M1, testable without a UI.
3. **OS-501**: the React Native proof of concept, as soon as M1 serves real data.
4. **OS-110 → OS-112**, in parallel with **OS-201 → OS-202** and **OS-223** (web and i18n from the start).
5. The rest of phase 2 in number order, ending with **OS-224** (self-hosting kit).

## External dependencies and risks by phase

| Phase | Risk | Mitigation |
|---|---|---|
| All | The Garmin Personal API is for personal use only | Self-hosting with each person's own key: no shared key, no central service |
| 1 | Unpublished Personal API limits | Incremental sync, pauses and retries on 429. Measure in production |
| 1 | The key expires (1 year at most) | OS-403 and the date visible in settings (OS-212) |
| 2 | Vercel proxy limits | Poll progress every 2 s and paginate. Plan B: an `api.` subdomain |
| 2 | WebGL not available | Leaflet fallback, already tested in the prototype |
| 2 | Untranslated texts creeping in | Lint rule against hard-coded strings and a CI check for missing `es` keys (OS-223) |
| 2 | React Native performance on the activity detail | Proof of concept OS-501 with measurable criteria, before the PWA and the rest of the app |
| 2 | Self-hosting too hard for other people | Railway template, Docker Compose and a guide tested by someone else (OS-224) |
| 4 | Web Push on iOS only with the PWA installed | Document it. The phase 5 app is Android, but with Expo it could ship on iOS later |
| 5 | Play Store policy and health data | Internal testing and a sensitive data declaration |
| 6 | 7-day Google OAuth tokens in "Testing" mode and health scope verification | Test it first in OS-603. If not viable: Health Connect from Android (OS-607) |
| 6 | Duplicated or contradictory data between Garmin and the Fitbit Air | Configurable priority rules (OS-605) and the source always visible |
| 7 | The LLM invents numbers | Tools that return real data, evaluation with number checks (OS-707) |
| 7 | Health data sent to a third party | Minimisation (OS-702), no coordinates, global switch (OS-708) |
| 11 | The React Native app keeps moving while the native one is built | Fixes only there; every new feature added to the screen map (OS-1101) the same day |
| 11 | The map and the charts take far longer than planned | Built first and on their own (OS-1132, OS-1133), measured at 120 Hz before any screen uses them |
| 11 | Biometric keys behave differently on Samsung phones | The same rules as today (class 3 for the key, weak only as a lock), tested on the S26 in OS-1111 |
| 11 | The cutover loses the session or notifications | Same package and key, so it installs as an update; one sign-in, and the FCM token registered again on first launch |
