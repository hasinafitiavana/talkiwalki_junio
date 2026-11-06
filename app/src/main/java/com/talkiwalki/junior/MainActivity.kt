package com.talkiwalki.junior

import android.Manifest
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
import com.talkiwalki.junior.notification.AndroidGroupMessageNotifier
import com.talkiwalki.junior.ui.TalkieWalkieApp
import com.talkiwalki.junior.ui.TalkieWalkieViewModel
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: TalkieWalkieViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TalkieWalkieViewModel(
                    discovery = NsdSessionDiscovery(applicationContext),
                    audioEngine = com.talkiwalki.junior.audio.PcmAudioEngine(applicationContext),
                    speakerStartNotifier = SpeakerStartVibration(applicationContext),
                    messageNotifier = AndroidGroupMessageNotifier(applicationContext)
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
                        if (state.destination == com.talkiwalki.junior.ui.AppDestination.COMMUNICATION && state.status == com.talkiwalki.junior.ui.ConnectionStatus.CONNECTED) {
                            ActiveSessionService.start(this@MainActivity)
                        } else {
                            ActiveSessionService.stop(this@MainActivity)
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

}
