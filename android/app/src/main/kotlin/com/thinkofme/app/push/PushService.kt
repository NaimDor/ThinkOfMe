package com.thinkofme.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.thinkofme.app.R
import com.thinkofme.core.data.ProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Receives push token refreshes. Notification display is handled by FCM
 * (server sends notification payloads with a channel id).
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var profiles: ProfileRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { profiles.saveDeviceSettings(TimeZone.getDefault().id, Locale.getDefault().toLanguageTag(), token) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Channels must exist before the first notification. Names are localized. */
fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannels(
        listOf(
            NotificationChannel("reminders", context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("gift_updates", context.getString(R.string.channel_gift_updates), NotificationManager.IMPORTANCE_DEFAULT),
        ),
    )
}
