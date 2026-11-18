package com.zankup.android.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zankup.android.R
import com.zankup.android.api.endpoints.SyncApi
import com.zankup.android.api.model.SyncRequest
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.push.Push
import com.zankup.android.session.Session
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile to sync (OS-1171). Without the fingerprint between it and the token (the
 * background sync's rule) it asks the instance to sync right there; behind the fingerprint it opens the
 * app's Sync screen, where the fingerprint is asked for.
 */
@AndroidEntryPoint
class SyncTile : TileService() {
    @Inject lateinit var session: Session

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_sync)
            icon = Icon.createWithResource(this@SyncTile, com.zankup.android.designsystem.R.drawable.ic_sync)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = null
            updateTile()
        }
    }

    override fun onClick() {
        scope.launch {
            val api = session.backgroundApi()
            if (api == null) {
                openSync()
                return@launch
            }
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                updateTile()
            }
            val started = call { api.service<SyncApi>().startSync(SyncRequest()) } is ApiResult.Ok
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = getString(if (started) R.string.tile_sync_started else R.string.tile_sync_failed)
                }
                updateTile()
            }
        }
    }

    private fun openSync() {
        val intent = (packageManager.getLaunchIntentForPackage(packageName) ?: Intent())
            .putExtra(Push.EXTRA_URL, "/sync")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
