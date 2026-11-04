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
import com.talkiwalki.junior.network.NsdSessionDiscovery
import com.talkiwalki.junior.ui.TalkieWalkieApp
import com.talkiwalki.junior.ui.TalkieWalkieViewModel
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TalkieWalkieViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TalkieWalkieViewModel(discovery = NsdSessionDiscovery(applicationContext)) as T
        }
    }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The UI reports a connection error if the permission is declined. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        setContent {
            TalkiWalkiJuniorTheme {
                TalkieWalkieApp(viewModel = viewModel)
            }
        }
    }
}
