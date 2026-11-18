package com.zankup.android.shell

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.zankup.android.push.Push

/**
 * Android 13+ asks before an app shows notifications. Zankup asks once, the first time the app opens
 * signed in; after that the switch is Android's (and Settings → Notifications says how to turn it on).
 */
@Composable
internal fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!Push.allowed(context) && !prefs.getBoolean(ASKED, false)) {
            prefs.edit { putBoolean(ASKED, true) }
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private const val ASKED = "notifications_asked"
