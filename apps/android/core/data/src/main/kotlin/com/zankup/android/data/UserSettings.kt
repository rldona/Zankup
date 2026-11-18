package com.zankup.android.data

import com.zankup.android.api.endpoints.SettingsApi
import com.zankup.android.api.model.Preferences
import com.zankup.android.domain.Units
import com.zankup.android.network.call
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The person's settings as the screens need them: kept like any answer, refreshed when the app opens. */
@Singleton
class UserSettings
@Inject
constructor(store: Store) {
    val query = store.query(CacheKeys.SETTINGS, Preferences.serializer()) { api ->
        call { api.service<SettingsApi>().getSettings() }
    }

    /** Metric until the settings say otherwise. */
    val units: Flow<Units> = query.state.map { Units.fromKey(it.data?.units?.value) }.distinctUntilChanged()
}
