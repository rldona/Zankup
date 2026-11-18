package com.zankup.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.zankup.android.data.BackgroundSync
import com.zankup.android.push.Push
import com.zankup.android.session.Session
import com.zankup.android.session.SessionState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@HiltAndroidApp
class ZankupApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workers: HiltWorkerFactory

    @Inject lateinit var session: Session

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workers).build()

    override fun onCreate() {
        super.onCreate()
        Push.createChannel(this)
        // Signed in: the background sync runs and this phone receives notifications. Signed out: neither.
        scope.launch {
            session.current.distinctUntilChangedBy { it::class }.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> {
                        BackgroundSync.schedule(this@ZankupApp)
                        Push.register(this@ZankupApp, state.api)
                    }
                    SessionState.SignedOut -> BackgroundSync.cancel(this@ZankupApp)
                    else -> Unit
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Debug builds log requests and may use a local API over plain HTTP. */
    @Provides
    @Named("debug")
    fun debug(): Boolean = BuildConfig.DEBUG
}
