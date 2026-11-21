package com.zankup.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.endpoints.AiApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.data.SyncHook
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.ZankupJson
import com.zankup.android.network.call
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The snapshot on the phone: what the widget draws until the next refresh. */
internal class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun read(): Snapshot = prefs.getString(KEY, null)
        ?.let { runCatching { ZankupJson.decodeFromString(Snapshot.serializer(), it) }.getOrNull() }
        ?: Snapshot()

    fun write(snapshot: Snapshot) {
        prefs.edit().putString(KEY, ZankupJson.encodeToString(Snapshot.serializer(), snapshot)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    /** When it was last drawn, in epoch milliseconds (0 if never). */
    fun lastRefresh(): Long = prefs.getLong(REFRESHED, 0L)

    fun stamp(now: Long) {
        prefs.edit().putLong(REFRESHED, now).apply()
    }

    private companion object {
        const val KEY = "snapshot"
        const val REFRESHED = "refreshed"
    }
}

/**
 * Builds a new snapshot from the API and redraws the widgets, and the launcher's shortcuts with it:
 * when the app opens (at most every [EVERY_MS]) and after each background sync. Nothing changes when the
 * API cannot be reached.
 */
@Singleton
class WidgetRefresh
@Inject
constructor(@param:ApplicationContext private val context: Context) : SyncHook {
    override suspend fun afterSync(api: ZankupApi) = refresh(api, force = true)

    suspend fun refresh(api: ZankupApi, force: Boolean = false) {
        withContext(Dispatchers.IO) {
            val store = WidgetStore(context)
            val now = System.currentTimeMillis()
            if (!force && now - store.lastRefresh() < EVERY_MS) return@withContext
            val page = call { api.service<ActivitiesApi>().listActivities(limit = 1) }
            if (page !is ApiResult.Ok) return@withContext
            val from = LocalDate.now().minusDays(3).toString()
            val form = (call { api.service<StatsApi>().getFitness(from = from) } as? ApiResult.Ok)?.value?.lastOrNull()
            val snapshot = snapshotOf(page.value.items.firstOrNull(), form, Locale.getDefault())
            store.write(snapshot)
            LatestWidget().updateAll(context)
            val coach = (call { api.service<AiApi>().getAiStatus() } as? ApiResult.Ok)?.value?.available == true
            Shortcuts.update(context, snapshot, coach)
            store.stamp(now)
        }
    }

    /** After signing out: the widget forgets the person's activity. */
    suspend fun clear() {
        WidgetStore(context).clear()
        LatestWidget().updateAll(context)
        Shortcuts.clear(context)
    }

    private companion object {
        /** Opening the app again within this window does not ask the API for the widget again. */
        const val EVERY_MS = 15 * 60_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds @IntoSet
    abstract fun hook(refresh: WidgetRefresh): SyncHook
}
