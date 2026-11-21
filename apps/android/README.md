# apps/android

The native Android app (phase 11): Kotlin and Jetpack Compose, written from scratch. It shares nothing
with the rest of the repository but the **API** (its OpenAPI document is the contract) and the **screen
structure** of the React Native app, written down in [docs/android-screen-map.md](../../docs/android-screen-map.md).
The decision and the architecture are in [docs/architecture.md](../../docs/architecture.md#android-app-phase-11-native-kotlin--compose);
the plan is phase 11 of the [roadmap](../../docs/roadmap.md).

| Module              | What                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| `app`               | The application: the activity, its screens and, in debug builds, the component catalogue                     |
| `core:designsystem` | Themes, typography, shapes, spacing, icons, the brand and the shared components, with their texts            |
| `core:network`      | The API client generated from `packages/core/src/api/openapi.json`, the session, results and NDJSON          |
| `core:domain`       | Plain Kotlin: formatting by units and locale, zones, splits, grade-adjusted pace, effort, axes, power levels |
| `core:charts`       | The charts in Compose `Canvas`: series with a shared cursor and section, bars, zones, the power curve        |
| `core:map`          | The activity's map with MapLibre: the route coloured by a metric, the cursor's dot, the section, the replay  |
| `core:ui`           | What several screens share: the person's formats, sports, the activity card and its route thumbnail          |
| `core:health`       | Health Connect: its permissions, reading what is new and sending it to the API                               |
| `core:data`         | The phone's copy of the API in Room, the offline state and the background sync                               |
| `core:session`      | The session: the instance, the refresh token sealed in the Keystore behind the fingerprint, the lock         |
| `feature:signin`    | Signing in (password and code, Google, passkey), "Forgot it?" and the first account of a new instance        |
| `feature:home`      | Home: this week, the last day, the metrics and the last seven days                                           |
| `feature:feed`      | Activities: the feed, search and its filters                                                                 |
| `feature:activity`  | The activity by sport, its training analysis and matched activities, editing and sharing                     |
| `feature:progress`  | The Progress tab, its eleven cards and the pages they open (volume, goals, zones, skills, best times…)       |
| `feature:stats`     | Stats, estimates, year by year, the calendar, the heatmap and the month and year in review                   |
| `feature:routes`    | Routes done more than once and segments, each with its map and efforts                                       |
| `feature:health`    | Health: last night, the day and the trends, weight, and Settings › Health with Health Connect                |
| `feature:you`       | You: the profile and its editing, the year and all time by sport, records and physical data                  |
| `feature:coach`     | The Coach streamed as it is written, the weekly report and the AI assistants                                 |
| `feature:settings`  | Settings and every section: general, heart rate, notifications, AI, privacy, data, account                   |
| `feature:tools`     | Gear, importing files, the sync screen and the notifications inbox                                           |
| `feature:widget`    | The home-screen widget (Glance) and the launcher's shortcuts                                                 |

## Running it

You need JDK 17 or newer and the Android SDK (`ANDROID_HOME`); Gradle comes with the wrapper. From the
repository root:

```bash
make android           # "Zankup Next Dev" (com.zankup.next.debug) on the connected phone, with the catalogue
make android-release   # "Zankup Next" (com.zankup.next), signed with the owner's key
```

With several devices connected (a phone and an emulator), choose one with `ANDROID_SERIAL=<serial>`
(`adb devices` lists them). Both apps sit beside the React Native ones (`com.zankup.app` and
`com.zankup.app.dev`) until the cutover (OS-1191), when the release becomes `com.zankup.app`.

Notifications need the instance's Firebase project with an Android app `com.zankup.next` (and
`com.zankup.next.debug`): put its `google-services.json` in `app/` (never committed). Without it the app
builds and simply receives none; the API sends through FCM with `FCM_SERVICE_ACCOUNT`.

Release builds are signed with `~/.config/zankup/android/signing.properties` (or the file `ZANKUP_SIGNING`
points at), the same key as the React Native app; without it they keep the debug key.

## Checks

```bash
cd apps/android
./gradlew ktlintCheck lintDebug                   # style and Android Lint (ktlintFormat fixes the style)
./gradlew testDebugUnitTest                       # unit tests: contrast, texts, API client, screenshots
./gradlew recordRoborazziDebug                    # after a visual change: rewrite src/test/screenshots
./gradlew verifyRoborazziDebug                    # what CI runs: fails when a screenshot changed
```

CI runs all of them and the release build (`.github/workflows/android-native.yml`).

## Performance

The debug build carries two tools that only log (they cost nothing in release):

- **Requests** (`adb logcat -s ZankupNet`): one line per API call with its place in the session, how many
  times that same call was made and the session's totals — `#13 GET /api/auth/me (2×) total=13 repeated=2`.
  It shows duplicated calls and whether a screen revalidates more than it should.
- **Frames** (`adb logcat -s ZankupJank`): the frames that miss their deadline, with the screen drawing
  when they did — `screen=ActivityRoute/{id} frame=105.9ms`. It comes from JankStats, in the debug source set.

The frame timings of the journeys are measured on the phone with the Baseline Profile on:

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.zankup.android.baselineprofile.Frames
```

`Frames` covers the cold start, the feed's scroll, push/pop of an activity, Progress's scroll and the
Training chart's scrub. The journeys are in `baselineprofile/.../Journeys.kt` and use the screens'
`testTag`s (`feed`, `activity`, `progress`, `fitness`, `fitness_chart`), which the shell exposes as resource ids.

## Motion

The screen transitions are Strava's: measured on the S26, frame by frame, from a `screenrecord` of its
own push, pop, tab change and back gesture, and kept in `app/src/main/kotlin/com/zankup/android/shell/AppShell.kt`.

- **A screen stacked over a tab** comes in from the right, whole and undimmed, over the 400 ms of Strava's
  push. The one underneath steps aside a quarter and darkens (to 80 % in the dark themes; in the light ones
  it only slips away, since a fade there washes it out). Popping plays the same movement backwards. The
  easing is a bezier fitted to Strava's own positions — off the mark at once, a long settle — not a linear-out.
- **The tab bar belongs to the tab** (its screen and the bar are one composable, `TabScreen`), so it slides
  and dims with it instead of vanishing before the push.
- **Tabs swap in place**, with no movement, as Strava's do; only a stacked screen travels.
- **The gate** (the splash's look, sign-in, the lock, the app) fades from one state into the next.
- **The edge swipe back and the back arrow play the same transition.** The system's predictive-back preview
  is off in the manifest (`enableOnBackInvokedCallback="false"`): it animates the window with a zoom-out that
  has nothing to do with the push/pop the app draws, so the two gestures did not match.
- With the system's animations off, every transition cuts instead of playing.

Changing any of these means recording Strava again and comparing frames: the durations and the easing are
numbers from that recording, not taste.

## Rules

- **Nothing from `apps/mobile` or `packages/core`.** Only the OpenAPI document is read from there, and only
  by the build. The brand comes from `docs/brand/assets`.
- **Colours only from the theme** (`ZankupTheme.colors`), spacing from `ZankupTheme.spacing`.
- **Texts only from resources**, in `values/strings.xml` and `values-es/strings.xml` (a test fails when one
  is missing; Lint fails on hard-coded text).
- **The API's models are generated**, never written by hand. When the API changes, `make generate-api`
  refreshes the document and the next build has the new models.
- Icons: add the name to `scripts/icons.txt` and run `uv run --no-project python scripts/material_symbols.py`.
  Brand: after a change in `docs/brand/assets`, run `python3 scripts/brand.py`.
