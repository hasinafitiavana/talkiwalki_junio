package com.talkiwalki.junior.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.talkiwalki.junior.R
import com.talkiwalki.junior.audio.ThreePulseVibration

fun interface GroupMessageNotifier {
    fun notifyIncomingMessage(groupName: String, senderName: String, text: String)
}

object NoopGroupMessageNotifier : GroupMessageNotifier {
    override fun notifyIncomingMessage(groupName: String, senderName: String, text: String) = Unit
}

/** Posts alerts from the session ViewModel, which remains alive behind the UI in the foreground service. */
class AndroidGroupMessageNotifier(context: Context) : GroupMessageNotifier {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val vibration = ThreePulseVibration(appContext)
    private var nextNotificationId = 10_000

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages de groupe", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nouveaux messages reçus dans un groupe TalkiWalki Junior"
                enableVibration(false)
            }
        )
    }

    override fun notifyIncomingMessage(groupName: String, senderName: String, text: String) {
        vibration.vibrate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notificationManager.notify(
            nextNotificationId++,
            Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("$senderName · $groupName")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object { const val CHANNEL_ID = "group_messages" }
}
