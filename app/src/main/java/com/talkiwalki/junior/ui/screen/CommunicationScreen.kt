package com.talkiwalki.junior.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.talkiwalki.junior.ui.ConnectionStatus
import com.talkiwalki.junior.ui.ParticipantUi
import com.talkiwalki.junior.ui.TalkieWalkieUiState
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme
import com.talkiwalki.junior.network.QrGroupInvitation
import com.talkiwalki.junior.ui.component.QrCode

@Composable
fun CommunicationScreen(state: TalkieWalkieUiState, onTransmissionToggle: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.groupName, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        ConnectionBadge(state.status)
        state.hostAddress?.let { address ->
            Spacer(Modifier.height(8.dp))
            Text("Host : $address:${state.hostPort}", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.participants.any { it.isHost && it.id == state.selfId } && state.hostAddress != null && state.hostPort != null) {
            Spacer(Modifier.height(18.dp))
            Text("Inviter avec le QR code", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            QrCode(QrGroupInvitation.create(state.hostAddress, state.hostPort))
            Spacer(Modifier.height(6.dp))
            Text("Les participants peuvent le scanner depuis l’écran Rejoindre.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        Text("${state.participants.size} participant${if (state.participants.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) { state.participants.forEach { ParticipantRow(it) } }
        Spacer(Modifier.height(32.dp))
        PushToTalkButton(isTransmitting = state.isPushToTalkPressed, onClick = onTransmissionToggle)
        Spacer(Modifier.height(12.dp))
        Text(when { state.isPushToTalkPressed -> "Vous parlez…"; state.speakerId != null -> "Un participant parle"; else -> "Appuyez pour parler" }, style = MaterialTheme.typography.titleMedium)
        Text("Audio local PCM — lecture sur haut-parleur", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Quitter le groupe") }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommunicationScreenPreview() {
    TalkiWalkiJuniorTheme {
        CommunicationScreen(
            state = TalkieWalkieUiState(
                groupName = "Les Explorateurs",
                status = ConnectionStatus.CONNECTED,
                participants = listOf(ParticipantUi("1", "Camille", true, true), ParticipantUi("2", "Noah"))
            ),
            onTransmissionToggle = {}, onLeave = {}
        )
    }
}

@Composable private fun ConnectionBadge(status: ConnectionStatus) {
    val connected = status == ConnectionStatus.CONNECTED
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Box(Modifier.size(9.dp).background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape))
        Spacer(Modifier.width(8.dp)); Text(if (connected) "Connecté" else "Déconnecté")
    }
}

@Composable private fun ParticipantRow(participant: ParticipantUi) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (participant.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Spacer(Modifier.width(12.dp)); Text(participant.name, modifier = Modifier.weight(1f), fontWeight = if (participant.isSpeaking) FontWeight.Bold else FontWeight.Normal)
            Text(when { participant.isSpeaking -> "Parle"; participant.isHost -> "Hôte"; else -> "" }, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun PushToTalkButton(isTransmitting: Boolean, onClick: () -> Unit) {
    val scale = animateFloatAsState(if (isTransmitting) .94f else 1f, label = "pttScale")
    Box(
        modifier = Modifier.size(178.dp).scale(scale.value)
            .background(if (isTransmitting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
            .semantics { role = Role.Button; contentDescription = if (isTransmitting) "Arrêter de parler" else "Parler" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(if (isTransmitting) "ARRÊTER" else "PARLER", color = if (isTransmitting) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
}
