/**
 * Settings on the phone (OS-522): one row per section, as the PWA's page has them. Each section opens
 * its own screen; signing out is in Account. Below them, the app's icon on the launcher.
 */
import { router } from 'expo-router';

import { Icon, type IconName } from '../../src/Icon';
import { t } from '../../src/i18n';
import { SETTINGS_SECTIONS, type SettingsSection } from '../../src/links';
import { sectionNote, sectionTitle } from '../../src/settings/titles';
import { useTheme } from '../../src/theme';
import { Card, MenuRow, Screen } from '../../src/ui';

const SECTION_ICONS: Record<SettingsSection, IconName> = {
  general: 'tune',
  heart: 'ecg_heart',
  notifications: 'notifications',
  ai: 'wand_stars',
  privacy: 'location_off',
  health: 'health_and_safety',
  data: 'database',
  account: 'lock',
};

export default function SettingsScreen() {
  const c = useTheme();
  return (
    <Screen header>
      <Card style={{ paddingVertical: 0, gap: 0 }}>
        {SETTINGS_SECTIONS.map((section, i) => (
          <MenuRow
            key={section}
            icon={<Icon name={SECTION_ICONS[section]} color={c.fg} />}
            label={sectionTitle(section)}
            note={sectionNote(section)}
            onPress={() => router.push(`/settings/${section}`)}
            last={i === SETTINGS_SECTIONS.length - 1}
          />
        ))}
      </Card>
      {/* The launcher icon (as Strava's), apart from the sections: it is the phone's, not the account's. */}
      <Card style={{ paddingVertical: 0, gap: 0 }}>
        <MenuRow
          icon={<Icon name="palette" color={c.fg} />}
          label={t('appIcon.title')}
          note={t('appIcon.rowNote')}
          onPress={() => router.push('/app-icon')}
          last
        />
      </Card>
    </Screen>
  );
}
