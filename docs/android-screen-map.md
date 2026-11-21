# Android screen map

The functional spec of the native Android app (phase 11, ticket OS-1101). It lists every screen of the React Native app in `apps/mobile` as it is on 18 Nov 2026: its route file, what it shows and in what order, the endpoints it calls, what can be done on it, and the native ticket that builds it.

**How it is kept up to date.** The React Native app stays in use until the cutover (OS-1191). Any feature it gains during phase 11 is added here the same day, in the screen's row, so the native app catches up with it. A fix that changes what a screen shows is written here too.

**How it is ticked.** During the parity check (OS-1190) both apps run side by side on the phone. A row is ticked (`- [x]`) when the native screen shows the same blocks in the same order, calls the same endpoints, and every interaction listed works, in English and Spanish and in every theme. A difference that stays is written under the row as `Differs:` with the reason, and the row is ticked.

Conventions:

- Routes are Expo Router files under `apps/mobile/app/`; components under `apps/mobile/src/`.
- Endpoints are the API's paths (`packages/core/src/api/openapi.json`), called through the query definitions in `packages/core/src/api/queries.ts` unless noted. `GET` reads come from the offline cache first (see the end).
- "Sheet" is a bottom panel (`src/progress/Panel.tsx` or `Sheet` in `src/detail/Tools.tsx`); "info" is the `(i)` button in the header that opens a short explanation.

---

## 1. Navigation

### Tab bar — `app/(tabs)/_layout.tsx` · OS-1112

- [ ] Five tabs, in this order, with Material Symbols icons (the filled variant on the tab you are on):

| #   | Route               | Label key      | Icon                                                          |
| --- | ------------------- | -------------- | ------------------------------------------------------------- |
| 1   | `(tabs)/index`      | `nav.home`     | `home`                                                        |
| 2   | `(tabs)/activities` | `nav.feed`     | `cards_stack`                                                 |
| 3   | `(tabs)/progress`   | `nav.progress` | `monitoring`                                                  |
| 4   | `(tabs)/health`     | `nav.health`   | `favorite`                                                    |
| 5   | `(tabs)/profile`    | `nav.you`      | `person`, or the profile photo (ringed in the accent when on) |

- [ ] Hidden: `(tabs)/goals.tsx` is registered with `href: null` (no tab, no link). The screen still exists.
- [ ] A tab opens at the top; pressing the tab you are on scrolls it up, animated (`src/scrollTop.ts`).
- [ ] Off-screen tabs do not redraw (`freezeOnBlur`). Tab bar: surface colour, 76 dp + gesture inset, active tint = accent; Strava themes draw inactive tabs in the text colour and the bar without a top border.
- [ ] Over the tabs, the **latest activity sheet** (`src/LatestActivity.tsx`, OS-1135): once per new activity (when the app opens or a sync brings one), a sheet from below says a new activity arrived, with its figures; fields for name, description, gear (and the workout type for strength); add photos; "OK" saves (`PATCH /api/activities/{activity_id}`), "Advanced editing" opens the activity. On the first run it only remembers the newest, so older ones never pop up. Data: `GET /api/activities`, `GET /api/activities/{activity_id}`, `GET /api/gear`.

### Stack — `app/_layout.tsx` · OS-1112

- [ ] Every screen below is a stacked route over the tabs, with the title shown in the table of each section. Header: page colour, no shadow, back arrow. `activity/[id]` and `heatmap` draw their own header.
- [ ] The offline notice (`src/OfflineNotice.tsx`) is over every signed-in screen.
- [ ] React Native has predictive back **off** (`predictiveBackGestureEnabled: false`); the native app turns it on (OS-1112).

### Home's header actions — `app/(tabs)/index.tsx` · OS-1112

- [ ] Left: the ZANKUP wordmark. Right, in order: **Coach** (`wand_stars`, opens `/coach`, only when `GET /api/ai/status` says `available`) and **Notifications** (`notifications`, opens `/notifications`). The header stays put while the widgets scroll.
- Note: OS-1112 lists "Sync, Coach, Search, Notifications" for Home. In the React Native app **Sync** (`sync`) and **Calendar** (`calendar_month`) are in Activities' header, and Search is Activities' search bar. Home has only Coach and Notifications.

### Deep links — `app.config.ts`, `src/links.ts`, `src/push.ts`, `src/widget/Latest.tsx` · OS-1112, OS-1122

- [ ] Scheme `zankup` (`zankup-dev` in Zankup Dev). Expo Router makes every route addressable (`zankup://activity/123`, `zankup://settings/heart`…); the one used is `zankup://activity/<id>` from the widget, and `zankup://` (Home) when the widget has no activity.
- [ ] A notification carries a **web path**; `screenFor()` maps it to a screen:
  - `/activities/<id>` → `/activity/<id>`
  - `/settings/import…` → `/import`
  - `/coach`, `/health`, `/sync`, `/gear` (also with `?` or `#`) → that screen
  - `/settings#<anchor>` → `/settings/<section>`, with `mcp → ai`, `zones → heart`, `garmin → account`, `sessions → account`, and each section name to itself; no anchor → `/settings`
  - `/profile…` → `/profile`; anything else → `/`
- [ ] Share links use the web origin: `api.zankup.com` → `zankup.com`; other instances as they are (`webOrigin`).

### Sign-in, lock and setup gate — `app/_layout.tsx`, `src/SignIn.tsx`, `src/signin/`, `src/session.ts` · OS-1110, OS-1111

- [ ] Opening the app: splash, then `unlock()` asks for the fingerprint to read the refresh token, refreshes it (`POST /api/auth/refresh` with `rotate: false`) and opens on what the cache keeps. Outcomes: **in**; **locked** (fingerprint cancelled or keystore error: the wordmark, `mobile.locked`, an "Unlock" button that asks again); **signed out** (no token, key invalidated because fingerprints changed, or refused token: sign-in).
- [ ] Sign-in steps (`SignIn.tsx`):
  1. **Instance**: URL field (default `https://api.zankup.com`), normalised (`https://` added, trailing `/` removed) and checked against `GET /api/health`. If `GET /api/setup` says the instance has no account, go to Setup.
  2. **Password**: "Continue with Google" when `GET /api/auth/google/providers` says so (native account sheet, `GET /api/auth/google/mobile/nonce` then `POST /api/auth/google/mobile`); "Passkey" (`POST /api/auth/passkeys/login/options`, then `POST /api/auth/passkeys/login`); "or"; e-mail and password (show/hide), "Forgot it?", "Sign in" (`POST /api/auth/login`); "Change instance". Validation: e-mail format, password not empty. Rate-limit and status messages as the PWA's (`src/signin/rules.ts`, `Retry-After`).
  3. **Code**: six boxes that submit themselves (`POST /api/auth/login/totp` with the challenge); "Back"; an expired challenge returns to step 2 with `signIn.error.expired`.
  4. **Forgot**: e-mail → `POST /api/auth/password/forgot`; then "sent" and "reset it on the web".
- [ ] **Setup** of a new instance (`src/signin/Setup.tsx`): setup code (from the server logs), name, e-mail, password (≥ 12 characters), accept switch → `POST /api/setup/account`; TOTP step with the key opened in the authenticator app and the code → `POST /api/setup/totp`; recovery codes to keep; then "Load the Garmin history" (`POST /api/sync` with `history`) or go straight to the app.
- [ ] Token storage: behind strong biometrics in the keystore; on phones with only weak biometrics the person can choose an app lock instead (Settings › Account › Security); without biometrics the token is stored without them. Signing out: `POST /api/auth/logout`, the token forgotten, the offline cache deleted.
- [ ] Signed in, the app also: registers push (`PUT /api/notifications/fcm`), schedules the background sync, refreshes the widget and reads Health Connect (at most hourly).

---

## 2. Home — OS-1130

### `app/(tabs)/index.tsx` — Home

- [ ] **Shows, in order** (under the header above):
  1. **This week** card (`WeekHeader` in `src/Feed.tsx`): the title and a streak badge (`week.streak`); seven day circles (Monday to Sunday) filled with the first activity's sport colour and icon, a dot for a second activity, today ringed in the accent, future days dimmed; the week's count, distance, time, ascent; closed by **vs. a usual week** (`UsualWeek`): time trained so far against the previous twelve weeks' average, as a ±% pill and a bar with the usual week marked.
  2. **Last day with activities** (`LastDay`): one card per activity of that day, swiped sideways (name, sport, distance/time, avg power or avg HR); then that day's steps, resting HR and HRV.
  3. **Metrics** (`Metrics`): tiles for VO₂ max (from VDOT, with a note), FTP (with W/kg from the profile weight), steps today as a ring against 10,000, training load as a gauge (form bands, with CTL · ATL).
  4. **Last 7 days** (`LastSevenDays`): totals by sport, then average steps, resting HR and HRV.
- [ ] **Endpoints:** `GET /api/week?today=`, `GET /api/activities` (feed, first page), `GET /api/stats/estimates`, `GET /api/stats/fitness`, `GET /api/wellness/days?start=&end=` (last week, one request shared), `GET /api/auth/me`, `GET /api/ai/status`.
- [ ] **Interactions:** a day circle with an activity opens it; an activity card opens `/activity/<id>` (the detail is prefetched on press-in: activity, context, streams); header buttons as above. Skeleton while the week loads (`WeekSkeleton`); "no activities yet" (`home.empty`).

---

## 3. Activities and the activity — OS-1131, OS-1132, OS-1133, OS-1134, OS-1135

### `app/(tabs)/activities.tsx` — Activities (feed and search) · OS-1131

- [ ] **Shows:** title `nav.feed` with **Sync** (`/sync`) and **Calendar** (`/calendar`) on the right; the search bar (`src/Search.tsx`) with a clear button and a filters button (`tune`, accent when filters are on); a row of sport chips (All, run, ride, strength, walk, row, swim, other); then the **feed** or the **results**.
- [ ] **Feed** (`src/Feed.tsx`, nothing typed or chosen): every activity newest first, 30 per page, loading more near the end. Card: sport icon, name, date; figures (distance + time for distance sports; time + avg HR otherwise); the route thumbnail (88 × 64, sport colour) or, for strength, the small body map; a "goal reached" capsule. Pull to refresh. Empty: `feed.empty`; skeleton: `FeedSkeleton`.
- [ ] **Results** (text, a sport or a filter): `GET /api/activities/search` with offset paging (50), a summary line of every match (`search.summary`: count and totals), cards as the feed; empty `search.none`.
- [ ] **Filters sheet:** period (all, this year, last 12 months); distance min/max (in km or mi); duration min/max; minimum ascent; switches "with a record", "with a description", "with a GPS track"; sort (date, distance, duration, elevation, speed) and order (descending, ascending). Footer: "Show N" (live count) and "Clear". Typing is debounced 300 ms.
- [ ] **Endpoints:** `GET /api/activities?limit=&cursor=`, `GET /api/activities/search`.
- [ ] **Interactions:** a card opens the activity (prefetched on press-in).

### `app/activity/[id].tsx` — The activity · OS-1134 (map OS-1132, charts OS-1133, editing OS-1135)

- [ ] **Header** (own, see-through over the map, fills in with the title as the map scrolls away): back; edit (`edit_square`, opens Edit); share menu (`share`: "Image", "Link").
- [ ] **Cover:** the map (300 dp under the header) when there is GPS; for a strength workout without GPS, the large body map with the muscles named (`MuscleCover`); otherwise none.
  - Map (`src/detail/RouteMap.tsx`): the route coloured by a metric (2–98 % range), a dot following any chart's cursor, the selected section highlighted. Controls (`MapControls`): play/pause bottom left; options top right opening a panel with **colour by** (speed or pace, HR, power, altitude — those the series have) with its legend (pace reversed: fast first) and **replay speed** 10×, 30×, 100×.
- [ ] **Title block:** sport icon, name (or the sport's), date · workout type · device · gear; a meta line with weather (temperature, wind…).
- [ ] **Figures:** six main figures in two centred columns; "More"/"Less" unfolds the rest (`activityFigures` in core).
- [ ] **Goal reached** banner (opens `/objectives`), then the **AI summary** card (`SummaryCard` in `src/Ai.tsx`): folded/unfolded, "Write it" when there is none, "Tell me more" opening the longer report in a sheet (sections and "next session").
- [ ] **Blocks by sport, in order:**
  - **Ride:** Fitness (→ `/fitness`) · Matched activities (→ `/matches?id=`) · Relative effort (→ `/effort`) · Goals (→ `/objectives`) · Results (medals and segment places, → `/bests`, each medal → `/bests?key=`) · "Profile" heading with the **Section** switch · power curve (this activity against all time) · power chart (+ work kJ, intensity, load) · power zones · HR chart · HR zones · speed chart · cadence chart · elevation chart · section card · photos · route and segments · laps. No splits.
  - **Run:** Fitness · Matched activities · Relative effort · Goals · Results · **Training analysis** (split bars over the elevation, average and fastest split, → `/analysis/<id>`) · splits table (distance, time, pace, elevation, HR) · "Profile" + Section · pace chart (+ moving, elapsed, fastest split) · GAP chart · pace zones · HR chart · HR zones · cadence · elevation · section card · photos · route and segments · laps.
  - **Strength:** Exercises at a glance (each once with sets × reps, weight) · Relative effort · Goals · "Profile" + Section · HR chart · HR zones · section card · the full sets (`src/detail/Strength.tsx`: totals sets/reps/volume/work/rest, the session's flow, each exercise's sets with reps bar, weight or bodyweight, time, rest, and the body map unless it is the cover) · photos · laps.
  - **Other sports** (walk, row, swim, other): "Profile" + Section with every chart the series have · section card · photos · HR zones · sets if any · route and segments · power · laps · splits.
  - Then for all: the **description** card and **best efforts** (distance, time, pace).
- [ ] **Charts** (`src/detail/MetricChart.tsx`): one card per series, the elevation in grey behind power, HR, speed/pace, GAP and cadence; distance axis in km or mi; a cursor that follows the finger with a readout, its own per card, and the map's dot follows. Charts mount after the screen has slid in (≈ 450 ms).
- [ ] **Section:** with the switch on, dragging on a chart selects a range shared by all charts and the map; the section card shows distance, time, pace/speed, ascent, avg HR, and (with GPS) "Save as segment" → name → `POST /api/segments`. Turning the switch off clears it.
- [ ] **Photos** (`Photos` in `src/detail/Tools.tsx`): thumbnails, each opening full size in a sheet.
- [ ] **Edit sheet** (`EditActivity`): name, description, sport, gear (or none), workout type (strength), photos (add from the photo picker: multipart `POST /api/activities/{activity_id}/photos`; remove with a confirm: `DELETE /api/photos/{photo_id}`); "Save" (`PATCH /api/activities/{activity_id}`), "Cancel"; "Delete activity" with a confirm (`DELETE /api/activities/{activity_id}`).
- [ ] **Share:** "Link" → `POST /api/activities/{activity_id}/shares` (`days: 7, show_map: true`) and the system share sheet with name + web link; "Image" → `GET /api/activities/{activity_id}/shared?show_map=true` drawn on a 1080 × 1350 card in the theme's colours (`src/share/card.ts`), shared as PNG.
- [ ] **Endpoints:** `GET /api/activities/{activity_id}`, `GET /api/activities/{activity_id}/context` (loaded beside it), `GET /api/activities/{activity_id}/streams` (kept on the phone), `GET /api/stats/curves?kind=power` (all-time curve), `GET /api/activities/{activity_id}/summary`, `POST /api/activities/{activity_id}/summary`, `GET /api/activities/{activity_id}/report`, `POST /api/activities/{activity_id}/report`, `GET /api/ai/status`, `GET /api/gear`, plus the writes above.
- [ ] **States:** `ActivitySkeleton` while loading; `activity.notFound` on error; zones "none" text when there is no max HR.

### `app/analysis/[id].tsx` — Training analysis · OS-1134

- [ ] **Shows:** the splits chart large (bars as wide as each split, taller the faster, over the elevation, pace axis on the left, split numbers under it, the average dashed); the list of splits (distance, time, pace), the picked one highlighted.
- [ ] **Endpoints:** `GET /api/activities/{activity_id}`, `GET /api/activities/{activity_id}/streams`.
- [ ] **Interactions:** a finger along the chart picks a split (chart and list together); the first split is picked at open.

### `app/matches.tsx` — Matched activities (`?id=`) · OS-1134

- [ ] **Shows:** a line of pace or speed over time of every effort on the same route, the opened one marked; the list by year: date, pace/speed, time, relative effort.
- [ ] **Endpoints:** `GET /api/activities/{activity_id}`, `GET /api/activities/{activity_id}/context`.
- [ ] **Interactions:** a row opens its activity.

---

## 4. Progress — OS-1140

### `app/(tabs)/progress.tsx` — Progress tab

- [ ] **Shows, in order** (each card opens its page):
  1. **Volume** (`VolumeCard`): sport chips, this week's totals (or the week touched on the chart), the last 12 weeks of the sport's main measure → `/progress-detail?sport=`. Endpoint `GET /api/stats/volume`.
  2. **Series** and **Training log** side by side. Series (`Series.tsx`): weeks in a row with an activity, in a flame; tap opens the **Series panel** (sheet): the month with each day's sport (a day opens its activity) and the streak band per week. Log (`Log.tsx`): this week and last as rows of dots with their time → `/training-log`. Endpoints `GET /api/stats/streak`, `GET /api/stats/calendar`.
  3. **Fitness** (`FitnessCard`): today's score, its change over the month, a sparkline → `/fitness`. `GET /api/stats/fitness`.
  4. **Relative effort** (`EffortCard`): this week's score against the range of the three weeks before, last week's, both weeks built up day by day → `/effort`. `GET /api/stats/volume` (unit `week` and `day`).
  5. **Race predictions** (`Predictions.tsx`): the 5 km prediction and its last month → `/predictions`. `GET /api/stats/estimates`.
  6. **Goals** (`GoalsCard`): up to three goals as rings → `/objectives`. `GET /api/goals`.
  7. **Training zones** (`ZonesCard`): where most of the last month's HR time went, a bar per zone → `/zones`. `GET /api/stats/zones`.
  8. **Skills** (`Skills.tsx`): the strongest skill beside the radars (last 8 weeks over all time) → `/skills`. `GET /api/stats/curves?kind=power`, `GET /api/auth/me`.
  9. **Best times** (`BestsCard`): the medals of the last 12 months (up to three) → `/bests`. `GET /api/stats/bests/recent`.
  10. **Monthly activities** (`Monthly.tsx`): this month's time adding up day by day over last month's → `/monthly`. `GET /api/stats/calendar`.
  11. **Data sources** (`DataSources.tsx`): devices and services that recorded in the last 30 days, last used → `/sources`. `GET /api/stats/devices?days=30`.

### `app/progress-detail.tsx` — Progress (detail of Volume)

- [ ] **Shows:** sport (sheet with search, `SportSheet`), measure (time, distance, elevation, activities — those the sport has), the range chips 7d, 1m, 3m (default), 6m, YTD, 1y; the total against the span before or against the same span some years back ("Compare" sheet); the chart (`VolumeChart`, previous span as a second line); a sentence on how it went. Info button.
- [ ] **Endpoints:** `GET /api/stats/volume` (current and compared span), `GET /api/stats/streak` (first activity), `GET /api/stats/calendar` (the picked bucket).
- [ ] **Interactions:** touch or slide on the chart picks a day/week (haptic click on points); its box opens a sheet with what was done in it; an activity opens it.

### `app/training-log.tsx` — Training log

- [ ] **Shows:** every week from this one back to the first activity, a bubble per day sized and labelled by time, distance or elevation, coloured by sport (grey when several).
- [ ] **Endpoints:** `GET /api/stats/streak`, `GET /api/stats/calendar` (eight weeks at a time as they scroll into view).
- [ ] **Interactions:** header: filters (sheet: sport, what bubbles show), go to month (panel from the right with the months), search (opens the Activities tab). A bubble opens its activity, or a sheet listing the day's activities.

### `app/fitness.tsx` — Training (fitness)

- [ ] **Shows:** intro; ranges 1m (default, remembered), 3m, 6m, 1y, 2y; the fitness chart (filled line, load circles on short ranges, the picked day's badge); change from the range's first day to the picked day; the picked day's activities (folded list). Info button.
- [ ] **Endpoints:** `GET /api/stats/fitness?from=&to=`, `GET /api/stats/calendar` (the picked day).
- [ ] **Interactions:** touch/slide picks a day (haptic click on days with load); a vertical swipe still scrolls; an activity opens it.

### `app/effort.tsx` — Relative effort

- [ ] **Shows:** the week on a panel in its status colour (score, meaning, days as bars); the weeks around it with the range each was judged against; the week's activities with their effort. Info button.
- [ ] **Endpoints:** `GET /api/stats/volume` (weeks and days), `GET /api/stats/calendar` (the week's activities).
- [ ] **Interactions:** swipe the panel, the arrows (previous/next week) or touch the chart to change week (haptic click); an activity opens it.

### `app/predictions.tsx` — Race predictions

- [ ] **Shows:** "Today": the four races (5 km, 10 km, half, marathon) with time, pace and 30-day change; "1m / 3m / 6m": one race (chips) with its line (faster is higher). The range chips sit at the bottom, always visible. Info button.
- [ ] **Endpoints:** `GET /api/stats/estimates`.

### `app/objectives.tsx` — Goals

- [ ] **Shows:** each goal with its ring, so far / to go / extra, behind or ahead of an even pace (months, years); a fold with the period's totals and its chart. Empty: `progress.noGoals`. "+" in the header.
- [ ] **Endpoints:** `GET /api/goals`, `GET /api/stats/volume` (per open goal), `DELETE /api/goals/{goal_id}`.
- [ ] **Interactions:** "+" → `/objective-new`; a goal's menu (sheet): edit → `/objective-new?id=`, delete with a confirm; the fold is remembered per goal.

### `app/objective-new.tsx` — New goal / edit goal (`?id=`)

- [ ] **Shows:** sport (all, or one in a sheet); period week / month / year; measure (kinds already set for that sport and period greyed out; strength has no distance); target in its units (km or mi, hours + minutes, metres or feet, activities).
- [ ] **Endpoints:** `GET /api/goals`, `POST /api/goals`, `PATCH /api/goals/{goal_id}`.
- [ ] **Interactions:** save returns to Goals; an error message on failure.

### `app/zones.tsx` — Training zones

- [ ] **Shows:** kind HR / pace / power, range chips (1m default), sport (all or one, sheet); the zone with most time and its share against the span before; the zone rows; a sentence on what changed most; notices when there is no threshold or FTP. Header: info and pencil.
- [ ] **Endpoints:** `GET /api/stats/zones?kind=&from=&to=&sport=` (current and previous span).
- [ ] **Interactions:** pencil → `/settings/heart`.

### `app/skills.tsx` — Skills

- [ ] **Shows:** the radar of best power over twelve durations (last 8 weeks over all time, or all time alone); sprint, attack and climb with their level on a nine-step bar; a note when there is no weight. Info button.
- [ ] **Endpoints:** `GET /api/stats/curves?kind=power` (both spans), `GET /api/auth/me` (weight).
- [ ] **Interactions:** span switch; a skill opens to its durations (watts and bar each); a radar point opens a sheet.

### `app/bests.tsx` — Best times (`?key=`, default `10k`)

- [ ] **Shows:** title = the measure; measure chips (run distances, or a ride's power durations and distance); tabs **Analysis** (record, 2nd and 3rd; a chart of bests with trend, twelve at a time, scrolled sideways; the bests by year with each year's medal) and **Top 10**. Info button.
- [ ] **Endpoints:** `GET /api/stats/bests?key=`.
- [ ] **Interactions:** sport sheet (run/ride), year sheet; chart and list move together; a best opens its activity. Imperial hides metric-only distances.

### `app/monthly.tsx` — Monthly activities

- [ ] **Shows:** this month against last for all sports, then each sport done in either, with time, distance, elevation and count.
- [ ] **Endpoints:** `GET /api/stats/calendar`.

### `app/sources.tsx` — Apps and devices

- [ ] **Shows:** groups "Devices" (Garmin Connect, connected or not; Import), "Tools" (AI assistants), "Services" (Google Health, Health Connect), each with a note.
- [ ] **Endpoints:** `GET /api/sources`, `GET /api/wellness/settings`.
- [ ] **Interactions:** Garmin → `/settings/account`; Import → `/import`; assistants → `/assistants`; Google Health and Health Connect → `/settings/health`.

### `app/(tabs)/goals.tsx` — Goals (hidden tab, unlinked) · OS-1140

- [ ] **Shows:** each goal with progress and end-of-period projection (`Goals` in `src/profile/Sections.tsx`). Header: pencil (edit mode: each goal can be changed or deleted; the pencil becomes a tick) and "+" (the goal form in a sheet).
- [ ] **Endpoints:** `GET /api/goals`, `POST /api/goals`, `PATCH /api/goals/{goal_id}`, `DELETE /api/goals/{goal_id}`.
- Note: no link reaches it; `objectives` replaced it. Decide in OS-1140 whether to drop it (then tick with `Differs:`).

---

## 5. Stats and reviews — OS-1141, OS-1142

### `app/stats.tsx` — Stats (from You)

- [ ] **Shows:** toolbar: period last 12 months / year (arrows, dimmed at the ends of the history) / months (from–to, each with arrows, default last month to this one) / all time. Header right: **Reviews** menu (`summarize`: this month, this year) and **Heatmap** (`layers`). While the engine recomputes: "Recomputing N / M" (polled every 3 s). Then, in order:
  1. **Summary** against the year before (`GET /api/stats/summary`).
  2. **Fitness and form** (`GET /api/stats/fitness`).
  3. **Volume** by sport (`GET /api/stats/volume`, unit by period).
  4. **Aerobic efficiency** (`GET /api/stats/efficiency`).
  5. **Best-effort curves**, power or speed (run) against all time (`GET /api/stats/curves`).
  6. **Records** of the period, each opening `/bests?key=`; a link to `/bests`.
  7. **More** links: Routes (`/routes`), Segments (`/segments`), Year by year (`/years`).
- [ ] **Endpoints:** also `GET /api/stats/years`, `GET /api/engine`.
- [ ] **Interactions:** activity links open the activity.

### `app/estimates.tsx` — Estimates (from You)

- [ ] **Shows:** VDOT, FTP, critical speed, threshold HR, each with value, a history chart and the activities it comes from (dates linking to them); the race predictions with their method. Empty: `stats.estimatesEmpty`.
- [ ] **Endpoints:** `GET /api/stats/estimates`.

### `app/years.tsx` — Year by year

- [ ] **Shows:** each year's distance cumulative through the year, one line per year, filling the height above the years' list. Info button. Not scrolled.
- [ ] **Endpoints:** `GET /api/stats/progress`.
- [ ] **Interactions:** a year in the list stands out (this year by default).

### `app/calendar.tsx` — Calendar (from Activities)

- [ ] **Shows:** month title with arrows; weekday labels; weeks of days, each day with its number, its form (TSB, coloured ±) and a round sport icon per activity sized by effort; each week's totals at its foot.
- [ ] **Endpoints:** `GET /api/stats/calendar?from=&to=`.
- [ ] **Interactions:** arrows change month; a bubble opens the activity; tapping two days sums the days between them (`calendar.selection`).

### `app/heatmap.tsx` — Heatmap (from Stats)

- [ ] **Shows:** full-screen map with every GPS route; see-through header (back on a round background, title on the map); sport chips (All + sports); the number of routes at the bottom. Loading skeleton; `heatmap.note` when there is nothing.
- [ ] **Endpoints:** `GET /api/stats/heatmap?sport=`.

### `app/review/[period].tsx` — Month / year in review (`month` or `year`)

- [ ] **Shows:** period switch (month/year) with arrows; totals (activities, distance, time, ascent) against the previous period with their bars; consistency (active days, longest streak) and sports; longest, hardest and hilliest activity; records (with the time beaten); gear with its count. Empty: `review.empty`.
- [ ] **Endpoints:** `GET /api/stats/review?period=&value=`.
- [ ] **Interactions:** arrows move between periods; activities and records open the activity.

### `app/routes/index.tsx` — Routes (from Stats › More) · OS-1142

- [ ] **Shows:** routes done more than once: name, times done, distance, best time; skeleton first, list drawn once settled, only rows in sight. Empty: `routes.empty`.
- [ ] **Endpoints:** `GET /api/routes`.
- [ ] **Interactions:** a route → `/routes/<id>`.

### `app/routes/[id].tsx` — One route · OS-1142

- [ ] **Shows:** times done; the route on a map in the sport colour; every time you did it, the fastest marked.
- [ ] **Endpoints:** `GET /api/routes/{group_id}`.
- [ ] **Interactions:** a row opens the activity.

### `app/segments/index.tsx` — Segments (from Stats › More) · OS-1142

- [ ] **Shows:** your segments: name, distance, efforts, best time. Empty: `segments.empty`.
- [ ] **Endpoints:** `GET /api/segments`.
- [ ] **Interactions:** a segment → `/segments/<id>`.

### `app/segments/[id].tsx` — One segment · OS-1142

- [ ] **Shows:** efforts count; the line on a map (260 dp, accent); history of efforts.
- [ ] **Endpoints:** `GET /api/segments/{segment_id}`, `DELETE /api/segments/{segment_id}`.
- [ ] **Interactions:** an effort opens its activity; "Delete segment" with a confirm, then back.

---

## 6. Health — OS-1150, OS-1151

### `app/(tabs)/health.tsx` — Health tab · OS-1150

- [ ] **Header right:** sync (spinning icon while it runs, `POST /api/wellness/sync`) and settings (→ `/settings/health`). Toolbar: period 30 / 90 / 365 days.
- [ ] **Shows, in order:**
  1. **Last night** (or "Night of …"): the hypnogram as stage lanes, asleep from–to, the source icon (and "also from" others).
  2. **Today / Yesterday / day**: Readiness (Zankup mark) with its meaning, then tiles HRV, resting HR, SpO₂, respiration, steps, each with its source in the corner.
  3. **Weight** card: latest weight and change → `/weight`.
  4. **Sleep** trend chart (with score).
  5. **Heart** trend: HRV (ms) and resting HR (bpm).
  6. **Readiness and form**: readiness and the training form (TSB) over the period; info.
- [ ] **Endpoints:** `GET /api/wellness/days?start=&end=`, `GET /api/wellness/nights/{day}`, `GET /api/weight`, `POST /api/wellness/sync`.
- [ ] **States:** `HealthSkeleton`; error text; no data: `health.none` with "Connect" (→ `/settings`).

### `app/weight.tsx` — Weight (from Health) · OS-1150

- [ ] **Shows:** a line through the weighings over 1m, 3m (default), 6m, 1y, all; every weighing below, newest first. "+" in the header.
- [ ] **Endpoints:** `GET /api/weight`, `PUT /api/weight`, `DELETE /api/weight/{day}`.
- [ ] **Interactions:** "+" adds one (a second on the same day replaces it); a weighing opens to be changed (day and weight); a long press deletes with a confirm. The latest is the profile's weight.

---

## 7. You and the profile — OS-1160

### `app/(tabs)/profile.tsx` — You

- [ ] **Shows, in order:** the header (photo or initials, name or e-mail, location, edit pencil); a menu card: Stats (`bar_chart`, `/stats`), Estimates (`speed`, `/estimates`), Coach (`wand_stars`, `/coach`, only when AI is available), Gear (`footprint`, `/gear`), Import (`upload`, `/import`), Account (`account_circle`, `/settings/account`), Settings (`settings`, `/settings`); then the **summary**: this year by sport (count, distance, time), all time by sport with "since", records (each opening its activity), physical data (age, sex, weight, height…).
- [ ] **Endpoints:** `GET /api/auth/me`, `GET /api/profile/summary`, `GET /api/ai/status`, `PATCH /api/auth/me`.
- [ ] **Interactions:** the pencil opens **Edit profile** (sheet): name, location, birth date (validated format), sex (none, female, male, other), weight, height; Save / Cancel.

---

## 8. Settings — OS-1161

### `app/settings/index.tsx` — Settings

- [ ] **Shows:** one row per section with icon and note: General (`tune`), Heart rate (`ecg_heart`), Notifications (`notifications`), AI assistant (`wand_stars`), Privacy (`location_off`), Health (`health_and_safety`), Your data (`database`), Account (`lock`); a separate card: App icon (`palette`, → `/app-icon`, OS-1171).
- [ ] **Interactions:** a row → `/settings/<section>`.

### `app/settings/[section].tsx` — One section (title = section's; unknown section falls back to General)

- [ ] **general:** Language (Automatic, English, Español; saved on the phone and `PATCH /api/settings {language}`; "restart to apply" when it differs) · Units (metric, imperial; `PATCH /api/settings {units}`) · Appearance: theme picker with a preview card per theme (stored on the phone).
- [ ] **heart:** Heart rate: max HR (placeholder = the engine's estimate) and resting HR, Save (`PATCH /api/settings`) · Zones: model (max, reserve, LTHR), threshold HR and FTP saved on blur (estimates as placeholders), "Recompute" (`POST /api/engine/recompute`, progress from `GET /api/engine`). Uses `GET /api/settings`, `GET /api/stats/estimates`.
- [ ] **notifications:** This phone on/off (`PUT /api/notifications/fcm` with the token or `null`), "Send a test" (`POST /api/notifications/test`); which kinds: activity, record, sync_error, garmin_key, estimate, import, weekly (`PATCH /api/settings`) · Alerts card: e-mail for alerts (`GET/PATCH /api/instance`) and Resend's key.
- [ ] **ai:** Anthropic key (`GET /api/keys`, `PUT /api/keys/{name}`, `DELETE /api/keys/{name}`; only the last characters shown) · Zankup Coach (only with a key): model with price, monthly budget, Monday's hour in local time (`PATCH /api/instance`) · AI card: enabled, summaries, weekly report switches (`PATCH /api/settings {ai}`), budget exceeded note (`GET /api/ai/status`) · MCP: "Connect an assistant" (→ `/assistants`), the MCP URL, token name + "Create" (`POST /api/ai/tokens`, shown once, copy and share), list with last used and "Revoke" with a confirm (`GET /api/ai/tokens`, `DELETE /api/ai/tokens/{token_id}`).
- [ ] **privacy:** a map (tap to place a zone; existing zones drawn as circles), name, radius 200 / 400 / 800 / 1000 / 1600 m, "Add"; the list with delete. `GET /api/settings`, `GET /api/activities` (to frame the map), `PATCH /api/settings {privacy_zones}`.
- [ ] **health:** Bring the year's history (`POST /api/wellness/history`, then a Health Connect history read on this phone) · Health Connect card (OS-1151): "Connect" (asks the permissions, reads and sends), "Read now", last read time, "unavailable" note · Google Health switch (`PATCH /api/instance`) · Health sources: each source's state, "Disconnect Google Health" (`DELETE /api/wellness/google`), "Delete its data" with a confirm (`DELETE /api/wellness/sources/{source}/data`), preferred source per metric with "Automatic" (`PUT /api/wellness/priority`). `GET /api/wellness/settings`. Connecting Google Health needs the web.
- [ ] **data:** rows Import (→ `/import`) and Gear (→ `/gear`) · Export: "Prepare" (`POST /api/exports`), "preparing", "Download (N MB)" opening the download URL in the browser, valid until (`GET /api/exports`) · Backups: hour, days kept, S3 key (`PATCH /api/instance`, `PUT/DELETE /api/keys/{name}`).
- [ ] **account:** Sign-in methods (password + TOTP on/off; Google linked → "Unlink" `DELETE /api/auth/google`, 409 needs another method; not linked → "link on the web"; shown when `GET /api/auth/google/providers` offers Google) · Passkeys: list with last used, "Remove" with a confirm (`GET /api/auth/passkeys`, `DELETE /api/auth/passkeys/{passkey_id}`), "Add a passkey" (`POST /api/auth/passkeys/register/options`, `POST /api/auth/passkeys/register`) · Security: biometric type note, "Biometric lock" switch (re-stores the token behind the fingerprint; reverts if cancelled), "Weak biometrics as app lock" when only weak ones exist, the instance URL · Garmin: key expiry warning, last sync / last error, sync interval (off, minutes, hours; `PATCH /api/instance`), Garmin key (`PUT/DELETE /api/keys/garmin`); `GET /api/sources` · Sessions: devices with last seen, "this device" badge, "Sign out" per device with a confirm (`GET /api/auth/devices`, `DELETE /api/auth/devices/{device_id}`) · "Sign out" button.

### `app/app-icon.tsx` — App icon · OS-1171

- [ ] **Shows:** heading, intro, a grid of the Zankup mark on its colours, the one in use ringed, a note that the launcher takes a few seconds. Variants (`plugins/appIcons.json`): default, mint, black, white, night, blue, purple, red, orange, flame, pride.
- [ ] **Endpoints:** none.
- [ ] **Interactions:** a tap switches the launcher icon (activity alias).

---

## 9. Coach and AI — OS-1162

### `app/coach.tsx` — Coach

- [ ] **Shows:** when AI is off or unavailable: a message and (if available) "Settings". Otherwise a chat: header info (disclaimer) and "Clear"; before the first question, the **weekly report** card when weekly reports are on (week, summary, "More/Less", recommendation, "Generate" → `POST /api/ai/weekly`; pending / failed states) and the suggestions; the turns, streamed as they are written, with "working" labels while tools run; budget-exceeded notice; the input with Send / Stop.
- [ ] **Endpoints:** `GET /api/ai/status`, `GET /api/ai/weekly`, `POST /api/ai/weekly`, `POST /api/ai/coach` (NDJSON stream of `text`, `tool`, `error` events; on 401 the token is refreshed and the request sent again).
- [ ] **Interactions:** a suggestion asks it; activity links in an answer open `/activity/<id>`; Stop aborts. The conversation lives only in the screen.

### `app/assistants.tsx` — AI assistants · OS-1162

- [ ] **Shows:** intro; Claude: the MCP address with "Copy" and "Open Claude" (`https://claude.ai/settings/connectors` in the browser); other assistants with "Personal tokens" (→ `/settings/ai`); what they can read and how to disconnect.
- [ ] **Endpoints:** none (the instance URL builds the address).

Also in this area: the activity's AI summary and report (section 3), the AI and MCP settings (section 8).

---

## 10. Gear, import, sync and notifications — OS-1163

### `app/gear.tsx` — Gear

- [ ] **Shows:** help; each shoe or bike with badges (habitual, retired), mileage against its limit as a bar, "over limit" warning, "Edit". "+" in the header. Empty: `gear.empty`.
- [ ] **Endpoints:** `GET /api/gear`, `POST /api/gear`, `PATCH /api/gear/{gear_id}`, `DELETE /api/gear/{gear_id}`.
- [ ] **Interactions:** the form (sheet): name, kind (shoe, bike), brand, model, default for sports, habitual, initial km, limit km; Save; Retire / Unretire; Delete with a confirm.

### `app/import.tsx` — Import

- [ ] **Shows:** help (Strava export, Garmin export, FIT/TCX/GPX files); "Choose files"; upload progress; imports history with counts and problems (each opens its details); after a Strava export, the **comparison with Strava** (`CompareWithStrava`).
- [ ] **Endpoints:** `POST /api/imports`, then each part `PUT /api/direct/uploads/{import_id}/{index}?token=` (outside the OpenAPI document, raw bytes), `POST /api/imports/{import_id}/finish`; `GET /api/imports`; `GET /api/stats/strava-comparison`.
- [ ] **Interactions:** Android's file picker (several files, FIT/TCX/GPX/ZIP); retries a failed part.

### `app/sync.tsx` — Sync (from Settings › Your data)

- [ ] **Shows:** "syncs automatically" note; "Sync now"; the current run's state (queued, running, with a progress bar of windows); "Whole history" with help and "Load history"; "Recent runs" (folded list with status, counts, errors).
- [ ] **Endpoints:** `GET /api/sync?limit=10` (every 2 s while a run is queued or running), `POST /api/sync {history}`. When a run ends, the feed and stats are refreshed.

### `app/notifications.tsx` — Notifications (from Home)

- [ ] **Shows:** what the engine noticed lately (new record, estimate up, risky load jump…), worded as the notification. Empty: `notifications.empty`.
- [ ] **Endpoints:** `GET /api/events`.
- [ ] **Interactions:** an event with an activity opens it.

---

## 11. What only Android has — OS-1121, OS-1122, OS-1151, OS-1170, OS-1171, OS-1133, OS-1120

- [ ] **Widget** (`src/widget/`, OS-1170): 4 × 2 cells (min 250 × 110 dp), "Your latest activity and today's form"; drawn from a snapshot kept on the phone (works offline); refreshed when the app opens, by the background sync and every 30 min by the system. Data: `GET /api/activities?limit=1`, `GET /api/stats/fitness`. Tap opens `zankup://activity/<id>`. Figures are formatted metric regardless of the units setting.
- [ ] **App icons** (OS-1171): see `app/app-icon.tsx`. Adaptive icon (`#20c997` background) with a monochrome layer for themed icons.
- [ ] **Haptics** (`modules/haptic-click`, OS-1133): a click when the finger crosses a point with a ring on `VolumeChart` (Progress volume and detail), a day with load on `FitnessChart`, and a week on the effort chart. The activity's charts have no haptics in the React Native app.
- [ ] **Background sync** (`src/background.ts`, OS-1121): WorkManager every ~30 min: refresh token (`POST /api/auth/refresh`), feed first page, this week, the latest activities' series (`GET /api/activities/{activity_id}/streams`), persisted cache, widget. Only when the token can be read without the fingerprint (weak-biometric lock or no biometrics); otherwise it waits for the next opening.
- [ ] **Push** (`src/push.ts`, OS-1122): FCM token registered per device (`PUT /api/notifications/fcm`), unregistered with `null`; one Android channel `default`, accent `#20c997`; a tap opens `screenFor(url)`. Roadmap asks for one channel per kind in the native app.
- [ ] **Health Connect** (`src/healthConnect.ts`, OS-1151): read-only permissions sleep, resting HR, HRV, SpO₂, respiratory rate, steps, plus history beyond 30 days; reads what is new since the last time with two days of overlap and sends it (`POST /api/wellness/health-connect`); on the button and, once allowed, on each opening at most hourly. Nothing is written.
- [ ] **Offline** (`src/offline.ts`, OS-1120): see the cross-cutting rules below.
- Not in the React Native app, new in the native one: app shortcuts and the Quick Settings tile (OS-1171), Glance (OS-1170), Credential Manager for Google and passkeys (OS-1110).

---

## 12. Cross-cutting behaviours

- [ ] **Themes** (`packages/core/src/tokens`, `src/theme.ts`, `src/settings/Themes.tsx`; OS-1104): System (light or dark from the phone, with Android's dynamic accent — Material You), Light, Dark, OLED Black, High contrast, Forest, Ocean, Sunset, Strava, Strava dark. The choice is kept on the phone. Strava themes: borderless cards, inactive tabs in the text colour. Sport colours and zone colours (zone1…zone5) per theme; form positive/negative colours.
- [ ] **Languages** (`src/i18n.ts`; OS-1105): English and Spanish, the same catalogues as the PWA; Automatic follows the phone, English when neither; a change applies on the next start. Dates and numbers by locale.
- [ ] **Units** (OS-1123): metric or imperial from `GET /api/settings`; distances km/mi, elevation m/ft, pace /km or /mi, speed km/h or mph; run sports use pace, others speed (`usesPace`). Max HR for zones: the setting, else the engine's estimate (marked as estimated).
- [ ] **Offline** (OS-1120): every query seen is persisted for 7 days (SQLite), except `keys` and `instance`; activity series in their own table, the latest 60 kept; screens show what is kept and refresh behind. When the server cannot be reached (no coverage, 429 or 5xx) a pill above the tab bar says so until a request goes through; it never takes a touch. Signing out deletes everything kept.
- [ ] **Pull to refresh:** only on the Activities feed. Other screens refresh on open and after a sync.
- [ ] **Skeletons** (`src/Skeleton.tsx`): pulsing bones — `PageSkeleton` (generic loading), `FeedSkeleton`, `WeekSkeleton`, `ActivitySkeleton`, `ChartSkeleton`, `HealthSkeleton`, `GoalsSkeleton`, `RoutesSkeleton`, `CardSkeleton`.
- [ ] **Empty and error states:** `Empty` (centred muted text) on lists; errors as a muted line with `accessibilityRole="alert"` (`common.error`), keeping what was shown; destructive actions always confirm with a system alert (Cancel / the action in red).
- [ ] **Info buttons:** the `(i)` in the header of progress-detail, fitness, effort, predictions, zones, skills, bests, years and the Coach.
- [ ] **Scrolling:** tabs start at the top and scroll up when pressed again; chart touches pick values sideways while a vertical swipe still scrolls; new values slide into place when a filter changes.
- [ ] **Prefetch:** pressing an activity card (feed, Home) starts loading the activity, its context and its series.
- [ ] **Accessibility:** every icon button has a label; charts carry a spoken summary; switches and folds expose their state (OS-1181).

---

## Notes found while mapping

- OS-1112's Home header ("Sync, Coach, Search, Notifications") does not match the React Native app: Home has Coach (only with AI) and Notifications; Sync and Calendar are on Activities.
- `(tabs)/goals.tsx` is hidden and unlinked; `objectives.tsx` replaced it.
- `src/Ai.tsx`'s header comment says the Coach lives in the PWA; `app/coach.tsx` has it on the phone. `app/sync.tsx` calls itself "the Sync tab"; it is a stacked screen.
- Theme is stored only on the phone; language and units also go to the server.
- The widget formats in metric whatever the units setting.
- The import's part upload (`/api/direct/uploads/{import_id}/{index}`) is not in `openapi.json`; the generated client (OS-1103) will not have it.
