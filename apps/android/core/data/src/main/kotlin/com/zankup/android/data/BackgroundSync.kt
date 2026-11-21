package com.zankup.android.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.api.model.Week
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.Session
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Background sync (OS-1121): every ~30 minutes, with a network, Android lets the app refresh the feed's
 * first page and this week and keep the latest activities' series, so the app opens with today's
 * activity already there, offline too. It needs the refresh token without the fingerprint (no strong
 * biometrics, or the lock turned off); behind the fingerprint it waits for the next opening.
 */
@HiltWorker
class BackgroundSync
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: Session,
    private val store: Store,
    private val hooks: Set<@JvmSuppressWildcards SyncHook>
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val api = session.backgroundApi() ?: return Result.success()
        val activities = api.service<ActivitiesApi>()
        val page = call { activities.listActivities() }
        if (page !is ApiResult.Ok) return if (page is ApiResult.Offline) Result.retry() else Result.success()
        store.put(CacheKeys.FEED_FIRST, ActivityPage.serializer(), page.value)
        val today = LocalDate.now().toString()
        (call { api.service<ProfileApi>().getWeek(today) } as? ApiResult.Ok)?.let {
            store.put(CacheKeys.week(today), Week.serializer(), it.value)
        }
        coroutineScope {
            page.value.items.take(LATEST).forEach { item ->
                if (!item.hasGps || store.hasStreams(item.id.toLong())) return@forEach
                launch {
                    (call { activities.getActivityStreams(item.id) } as? ApiResult.Ok)?.let {
                        store.putStreams(item.id.toLong(), ActivityStreams.serializer(), it.value)
                    }
                }
            }
        }
        hooks.forEach { runCatching { it.afterSync(api) } }
        return Result.success()
    }

    companion object {
        private const val NAME = "zankup-sync"

        /** The latest activities whose series are kept for offline use. */
        private const val LATEST = 5

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<BackgroundSync>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(
                context
            ).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
