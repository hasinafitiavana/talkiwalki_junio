package com.talkiwalki.junior.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.talkiwalki.junior.ui.component.TalkieWalkieMark
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme

@Composable
fun HomeScreen(onCreateGroup: () -> Unit, onJoinGroup: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(PaddingValues(horizontal = 28.dp, vertical = 40.dp)).widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TalkieWalkieMark()
        Spacer(Modifier.height(28.dp))
        Text("TalkiWalki Junior", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Communiquez simplement, sans Internet", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Créer un groupe") }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onJoinGroup, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Rejoindre un groupe") }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TalkiWalkiJuniorTheme { HomeScreen({}, {}) }
}
