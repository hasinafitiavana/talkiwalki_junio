package com.talkiwalki.junior.ui

import com.talkiwalki.junior.network.DiscoveredGroup

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ParticipantUi(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val isSpeaking: Boolean = false
)

data class TalkieWalkieUiState(
    val destination: AppDestination = AppDestination.HOME,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val nickname: String = "",
    val groupName: String = "Junior",
    val manualAddress: String = "",
    val discoveredGroups: List<DiscoveredGroup> = emptyList(),
    val isDiscovering: Boolean = false,
    val isResolvingGroup: Boolean = false,
    val participants: List<ParticipantUi> = emptyList(),
    val hostAddress: String? = null,
    val hostPort: Int? = null,
    val speakerId: String? = null,
    val selfId: String? = null,
    val isPushToTalkPressed: Boolean = false,
    val errorMessage: String? = null
)
