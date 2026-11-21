package com.zankup.android.settings.more

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.Preferences
import com.zankup.android.api.model.PreferencesUpdate
import com.zankup.android.api.model.PrivacyZone
import com.zankup.android.data.Store
import com.zankup.android.data.UserSettings
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Settings › Privacy (OS-1161): the zones hidden from anything shared, and the latest routes to frame the map. */
@HiltViewModel
class PrivacyModel
@Inject
constructor(store: Store, userSettings: UserSettings, private val service: ServiceSettings) :
    ViewModel() {
    val settings = userSettings.query

    val latest = store.query("settingsm/activities", ActivityPage.serializer()) { api ->
        call { api.service<ActivitiesApi>().listActivities(limit = 10) }
    }

    suspend fun refresh() {
        settings.refreshIfStale()
        latest.refreshIfStale()
    }

    suspend fun save(zones: List<PrivacyZone>): ApiResult<Preferences> =
        service.patchSettings(PreferencesUpdate(privacyZones = zones))
}
