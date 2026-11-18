package com.zankup.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun route(path: String) = Links.routeForWebPath(path)

    @Test
    fun activities() {
        assertEquals(ActivityRoute(123), route("/activities/123"))
        assertEquals(ActivityRoute(123), route("/activities/123?from=push"))
        assertEquals(HomeRoute, route("/activities/abc"))
    }

    @Test
    fun screens() {
        assertEquals(ImportRoute, route("/settings/import/strava"))
        assertEquals(CoachRoute, route("/coach?week=1"))
        assertEquals(HealthRoute, route("/health#sleep"))
        assertEquals(SyncRoute, route("/sync"))
        assertEquals(GearRoute, route("/gear"))
        assertEquals(YouRoute, route("/profile/edit"))
        assertEquals(HomeRoute, route("/whatever"))
    }

    @Test
    fun settingsAnchors() {
        assertEquals(SettingsRoute, route("/settings"))
        assertEquals(SettingsSectionRoute("ai"), route("/settings#mcp"))
        assertEquals(SettingsSectionRoute("heart"), route("/settings#zones"))
        assertEquals(SettingsSectionRoute("account"), route("/settings#garmin"))
        assertEquals(SettingsSectionRoute("account"), route("/settings#sessions"))
        assertEquals(SettingsSectionRoute("privacy"), route("/settings#privacy"))
        assertEquals(SettingsRoute, route("/settings#unknown"))
    }
}
