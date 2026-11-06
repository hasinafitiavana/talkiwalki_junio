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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.tooling.preview.Preview
import com.talkiwalki.junior.ui.ConnectionStatus
import com.talkiwalki.junior.ui.ParticipantUi
import com.talkiwalki.junior.ui.TalkieWalkieUiState
import com.talkiwalki.junior.ui.ChatMessageUi
import com.talkiwalki.junior.ui.theme.TalkiWalkiJuniorTheme
import com.talkiwalki.junior.network.QrGroupInvitation
import com.talkiwalki.junior.ui.component.QrCode

@Composable
fun CommunicationScreen(
    state: TalkieWalkieUiState,
    onTransmissionToggle: () -> Unit,
    onNotifyGroup: () -> Unit,
    onChatDraftChange: (String) -> Unit,
    onSendChat: () -> Unit,
    onLeave: () -> Unit
) {
    var isChatOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
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
        Column(modifier = Modifier.fillMaxWidth()) { state.participants.forEach { ParticipantRow(it, it.id == state.selfId) } }
        Spacer(Modifier.height(32.dp))
        PushToTalkButton(isTransmitting = state.isPushToTalkPressed, onClick = onTransmissionToggle)
        Spacer(Modifier.height(12.dp))
        Text(if (state.isPushToTalkPressed) "Votre micro est activé" else "Appuyez pour activer votre micro", style = MaterialTheme.typography.titleMedium)
        Text("Audio local PCM — lecture sur haut-parleur", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNotifyGroup, enabled = state.participants.size > 1) { Text("Notifier le groupe") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Quitter le groupe") }
        }
        FloatingActionButton(
            onClick = { isChatOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Text("💬") }
    }
    if (isChatOpen) {
        ChatDialog(
            state = state,
            onDraftChange = onChatDraftChange,
            onSend = onSendChat,
            onDismiss = { isChatOpen = false }
        )
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
            onTransmissionToggle = {}, onNotifyGroup = {}, onChatDraftChange = {}, onSendChat = {}, onLeave = {}
        )
    }
}

@Composable private fun ChatDialog(
    state: TalkieWalkieUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.chatMessages.lastOrNull()?.id) {
        if (state.chatMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Discussion", style = MaterialTheme.typography.titleLarge)
                        Text(state.groupName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(280.dp), state = messageListState) {
                    if (state.chatMessages.isEmpty()) {
                        item { Text("Aucun message pour cette session.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
                    } else {
                        items(state.chatMessages.size) { index -> ChatMessageRow(state.chatMessages[index]) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.chatDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Écrire un message") },
                    supportingText = { Text("Messages supprimés à la fin du groupe") },
                    maxLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSend, modifier = Modifier.fillMaxWidth(), enabled = state.chatDraft.isNotBlank() && state.status == ConnectionStatus.CONNECTED) {
                    Text("Envoyer")
                }
            }
        }
    }
}

@Composable private fun ChatMessageRow(message: ChatMessageUi) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        Text(if (message.isMine) "Vous" else message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            message.text,
            modifier = Modifier.background(
                if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            ).padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (message.isMine) TextAlign.End else TextAlign.Start
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

@Composable private fun ParticipantRow(participant: ParticipantUi, isCurrentUser: Boolean) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (isCurrentUser) CurrentUserGreenBackground else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (isCurrentUser) CurrentUserGreen else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Spacer(Modifier.width(12.dp)); Text(participant.name, modifier = Modifier.weight(1f), fontWeight = if (participant.isSpeaking) FontWeight.Bold else FontWeight.Normal, color = if (isCurrentUser) CurrentUserGreenText else MaterialTheme.colorScheme.onSurface)
            Text(when { isCurrentUser -> "Vous"; participant.isHost -> "Hôte"; else -> "" }, color = if (isCurrentUser) CurrentUserGreen else MaterialTheme.colorScheme.primary)
        }
    }
}

private val CurrentUserGreen = Color(0xFF2E7D32)
private val CurrentUserGreenBackground = Color(0xFFE8F5E9)
private val CurrentUserGreenText = Color(0xFF17351D)

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
