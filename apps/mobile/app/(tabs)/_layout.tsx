import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { Image } from 'expo-image';
import { View, type ColorValue } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MATERIAL_ICONS, meQuery } from '@zankup/core';

import { useApi } from '../../src/auth';
import { scrollTabToTop } from '../../src/scrollTop';
import { Icon, type IconName } from '../../src/Icon';
import { LatestActivity } from '../../src/LatestActivity';
import { t } from '../../src/i18n';
import { borderlessCards, useTheme } from '../../src/theme';

const TABS: { name: string; icon: IconName; label: Parameters<typeof t>[0] }[] = [
  { name: 'index', icon: 'home', label: 'nav.home' },
  { name: 'activities', icon: 'cards_stack', label: 'nav.feed' },
  { name: 'progress', icon: 'monitoring', label: 'nav.progress' },
  { name: 'health', icon: 'favorite', label: 'nav.health' },
  { name: 'profile', icon: 'person', label: 'nav.you' },
];

/** The filled version for the tab you are on, when Material Symbols has one. */
const filled = (icon: IconName): IconName =>
  `${icon}-fill` in MATERIAL_ICONS ? (`${icon}-fill` as IconName) : icon;

/**
 * You's tab, as Strava's: your profile photo, ringed in the tab colour when you are on it; without a
 * photo (or until it loads), the person icon.
 */
function YouIcon({ color, focused }: { color: ColorValue; focused: boolean }) {
  const api = useApi();
  const me = useQuery(meQuery(api));
  const photo = me.data?.avatar_url;
  if (!photo) return <Icon name={focused ? filled('person') : 'person'} color={color} size={28} />;
  return (
    <View
      style={{
        width: 30,
        height: 30,
        borderRadius: 15,
        borderWidth: focused ? 2 : 0,
        borderColor: color,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Image
        accessible={false}
        source={{ uri: photo }}
        style={{ width: focused ? 24 : 28, height: focused ? 24 : 28, borderRadius: 14 }}
      />
    </View>
  );
}

export default function TabsLayout() {
  const c = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <>
      <Tabs
        screenOptions={{
          headerShown: false,
          // Taller than the default, with room above the icons and above the system's gesture bar.
          tabBarStyle: {
            backgroundColor: c.surface,
            borderTopColor: c.line,
            ...(borderlessCards(c) && { borderTopWidth: 0 }),
            height: 76 + insets.bottom,
            paddingTop: 8,
            paddingBottom: insets.bottom + 16,
          },
          tabBarLabelStyle: { fontSize: 12, marginTop: 4 },
          tabBarIconStyle: { width: 28, height: 28 },
          tabBarActiveTintColor: c.accent,
          // Strava's themes draw the other tabs in the text colour (white on dark), as Strava does.
          tabBarInactiveTintColor: borderlessCards(c) ? c.fg : c.muted,
        }}
      >
        {TABS.map((tab) => (
          <Tabs.Screen
            key={tab.name}
            name={tab.name}
            // A tab opens at the top (it would keep its scroll); pressed again, it scrolls up.
            listeners={({ navigation }) => ({
              tabPress: () => scrollTabToTop(tab.name, navigation.isFocused()),
            })}
            options={{
              title: t(tab.label),
              tabBarIcon: ({ color, focused }) =>
                tab.name === 'profile' ? (
                  <YouIcon color={color} focused={focused} />
                ) : (
                  <Icon name={focused ? filled(tab.icon) : tab.icon} color={color} size={28} />
                ),
            }}
          />
        ))}
        {/* Goals is no longer a tab (Progress took its place); the screen stays, unlinked for now. */}
        <Tabs.Screen name="goals" options={{ href: null }} />
      </Tabs>
      {/* A new activity, from below, once (as Strava): name it, add photos, choose its gear. */}
      <LatestActivity />
    </>
  );
}
