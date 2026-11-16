/**
 * The app's icons: Material Symbols (Rounded) from packages/core, as the PWA shows them, drawn as
 * characters of an icon font built from the same paths (assets/fonts/ZankupIcons.otf,
 * scripts/icon_font.py): plain text, so a screen with dozens of icons has no Skia canvas for each.
 * `SportIcon` is a sport's icon in its colour on a soft tile, like the PWA's.
 */
import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import {
  GOOGLE_G,
  MARK_SHAPES,
  MARK_VIEWBOX,
  SOURCE_ICONS,
  SPORT_ICONS,
  type HealthSourceName,
  type MaterialIcon,
  type Sport,
} from '@zankup/core';
import { useEffect, useState } from 'react';
import { Animated, Easing, Text, View, type ColorValue } from 'react-native';

import { ICON_GLYPHS } from './iconGlyphs';

import { useTheme } from './theme';

export type IconName = MaterialIcon;

/** Every icon has its character in the font: a name without one does not compile. */
const GLYPHS: Record<IconName, string> = ICON_GLYPHS;

export function Icon({ name, color, size = 24 }: { name: IconName; color: ColorValue; size?: number }) {
  // The font's em is the icons' 960-unit box (ascent 960, descent 0): at `size`, a glyph fills a square
  // of `size` exactly.
  return (
    <Text
      accessible={false}
      importantForAccessibility="no"
      allowFontScaling={false}
      style={{
        width: size,
        height: size,
        fontFamily: 'ZankupIcons',
        fontSize: size,
        lineHeight: size,
        color,
        includeFontPadding: false,
        textAlign: 'center',
        textAlignVertical: 'center',
      }}
    >
      {GLYPHS[name]}
    </Text>
  );
}

/** A sport's icon in its colour, on a tile of the same colour, faint. */
export function SportIcon({ sport, size = 36 }: { sport: Sport; size?: number }) {
  const c = useTheme();
  const colour = c[sport];
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.28,
        backgroundColor: `${colour}26`,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon name={SPORT_ICONS[sport]} color={colour} size={size * 0.62} />
    </View>
  );
}

// Google's G in one colour (Ionicons' logo-google, from packages/core), white on "Continue with Google".
const GOOGLE = Skia.Path.MakeFromSVGString(GOOGLE_G.d);

export function GoogleIcon({ color, size = 20 }: { color: ColorValue; size?: number }) {
  if (!GOOGLE) return null;
  return (
    <Canvas style={{ width: size, height: size }}>
      <Path path={GOOGLE} color={String(color)} transform={[{ scale: size / GOOGLE_G.size }]} />
    </Canvas>
  );
}

// The Zankup mark (packages/core): one stroke, in a 1106 × 1300 box.
const ZANKUP = Skia.Path.MakeFromSVGString(MARK_SHAPES.path.d);

/**
 * The Zankup mark, small, in one colour: beside a figure Zankup works out itself (a sleep score), as a
 * source's mark sits beside the figures it gives.
 */
export function ZankupMark({ color, size = 16 }: { color: ColorValue; size?: number }) {
  if (!ZANKUP) return null;
  const scale = size / MARK_VIEWBOX.height;
  return (
    <Canvas style={{ width: size, height: size }}>
      <Path
        path={ZANKUP}
        style="stroke"
        strokeWidth={MARK_SHAPES.path.strokeWidth}
        strokeCap="butt"
        color={String(color)}
        transform={[{ translateX: (size - MARK_VIEWBOX.width * scale) / 2 }, { scale }]}
      />
    </Canvas>
  );
}

const marks = new Map<string, ReturnType<typeof Skia.Path.MakeFromSVGString>>();

/** A health source's mark (Garmin, Google Health, Health Connect), in the corner of a health widget. */
export function SourceIcon({
  source,
  color,
  size = 16,
}: {
  source: HealthSourceName;
  color: ColorValue;
  size?: number;
}) {
  return <PathIcon icon={SOURCE_ICONS[source]} color={color} size={size} />;
}

/** A mark drawn from one SVG path in its own box (a source's, Claude's). */
export function PathIcon({
  icon,
  color,
  size = 16,
}: {
  icon: { viewBox: string; d: string };
  color: ColorValue;
  size?: number;
}) {
  if (!marks.has(icon.d)) marks.set(icon.d, Skia.Path.MakeFromSVGString(icon.d));
  const shape = marks.get(icon.d);
  if (!shape) return null;
  const [x = 0, y = 0, w = 24, h = 24] = icon.viewBox.split(' ').map(Number);
  const scale = size / Math.max(w, h);
  return (
    <Canvas style={{ width: size, height: size }}>
      <Path
        path={shape}
        color={String(color)}
        transform={[{ scale }, { translateX: -x }, { translateY: -y }]}
      />
    </Canvas>
  );
}

/** An icon that turns while `spinning` (sync in progress), and rests otherwise. */
export function SpinningIcon({
  name,
  color,
  size = 24,
  spinning,
}: {
  name: IconName;
  color: ColorValue;
  size?: number;
  spinning: boolean;
}) {
  const [turn] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (!spinning) {
      turn.stopAnimation();
      turn.setValue(0);
      return;
    }
    const loop = Animated.loop(
      Animated.timing(turn, { toValue: 1, duration: 1000, easing: Easing.linear, useNativeDriver: true }),
    );
    loop.start();
    return () => loop.stop();
  }, [spinning, turn]);
  // Sync arrows turn anticlockwise, like the icon's arrows.
  const rotate = turn.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '-360deg'] });
  return (
    <Animated.View style={{ transform: [{ rotate }] }}>
      <Icon name={name} color={color} size={size} />
    </Animated.View>
  );
}
