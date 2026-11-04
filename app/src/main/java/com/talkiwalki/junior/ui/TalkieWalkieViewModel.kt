package com.talkiwalki.junior.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talkiwalki.junior.domain.model.ParticipantRole
import com.talkiwalki.junior.network.LocalSessionRepository
import com.talkiwalki.junior.network.NoopSessionDiscovery
import com.talkiwalki.junior.network.SessionDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TalkieWalkieViewModel(
    private val sessionRepository: LocalSessionRepository = LocalSessionRepository(),
    private val discovery: SessionDiscovery = NoopSessionDiscovery()
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TalkieWalkieUiState())
    val uiState: StateFlow<TalkieWalkieUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { session ->
                val connected = session.participants.isNotEmpty()
                mutableUiState.update { current ->
                    current.copy(
                        status = when {
                            session.error != null -> ConnectionStatus.ERROR
                            connected -> ConnectionStatus.CONNECTED
                            current.status == ConnectionStatus.CONNECTING -> ConnectionStatus.DISCONNECTED
                            else -> current.status
                        },
                        groupName = session.groupName.ifBlank { current.groupName },
                        hostAddress = session.hostAddress,
                        hostPort = if (connected) session.port else null,
                        participants = session.participants.map { ParticipantUi(it.id, it.displayName, it.role == ParticipantRole.HOST) },
                        errorMessage = session.error
                    )
                }
            }
        }
        viewModelScope.launch {
            discovery.snapshot.collectLatest { snapshot ->
                mutableUiState.update { it.copy(discoveredGroups = snapshot.groups, isDiscovering = snapshot.isSearching, isResolvingGroup = snapshot.resolvingCount > 0, errorMessage = snapshot.error ?: it.errorMessage) }
            }
        }
    }

    fun navigateTo(destination: AppDestination) {
        mutableUiState.update { it.copy(destination = destination, status = ConnectionStatus.DISCONNECTED, errorMessage = null) }
        if (destination == AppDestination.JOIN_GROUP) discovery.startDiscovery()
    }
    fun navigateBack() { leaveGroup(); mutableUiState.update { it.copy(destination = AppDestination.HOME) } }
    fun updateNickname(value: String) = mutableUiState.update { it.copy(nickname = value) }
    fun updateGroupName(value: String) = mutableUiState.update { it.copy(groupName = value) }
    fun updateManualAddress(value: String) = mutableUiState.update { it.copy(manualAddress = value) }

    fun createGroup() {
        val state = mutableUiState.value
        if (state.nickname.isBlank()) return showError("Saisissez votre pseudo pour créer un groupe.")
        mutableUiState.update { it.copy(status = ConnectionStatus.CONNECTING, errorMessage = null) }
        viewModelScope.launch {
            sessionRepository.host(state.nickname.trim(), state.groupName.ifBlank { "Junior" }.trim())
                .onSuccess {
                    val session = sessionRepository.state.value
                    discovery.advertise(session.groupName, session.port)
                    mutableUiState.update { it.copy(destination = AppDestination.COMMUNICATION) }
                }
        }
    }

    fun joinByManualAddress() {
        val state = mutableUiState.value
        if (state.nickname.isBlank() || state.manualAddress.isBlank()) return showError("Indiquez votre pseudo et l’adresse IP du groupe.")
        mutableUiState.update { it.copy(status = ConnectionStatus.CONNECTING, errorMessage = null) }
        viewModelScope.launch {
            sessionRepository.join(state.nickname.trim(), state.manualAddress.trim())
                .onSuccess {
                    discovery.stopDiscovery()
                    mutableUiState.update { it.copy(destination = AppDestination.COMMUNICATION) }
                }
        }
    }

    fun joinDiscoveredGroup(group: com.talkiwalki.junior.network.DiscoveredGroup) {
        updateManualAddress("${group.host}:${group.port}")
        joinByManualAddress()
    }

    fun setPushToTalkPressed(isPressed: Boolean) = mutableUiState.update { state ->
        if (state.status != ConnectionStatus.CONNECTED) state else state.copy(isPushToTalkPressed = isPressed)
    }

    fun leaveGroup() {
        discovery.stopAdvertising()
        discovery.stopDiscovery()
        viewModelScope.launch { sessionRepository.close() }
        mutableUiState.update { it.copy(destination = AppDestination.HOME, status = ConnectionStatus.DISCONNECTED, participants = emptyList(), hostAddress = null, hostPort = null, isPushToTalkPressed = false, errorMessage = null) }
    }
    private fun showError(message: String) = mutableUiState.update { it.copy(status = ConnectionStatus.ERROR, errorMessage = message) }
    override fun onCleared() { discovery.stopAdvertising(); discovery.stopDiscovery(); super.onCleared() }
}
