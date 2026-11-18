# apps/android

The native Android app (phase 11): Kotlin and Jetpack Compose, written from scratch. It shares nothing
with the rest of the repository but the **API** (its OpenAPI document is the contract) and the **screen
structure** of the React Native app, written down in [docs/android-screen-map.md](../../docs/android-screen-map.md).
The decision and the architecture are in [docs/architecture.md](../../docs/architecture.md#android-app-phase-11-native-kotlin--compose);
the plan is phase 11 of the [roadmap](../../docs/roadmap.md).

| Module              | What                                                                                                  |
| ------------------- | ----------------------------------------------------------------------------------------------------- |
| `app`               | The application: the activity, its screens and, in debug builds, the component catalogue              |
| `core:designsystem` | Themes, typography, shapes, spacing, icons, the brand and the shared components, with their texts     |
| `core:network`      | The API client generated from `packages/core/src/api/openapi.json`, the session, results and NDJSON   |
| `core:session`      | The session: the instance, the refresh token sealed in the Keystore behind the fingerprint, the lock  |
| `feature:signin`    | Signing in (password and code, Google, passkey), "Forgot it?" and the first account of a new instance |

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
