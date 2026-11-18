package com.zankup.android

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@HiltAndroidApp
class ZankupApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Debug builds log requests and may use a local API over plain HTTP. */
    @Provides
    @Named("debug")
    fun debug(): Boolean = BuildConfig.DEBUG
}
