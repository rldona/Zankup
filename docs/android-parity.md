# Android parity check (OS-1190)

The native app (Zankup Next, `apps/android`) against the React Native app (`apps/mobile`), screen by screen, as
[the screen map](android-screen-map.md) lists them. Each block was checked on the owner's S26 with real data
as it was built; this page keeps what was checked, what differs on purpose and what is still to walk through
before the cutover (OS-1191).

Status: ✅ checked on the phone · 🟡 built, not yet walked through on the phone · ↔ differs on purpose.

## Measured (S26, release builds, 18 Nov 2026)

| | Zankup Next | React Native | |
|---|---|---|---|
| Cold start (`am start -W`, lock off) | 78–85 ms | 175–265 ms | ✅ |
| Memory after opening (PSS) | 143 MB | 272 MB | ✅ |
| APK on the phone | 18.2 MB | 60.7 MB | ✅ |
| Dragging the chart cursor | ~124 fps, 0.07 % janky | — | ✅ |
| Replay on the map | ~97 fps, 2 % janky | — | ✅ |

Macrobenchmark (`:baselineprofile`, 10 cold starts each): time to the first frame 122 ms with the Baseline
Profile and 127 ms without it. The profile (start, Home, the feed, an activity, Progress) is in
`app/src/release/generated/baselineProfiles`; the app already starts fast, so it gains little there.

The first native builds held the splash until the fingerprint was given, so Android counted the prompt as
part of the start (0.4–7.6 s). The app now draws the splash's look itself and asks for the fingerprint
over it.

## Screens

| Area | Screens | Status | Differences that stay |
|---|---|---|---|
| Sign-in | password + code, Google, passkey, first account | ✅ password, code, lock 🟡 Google, passkeys | Google needs Android OAuth clients for `com.zankup.next`; passkeys need the new `assetlinks.json` deployed. |
| Home | this week, last day, metrics, seven days | ✅ | |
| Activities | feed, search, filters, calendar, sync | ✅ feed 🟡 calendar, sync | |
| Activity | by sport, charts, map, zones, curve, splits, sets, photos, segments, laps, analysis, matches | ✅ run, ride 🟡 strength, editing, photos, sharing, saving a segment | ↔ No body map for strength yet; ↔ sharing is a link, not the image; ↔ the AI's longer report is not there. |
| Progress | the tab's eleven cards | ✅ | ↔ Charts are drawn in the module; values blend into new ones instead of the RN path animation. |
| Progress pages | volume, log, fitness, effort, predictions, goals (add, edit, delete), zones, skills, bests, monthly, sources | 🟡 | ↔ Goal and weight fields use the person's units (RN: always metric). ↔ Strength goals have no distance. ↔ The hidden goals tab is not ported. |
| Stats | periods, estimates, years, calendar, heatmap, reviews | 🟡 | ↔ The heatmap is on the real map (RN too). ↔ Best-effort curves fit all time, not the period. |
| Routes and segments | lists, one route, one segment, deleting | 🟡 | ↔ Routes listed by times done. |
| Health | tab, weight | ✅ tab 🟡 weight editing | ↔ Weight in the person's units. |
| Health Connect | permissions, history, reading on opening | ✅ first read sent 33 nights and 31 days | |
| You | profile, editing, summary | ✅ 🟡 editing | ↔ Physical data first, as the RN code (the map said last). |
| Settings | general, heart, notifications, AI and MCP, privacy, health, data, account | ✅ list, heart 🟡 the rest | ↔ The language uses Android's per-app language (no restart). ↔ Theme kept on the phone. |
| Coach | weekly report, chat streamed, assistants | ✅ report 🟡 asking | |
| Gear, import, sync, notifications | | 🟡 | ↔ Uploads stop when leaving Import (no background upload). |
| Android only | widget, icons, shortcuts, tile, haptics, background sync, push | ✅ icons, shortcuts 🟡 widget, tile, background sync | Push needs the instance's Firebase app for `com.zankup.next` (`google-services.json`). |

## Accessibility (OS-1181)

- On the S26, the accessibility tree of the five tabs: every clickable element has a spoken label (its own
  or its content's) and none is under 48 dp. ✅
- No icon button in the code without a label. ✅
- The design catalogue at Android's largest font scale (200 %), dark and High contrast, as screenshot tests
  (`LargeFontScreenshotTest`): text wraps instead of being cut. ✅
- 🟡 TalkBack walked through by ear on the main journeys; the charts' spoken summaries.

## Both languages and every theme

- English and Spanish: every string comes from the same catalogues as the PWA; screens were checked in Spanish
  on the phone. 🟡 English to walk through once.
- Themes: the design system's screenshot tests cover every theme; 🟡 walk through High contrast and the Strava
  themes on the phone.

## Before the cutover

1. Walk through every 🟡 row on the phone for a few days of daily use.
2. Notifications: the Firebase app and `google-services.json` for the final package.
3. Google sign-in and passkeys: the OAuth client and `assetlinks.json` for the final package.
4. Then OS-1191: the native app becomes `com.zankup.app` with a higher `versionCode`, installed over the
   React Native app (signing in once more).
