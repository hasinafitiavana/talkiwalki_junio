package com.talkiwalki.junior.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TalkieWalkieViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test fun createGroup_requiresNickname() {
        val viewModel = TalkieWalkieViewModel()
        viewModel.createGroup()
        assertTrue(viewModel.uiState.value.errorMessage != null)
    }

    @Test fun transmissionToggle_isIgnoredWhenDisconnected() {
        val viewModel = TalkieWalkieViewModel()
        viewModel.toggleTransmission()
        assertFalse(viewModel.uiState.value.isPushToTalkPressed)
    }

    @Test fun leaveGroup_resetsSessionUiState() {
        val viewModel = TalkieWalkieViewModel()

        viewModel.leaveGroup()

        assertEquals(AppDestination.HOME, viewModel.uiState.value.destination)
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.participants.isEmpty())
    }
}
