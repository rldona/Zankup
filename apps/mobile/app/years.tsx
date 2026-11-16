/**
 * Year by year, from Stats' links: each year's distance, cumulative through it, one line per year. It
 * compares whole years, so it is not under Stats' period; a tap on a year makes it stand out. Not
 * scrolled: the chart takes the whole height the years' list leaves, the list stays at the foot.
 */
import { Stack } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { t } from '../src/i18n';
import { Progress } from '../src/stats/Sections';
import { InfoButton, Screen } from '../src/ui';

export default function YearsScreen() {
  const [selected, setSelected] = useState(() => new Date().getFullYear());
  const insets = useSafeAreaInsets();
  return (
    <Screen header scroll={false}>
      <Stack.Screen options={{ headerRight: () => <InfoButton text={t('stats.progressNote')} /> }} />
      <View style={{ flex: 1, padding: 16, paddingBottom: 16 + insets.bottom }}>
        <Progress selected={selected} onSelect={setSelected} />
      </View>
    </Screen>
  );
}
