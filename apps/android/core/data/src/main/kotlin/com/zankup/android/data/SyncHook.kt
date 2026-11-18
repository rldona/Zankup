package com.zankup.android.data

import com.zankup.android.network.ZankupApi
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Something to do after each background sync and when the app opens (the widget's snapshot). */
interface SyncHook {
    suspend fun afterSync(api: ZankupApi)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncHookModule {
    /** None by default; a feature adds one with `@IntoSet`. */
    @Multibinds abstract fun hooks(): Set<SyncHook>
}
