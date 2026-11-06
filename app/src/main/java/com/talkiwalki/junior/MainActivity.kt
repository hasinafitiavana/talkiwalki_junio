package com.talkiwalki.junior

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.talkiwalki.junior.network.NsdSessionDiscovery
import com.talkiwalki.junior.audio.SpeakerStartVibration
import com.talkiwalki.junior.audio.ThreePulseVibration
import com.talkiwalki.junior.ui.TalkieWalkieApp
import com.talkiwalki.junior.ui.TalkieWalkieViewModel
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastNotifiedChatMessageId: String? = null
    private val messageVibration by lazy { ThreePulseVibration(applicationContext) }
    private val viewModel: TalkieWalkieViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TalkieWalkieViewModel(
                    discovery = NsdSessionDiscovery(applicationContext),
                    audioEngine = com.talkiwalki.junior.audio.PcmAudioEngine(applicationContext),
                    speakerStartNotifier = SpeakerStartVibration(applicationContext)
                ) as T
        }
    }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The UI reports a connection error if the permission is declined. */ }
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onMicrophonePermissionResult(granted) }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Messaging works without this permission; only the alert is disabled. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createMessageNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.onMicrophonePermissionResult(true)
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        val hasMicrophonePermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (state.destination == com.talkiwalki.junior.ui.AppDestination.COMMUNICATION && state.status == com.talkiwalki.junior.ui.ConnectionStatus.CONNECTED && hasMicrophonePermission) {
                            ActiveSessionService.start(this@MainActivity)
                        } else {
                            ActiveSessionService.stop(this@MainActivity)
                        }
                    }
                }
                launch {
                    viewModel.uiState.collectLatest { state ->
                        val latest = state.chatMessages.lastOrNull() ?: return@collectLatest
                        if (!latest.isMine && latest.id != lastNotifiedChatMessageId) {
                            lastNotifiedChatMessageId = latest.id
                            showMessageNotification(state.groupName, latest.senderName, latest.text)
                        }
                    }
                }
            }
        }
        setContent {
            TalkiWalkiJuniorTheme {
                TalkieWalkieApp(viewModel = viewModel)
            }
        }
    }

    private fun createMessageNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(MESSAGE_CHANNEL_ID, "Messages de groupe", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nouveaux messages reçus dans un groupe TalkiWalki Junior"
                enableVibration(false)
            }
        )
    }

    private fun showMessageNotification(groupName: String, senderName: String, text: String) {
        messageVibration.vibrate()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = android.app.Notification.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$senderName · $groupName")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(nextNotificationId++, notification)
    }

    private companion object {
        const val MESSAGE_CHANNEL_ID = "group_messages"
        var nextNotificationId = 10_000
    }
}
