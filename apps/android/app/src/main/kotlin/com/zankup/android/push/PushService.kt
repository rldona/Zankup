package com.zankup.android.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zankup.android.MainActivity
import com.zankup.android.R
import com.zankup.android.session.Session
import com.zankup.android.session.SessionState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Firebase's entry point. With the app in the background Android shows the API's notifications itself
 * (and a tap opens the app with their `url`); in the foreground they arrive here and are shown the same way.
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var session: Session

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val api = (session.current.value as? SessionState.SignedIn)?.api ?: return
        scope.launch { Push.register(applicationContext, api) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val shown = message.notification ?: return
        if (!Push.allowed(this)) return
        val url = message.data[Push.EXTRA_URL] ?: "/"
        val tag = message.data["tag"]
        val open =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(Push.EXTRA_URL, url)
        val pending =
            PendingIntent.getActivity(
                this,
                url.hashCode(),
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        val notification =
            NotificationCompat
                .Builder(this, Push.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getColor(R.color.launcher_background))
                .setContentTitle(shown.title)
                .setContentText(shown.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(shown.body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        // The same tag replaces the previous notification of that kind, as on the web.
        getSystemService(NotificationManager::class.java).notify(tag, 0, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
