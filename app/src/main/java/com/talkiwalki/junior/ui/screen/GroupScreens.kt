package com.talkiwalki.junior.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.talkiwalki.junior.ui.ConnectionStatus
import com.talkiwalki.junior.ui.TalkieWalkieUiState
import com.talkiwalki.junior.network.DiscoveredGroup
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme

@Composable
fun CreateGroupScreen(state: TalkieWalkieUiState, onNicknameChange: (String) -> Unit, onGroupNameChange: (String) -> Unit, onCreate: () -> Unit, onBack: () -> Unit) {
    FormScaffold("Créer un groupe", onBack) {
        OutlinedTextField(state.nickname, onNicknameChange, Modifier.fillMaxWidth(), label = { Text("Votre pseudo") }, singleLine = true)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(state.groupName, onGroupNameChange, Modifier.fillMaxWidth(), label = { Text("Nom du groupe") }, singleLine = true)
        ErrorText(state.errorMessage)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = state.status != ConnectionStatus.CONNECTING) { Text(if (state.status == ConnectionStatus.CONNECTING) "Création…" else "Créer le groupe") }
    }
}

@Composable
fun JoinGroupScreen(state: TalkieWalkieUiState, onNicknameChange: (String) -> Unit, onJoinGroup: (DiscoveredGroup) -> Unit, onRefreshGroups: () -> Unit, onScanQr: () -> Unit, onAddressChange: (String) -> Unit, onManualJoin: () -> Unit, onBack: () -> Unit) {
    FormScaffold("Rejoindre un groupe", onBack) {
        OutlinedTextField(state.nickname, onNicknameChange, Modifier.fillMaxWidth(), label = { Text("Votre pseudo") }, singleLine = true)
        Spacer(Modifier.height(20.dp))
        JoinSectionCard(title = "Groupes sur ce Wi‑Fi") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Les téléphones doivent être sur le même réseau local.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRefreshGroups, enabled = state.status != ConnectionStatus.CONNECTING) { Text("Actualiser") }
            }
            Spacer(Modifier.height(12.dp))
            if (state.discoveredGroups.isEmpty() && state.isDiscovering) {
                Text("Recherche réseau active.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (state.discoveredGroups.isEmpty()) {
                Text("Aucun groupe trouvé pour le moment.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.discoveredGroups.forEach { group ->
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.groupName, style = MaterialTheme.typography.titleMedium)
                                Text(group.endpoint, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { onJoinGroup(group) }, enabled = state.status != ConnectionStatus.CONNECTING) { Text("Rejoindre") }
                        }
                    }
                }
            }
            if (state.isResolvingGroup) Text("Connexion aux informations d’un groupe…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        JoinSectionCard(title = "Avec un QR code") {
            Text("Scannez le QR affiché par la personne qui a créé le groupe.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onScanQr, modifier = Modifier.fillMaxWidth(), enabled = state.status != ConnectionStatus.CONNECTING) { Text("Scanner le QR code") }
        }
        Spacer(Modifier.height(20.dp))
        JoinSectionCard(title = "Adresse IP (secours)") {
            OutlinedTextField(state.manualAddress, onAddressChange, Modifier.fillMaxWidth(), label = { Text("Adresse du groupe") }, supportingText = { Text("Ex. 192.168.1.10 ou 192.168.1.10:49321") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onManualJoin, modifier = Modifier.fillMaxWidth(), enabled = state.status != ConnectionStatus.CONNECTING) { Text(if (state.status == ConnectionStatus.CONNECTING) "Connexion…" else "Se connecter") }
        }
        ErrorText(state.errorMessage)
    }
}

@Composable private fun JoinSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable private fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.Top) {
        OutlinedButton(onClick = onBack) { Text("Retour") }
        Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(28.dp)); content()
    }
}
@Composable private fun ErrorText(message: String?) { if (message != null) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

@Preview(showBackground = true)
@Composable
private fun JoinGroupScreenPreview() {
    TalkiWalkiJuniorTheme {
        JoinGroupScreen(
            state = TalkieWalkieUiState(nickname = "Lina"),
            onNicknameChange = {}, onJoinGroup = {}, onRefreshGroups = {}, onScanQr = {}, onAddressChange = {}, onManualJoin = {}, onBack = {}
        )
    }
}
