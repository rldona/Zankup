package com.zankup.android.settings.basic

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.settings.R

/** A section of Settings: its key (the route's), title, note and icon. */
enum class SettingsSection(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val note: Int,
    val icon: ZIcon
) {
    GENERAL("general", R.string.set_general, R.string.set_general_note, ZIcon.Options),
    HEART("heart", R.string.set_heart, R.string.set_heart_note, ZIcon.HealthConnect),
    NOTIFICATIONS("notifications", R.string.set_notifications, R.string.set_notifications_note, ZIcon.Notifications),
    AI("ai", R.string.set_ai, R.string.set_ai_note, ZIcon.Coach),

    // location_off, health_and_safety and database are not among the app's icons yet.
    PRIVACY("privacy", R.string.set_privacy, R.string.set_privacy_note, ZIcon.LocationOff),
    HEALTH("health", R.string.set_health, R.string.set_health_note, ZIcon.HealthSafety),
    DATA("data", R.string.set_data, R.string.set_data_note, ZIcon.Database),
    ACCOUNT("account", R.string.set_account, R.string.set_account_note, ZIcon.Lock)
    ;

    companion object {
        /** The section for a route's key; an unknown one is General. */
        fun fromKey(key: String?): SettingsSection = entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

/**
 * Settings (OS-1161): one row per section with its note; a row calls [onSection] with the section's
 * key (general, heart, notifications, ai, privacy, health, data, account). Below, apart, the app's
 * icon on the launcher ([onAppIcon]).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onSection: (String) -> Unit, onAppIcon: () -> Unit) {
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.set_title), onBack = onBack) }
    ) { padding ->
        SettingsBody(Modifier.padding(padding)) {
            SettingsGroup(
                rows = SettingsSection.entries.map { section ->
                    {
                        ListRow(
                            stringResource(section.title),
                            subtitle = stringResource(section.note),
                            icon = section.icon,
                            onClick = { onSection(section.key) }
                        )
                    }
                }
            )
            // palette is not among the app's icons yet.
            SettingsGroup(
                rows = listOf {
                    ListRow(
                        stringResource(R.string.set_app_icon),
                        subtitle = stringResource(R.string.set_app_icon_note),
                        icon = ZIcon.Palette,
                        onClick = onAppIcon
                    )
                }
            )
        }
    }
}

/**
 * One section of Settings, titled with its name; an unknown [section] is General. This screen owns the
 * Scaffold and the top bar; each section, and each slot ([ai], [privacy], [account]), draws its own
 * scrolling body inside it (use [SettingsBody]). "health" is not drawn here: the app opens Health's own
 * settings screen for it (it falls back to General if it arrives).
 */
@Composable
fun SettingsSectionScreen(
    section: String,
    onBack: () -> Unit,
    push: PushControl,
    onImport: () -> Unit,
    onGear: () -> Unit,
    ai: @Composable () -> Unit,
    privacy: @Composable () -> Unit,
    account: @Composable () -> Unit
) {
    val shown = SettingsSection.fromKey(section).takeUnless { it == SettingsSection.HEALTH } ?: SettingsSection.GENERAL
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = { ZankupTopBar(stringResource(shown.title), onBack = onBack) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (shown) {
                SettingsSection.GENERAL, SettingsSection.HEALTH -> GeneralSection()
                SettingsSection.HEART -> HeartSection()
                SettingsSection.NOTIFICATIONS -> NotificationsSection(push)
                SettingsSection.AI -> ai()
                SettingsSection.PRIVACY -> privacy()
                SettingsSection.DATA -> DataSection(onImport = onImport, onGear = onGear)
                SettingsSection.ACCOUNT -> account()
            }
        }
    }
}
