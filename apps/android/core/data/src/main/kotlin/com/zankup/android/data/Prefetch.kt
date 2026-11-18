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
 * Starts loading an activity — itself, its context and its series — as the finger lands on its card,
 * so the screen opens with them already kept. Each part is fetched once at a time.
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
                launch {
                    (call { activities.getActivity(id.toInt()) } as? ApiResult.Ok)?.let {
                        store.put(CacheKeys.activity(id), ActivityDetail.serializer(), it.value)
                    }
                }
                launch {
                    (call { activities.getActivityContext(id.toInt()) } as? ApiResult.Ok)?.let {
                        store.put(CacheKeys.context(id), ActivityContext.serializer(), it.value)
                    }
                }
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
