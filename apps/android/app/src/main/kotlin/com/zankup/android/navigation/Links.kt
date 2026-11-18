package com.zankup.android.navigation

/**
 * Where a link opens. Notifications carry a **web path** (the same link the web app opens), mapped
 * here to the app's screen as the React Native app does; anything unknown opens Home.
 */
object Links {
    /** Settings anchors that live under another section's name on the phone. */
    private val sectionAlias = mapOf("mcp" to "ai", "zones" to "heart", "garmin" to "account", "sessions" to "account")

    val SECTIONS = setOf("general", "heart", "notifications", "ai", "privacy", "health", "data", "account")

    fun routeForWebPath(path: String): Any {
        val clean = path.trim()
        val bare = clean.substringBefore('?').substringBefore('#').trimEnd('/')
        Regex("^/activities/(\\d+)$").find(bare)?.let { return ActivityRoute(it.groupValues[1].toLong()) }
        return when {
            bare.startsWith("/settings/import") -> ImportRoute
            bare == "/coach" -> CoachRoute
            bare == "/health" -> HealthRoute
            bare == "/sync" -> SyncRoute
            bare == "/gear" -> GearRoute
            bare == "/settings" -> {
                val anchor = clean.substringAfter('#', "").trim()
                if (anchor.isEmpty()) {
                    SettingsRoute
                } else {
                    val section = sectionAlias[anchor] ?: anchor
                    if (section in SECTIONS) SettingsSectionRoute(section) else SettingsRoute
                }
            }
            bare.startsWith("/profile") -> YouRoute
            else -> HomeRoute
        }
    }
}
