/**
 * The app icons the person can choose in Settings → App icon (as Strava's): one activity-alias of
 * MainActivity per variant in plugins/appIcons.json, each a launcher entry with its own adaptive icon,
 * only the default one enabled. modules/app-icon switches which is enabled.
 *
 * The icons are drawn from the mark's geometry (packages/core/src/brand/mark.ts) as vector drawables,
 * the mark at 40 % of the height as the default icon has it, on its variant's colour or gradient. The
 * default variant keeps the icon Expo makes from assets/ (ic_launcher), so it looks as before.
 */
const { mkdirSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');
const { withAndroidManifest, withDangerousMod } = require('expo/config-plugins');

const { variants } = require('./appIcons.json');

// The mark (packages/core/src/brand/mark.ts): one stroke in a 1106 × 1300 box.
const MARK_D = 'M140 710 V346 A206 206 0 0 1 553 346 V954 A206 206 0 0 0 966 954 V590';
const MARK = { width: 1106, height: 1300, stroke: 260 };
// The mark is 40 % of the adaptive icon's 108 dp: a viewport in which its 1300 units are that share.
const VIEWPORT = MARK.height / 0.4;

const capital = (name) => name.charAt(0).toUpperCase() + name.slice(1);
const hex = (colour) => colour.replace('#', '').toLowerCase();

function markVector(colour) {
  const x = (VIEWPORT - MARK.width) / 2;
  const y = (VIEWPORT - MARK.height) / 2;
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="${VIEWPORT}" android:viewportHeight="${VIEWPORT}">
  <group android:translateX="${x}" android:translateY="${y}">
    <path android:pathData="${MARK_D}" android:strokeColor="${colour}"
        android:strokeWidth="${MARK.stroke}" android:strokeLineCap="butt" android:fillColor="#00000000"/>
  </group>
</vector>
`;
}

function backgroundVector(colours) {
  const square = 'M0,0h108v108h-108z';
  if (colours.length === 1)
    return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:pathData="${square}" android:fillColor="${colours[0]}"/>
</vector>
`;
  const items = colours
    .map((c, i) => `        <item android:offset="${i / (colours.length - 1)}" android:color="${c}"/>`)
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:pathData="${square}">
    <aapt:attr name="android:fillColor">
      <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">
${items}
      </gradient>
    </aapt:attr>
  </path>
</vector>
`;
}

const adaptive = (name, mark) => `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@drawable/zankup_icon_bg_${name}"/>
  <foreground android:drawable="@drawable/zankup_icon_mark_${hex(mark)}"/>
  <monochrome android:drawable="@drawable/zankup_icon_mark_ffffff"/>
</adaptive-icon>
`;

const withIconResources = (config) =>
  withDangerousMod(config, [
    'android',
    (mod) => {
      const res = join(mod.modRequest.platformProjectRoot, 'app/src/main/res');
      mkdirSync(join(res, 'drawable'), { recursive: true });
      mkdirSync(join(res, 'mipmap-anydpi-v26'), { recursive: true });
      const marks = new Set(['#ffffff', ...variants.map((v) => v.mark)]);
      for (const colour of marks)
        writeFileSync(join(res, `drawable/zankup_icon_mark_${hex(colour)}.xml`), markVector(colour));
      for (const v of variants) {
        if (v.name === 'default') continue;
        writeFileSync(join(res, `drawable/zankup_icon_bg_${v.name}.xml`), backgroundVector(v.background));
        writeFileSync(join(res, `mipmap-anydpi-v26/zankup_icon_${v.name}.xml`), adaptive(v.name, v.mark));
      }
      return mod;
    },
  ]);

const isLauncher = (filter) =>
  (filter.action ?? []).some((a) => a.$['android:name'] === 'android.intent.action.MAIN') &&
  (filter.category ?? []).some((c) => c.$['android:name'] === 'android.intent.category.LAUNCHER');

const withAliases = (config) =>
  withAndroidManifest(config, (mod) => {
    const app = mod.modResults.manifest.application[0];
    const main = app.activity.find((a) => a.$['android:name'] === '.MainActivity');
    if (!main) throw new Error('withAppIcons: no .MainActivity in the manifest');
    // The launcher entry moves to the aliases; MainActivity keeps its deep links.
    main['intent-filter'] = (main['intent-filter'] ?? []).filter((f) => !isLauncher(f));
    app['activity-alias'] = [
      ...(app['activity-alias'] ?? []).filter((a) => !a.$['android:name'].startsWith('.AppIcon')),
      ...variants.map((v) => {
        const icon = v.name === 'default' ? '@mipmap/ic_launcher' : `@mipmap/zankup_icon_${v.name}`;
        const round = v.name === 'default' ? '@mipmap/ic_launcher_round' : icon;
        return {
          $: {
            'android:name': `.AppIcon${capital(v.name)}`,
            'android:targetActivity': '.MainActivity',
            'android:enabled': v.name === 'default' ? 'true' : 'false',
            'android:exported': 'true',
            'android:icon': icon,
            'android:roundIcon': round,
          },
          'intent-filter': [
            {
              action: [{ $: { 'android:name': 'android.intent.action.MAIN' } }],
              category: [{ $: { 'android:name': 'android.intent.category.LAUNCHER' } }],
            },
          ],
        };
      }),
    ];
    return mod;
  });

module.exports = (config) => withAliases(withIconResources(config));
