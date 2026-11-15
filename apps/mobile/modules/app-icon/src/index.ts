/**
 * The launcher icon (Settings → App icon): which variant the launcher shows, and changing it. Null and
 * false where the native module is missing (Expo Go, the web, an old build).
 */
import { requireOptionalNativeModule } from 'expo';

const AppIcon = requireOptionalNativeModule<{
  current(): string | null;
  set(name: string): boolean;
}>('AppIcon');

export const currentAppIcon = (): string | null => AppIcon?.current() ?? null;
export const setAppIcon = (name: string): boolean => AppIcon?.set(name) ?? false;
