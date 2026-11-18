package com.zankup.android.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.zankup.android.R
import com.zankup.android.api.endpoints.NotificationsApi
import com.zankup.android.api.model.FcmToken
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Notifications (OS-1122) through Firebase Cloud Messaging. The API sends each one to every device of
 * the person that registered its token (`PUT /api/notifications/fcm`); which kinds they get is chosen in
 * Settings. Without the instance's `google-services.json` in the build there is no Firebase, and this
 * quietly does nothing.
 */
object Push {
    /** The channel the API names in every message. One per kind would need the API to name them. */
    const val CHANNEL = "default"

    /** The web path a notification opens, in the intent that opens the app (the API's `url`). */
    const val EXTRA_URL = "url"

    fun available(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    fun allowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.push_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.push_channel_description)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    /** Whether this phone gets notifications: on unless turned off in Settings › Notifications. */
    fun enabled(context: Context) = available(context) && !prefs(context).getBoolean(OFF, false)

    /** Tells the instance where to send this phone's notifications, unless they were turned off here. */
    suspend fun register(context: Context, api: ZankupApi): Boolean {
        if (!enabled(context)) return false
        val token = token() ?: return false
        return call { api.service<NotificationsApi>().setFcmToken(FcmToken(token)) } is ApiResult.Ok
    }

    /** Settings › Notifications › This phone: registers the token, or tells the instance to forget it. */
    suspend fun setEnabled(context: Context, api: ZankupApi, on: Boolean): Boolean {
        if (!available(context)) return false
        val done =
            if (on) {
                prefs(context).edit().putBoolean(OFF, false).apply()
                register(context, api)
            } else {
                call { api.service<NotificationsApi>().setFcmToken(FcmToken(null)) } is ApiResult.Ok
            }
        if (done && !on) prefs(context).edit().putBoolean(OFF, true).apply()
        if (!done && on) prefs(context).edit().putBoolean(OFF, true).apply()
        return done
    }

    private const val OFF = "off"

    private suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}
