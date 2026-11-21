package com.zankup.android.data

import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.model.ActivityContext
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityStreams
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starts loading an activity — itself, its context and its series — as the finger lands on it, so the
 * screen opens with them already kept. It goes through the same [Query] the screen uses, so the screen
 * opening waits for this one instead of asking again. Each part is fetched once at a time.
 */
@Singleton
class Prefetch
@Inject
constructor(private val store: Store, private val source: ApiSource) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = mutableSetOf<Long>()

    fun activity(id: Long) {
        val api = source.api ?: return
        synchronized(running) { if (!running.add(id)) return }
        scope.launch {
            try {
                val activities = api.service<ActivitiesApi>()
                val detail = store.query(CacheKeys.activity(id), ActivityDetail.serializer()) {
                    call { it.service<ActivitiesApi>().getActivity(id.toInt()) }
                }
                val context = store.query(CacheKeys.context(id), ActivityContext.serializer()) {
                    call { it.service<ActivitiesApi>().getActivityContext(id.toInt()) }
                }
                launch { detail.refresh() }
                launch { context.refresh() }
                if (!store.hasStreams(id)) {
                    (call { activities.getActivityStreams(id.toInt()) } as? ApiResult.Ok)?.let {
                        store.putStreams(id, ActivityStreams.serializer(), it.value)
                    }
                }
            } finally {
                synchronized(running) { running.remove(id) }
            }
        }
    }
}
