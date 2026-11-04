package com.talkiwalki.junior.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.talkiwalki.junior.ui.screen.CommunicationScreen
import com.talkiwalki.junior.ui.screen.CreateGroupScreen
import com.talkiwalki.junior.ui.screen.HomeScreen
import com.talkiwalki.junior.ui.screen.JoinGroupScreen

@Composable
fun TalkieWalkieApp(viewModel: TalkieWalkieViewModel) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(enabled = state.destination != AppDestination.HOME) { viewModel.navigateBack() }

    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedContent(targetState = state.destination, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "navigation") { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(onCreateGroup = { viewModel.navigateTo(AppDestination.CREATE_GROUP) }, onJoinGroup = { viewModel.navigateTo(AppDestination.JOIN_GROUP) })
                AppDestination.CREATE_GROUP -> CreateGroupScreen(state, viewModel::updateNickname, viewModel::updateGroupName, viewModel::createGroup, viewModel::navigateBack)
                AppDestination.JOIN_GROUP -> JoinGroupScreen(state, viewModel::updateNickname, viewModel::joinDiscoveredGroup, viewModel::updateManualAddress, viewModel::joinByManualAddress, viewModel::navigateBack)
                AppDestination.COMMUNICATION -> CommunicationScreen(state, viewModel::toggleTransmission, viewModel::leaveGroup)
            }
        }
    }
}
