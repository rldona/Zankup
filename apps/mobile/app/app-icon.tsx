/**
 * App icon, from Settings (as Strava's "App icons"): the Zankup mark on a choice of colours, the one in
 * use ringed. Tapping one asks Android to show it on the launcher (modules/app-icon); the launcher can
 * take a few seconds to update. The variants are plugins/appIcons.json, which the build turns into
 * launcher entries (plugins/withAppIcons.js).
 */
import { Canvas, Group, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { MARK_SHAPES, MARK_VIEWBOX, type MessageKey } from '@zankup/core';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { currentAppIcon, setAppIcon } from '../modules/app-icon/src';
import { variants } from '../plugins/appIcons.json';
import { t } from '../src/i18n';
import { useTheme } from '../src/theme';
import { accent, Muted, Screen } from '../src/ui';

const mark = Skia.Path.MakeFromSVGString(MARK_SHAPES.path.d);

/** One variant as the launcher draws it: a rounded square, the mark at 52 % of its height. */
function IconPreview({ background, colour, size }: { background: string[]; colour: string; size: number }) {
  const scale = (size * 0.52) / MARK_VIEWBOX.height;
  const x = (size - MARK_VIEWBOX.width * scale) / 2;
  const y = (size - MARK_VIEWBOX.height * scale) / 2;
  return (
    <Canvas style={{ width: size, height: size }}>
      <RoundedRect x={0} y={0} width={size} height={size} r={size * 0.22} color={background[0]}>
        {background.length > 1 && (
          <LinearGradient start={vec(0, 0)} end={vec(size, size)} colors={background} />
        )}
      </RoundedRect>
      {mark && (
        <Group transform={[{ translateX: x }, { translateY: y }, { scale }]}>
          <Path
            path={mark}
            style="stroke"
            strokeWidth={MARK_SHAPES.path.strokeWidth}
            strokeCap="butt"
            color={colour}
          />
        </Group>
      )}
    </Canvas>
  );
}

export default function AppIconScreen() {
  const c = useTheme();
  const [chosen, setChosen] = useState(() => currentAppIcon() ?? 'default');
  const [width, setWidth] = useState(0);
  const size = width ? Math.floor((width - 3 * 16) / 4) : 0;
  return (
    <Screen header>
      <View style={{ gap: 6 }}>
        <Text style={{ color: c.fg, fontSize: 22, fontWeight: '800' }}>{t('appIcon.heading')}</Text>
        <Text style={{ color: c.fg, fontSize: 15, lineHeight: 22 }}>{t('appIcon.intro')}</Text>
      </View>
      <View
        onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
        style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: 16, rowGap: 20 }}
      >
        {size > 0 &&
          variants.map((v) => {
            const on = v.name === chosen;
            return (
              <Pressable
                key={v.name}
                accessibilityRole="radio"
                accessibilityState={{ checked: on }}
                accessibilityLabel={t(`appIcon.${v.name}` as MessageKey)}
                onPress={() => {
                  if (setAppIcon(v.name)) setChosen(v.name);
                }}
                style={{ width: size, alignItems: 'center', gap: 8 }}
              >
                <View
                  style={{
                    padding: 3,
                    borderRadius: size * 0.26,
                    borderWidth: 3,
                    borderColor: on ? accent(c) : 'transparent',
                  }}
                >
                  <IconPreview background={v.background} colour={v.mark} size={size - 12} />
                </View>
                <Text numberOfLines={2} style={{ color: c.fg, fontSize: 13, textAlign: 'center' }}>
                  {t(`appIcon.${v.name}` as MessageKey)}
                </Text>
              </Pressable>
            );
          })}
      </View>
      <Muted style={{ fontSize: 13 }}>{t('appIcon.note')}</Muted>
    </Screen>
  );
}
