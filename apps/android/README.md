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
