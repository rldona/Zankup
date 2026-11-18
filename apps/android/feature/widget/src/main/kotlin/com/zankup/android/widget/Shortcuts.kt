package com.zankup.android.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zankup.android.designsystem.R as DS
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf

/**
 * The launcher's long-press shortcuts (OS-1171): Sync, the latest activity and the Coach. Dynamic, since
 * the debug and release apps have different ids; each opens the app with the web path it shows, as a
 * notification does.
 */
object Shortcuts {
    fun update(context: Context, latest: Snapshot?, coach: Boolean) {
        val list = buildList {
            add(shortcut(context, "sync", context.getString(R.string.shortcut_sync), DS.drawable.ic_sync, "/sync"))
            latest?.activityId?.let { id ->
                val title = latest.title ?: latest.sport?.let { context.getString(sportName(sportOf(it))) }
                add(
                    shortcut(
                        context,
                        "latest",
                        title ?: context.getString(R.string.shortcut_latest),
                        DS.drawable.ic_directions_run,
                        "/activities/$id"
                    )
                )
            }
            if (coach) {
                add(
                    shortcut(
                        context,
                        "coach",
                        context.getString(R.string.shortcut_coach),
                        DS.drawable.ic_wand_stars,
                        "/coach"
                    )
                )
            }
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    private fun shortcut(context: Context, id: String, label: String, icon: Int, path: String): ShortcutInfoCompat {
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(Intent.ACTION_MAIN))
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_URL, path)
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label.take(25))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(open)
            .build()
    }
}
