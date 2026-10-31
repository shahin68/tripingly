package com.falcon.tripingly.feature.settings

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.FakeUserRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.feature.settings.presentation.BlockedPeopleViewModel
import com.falcon.tripingly.feature.settings.presentation.BlockedPeopleViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedPeopleViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val anna = "00000000-0000-4000-8000-000000000101"
    private val jonas = "00000000-0000-4000-8000-000000000102"

    /** Anna and Jonas blocked. */
    private suspend fun users() = FakeUserRepository().apply {
        setBlocked(anna, true)
        setBlocked(jonas, true)
    }

    @Test
    fun opening_listsTheBlockedPeople() = runTest {
        val viewModel = BlockedPeopleViewModel(users())

        viewModel.onAction(Action.Open)
        assertNull(viewModel.uiState.value.people)
        advanceUntilIdle()

        assertEquals(listOf(anna, jonas), viewModel.uiState.value.people?.map { it.id })
    }

    @Test
    fun unblocking_takesThePersonOffAtOnce() = runTest {
        val viewModel = BlockedPeopleViewModel(users())
        viewModel.onAction(Action.Open)
        advanceUntilIdle()

        viewModel.onAction(Action.Unblock(viewModel.uiState.value.people!!.first()))
        assertEquals(listOf(jonas), viewModel.uiState.value.people?.map { it.id })
        advanceUntilIdle()

        assertEquals(listOf(jonas), viewModel.uiState.value.people?.map { it.id })
        viewModel.onAction(Action.Open)
        advanceUntilIdle()
        assertEquals(listOf(jonas), viewModel.uiState.value.people?.map { it.id })
    }

    @Test
    fun aRefusedUnblock_putsThePersonBack_withAMessage() = runTest {
        val users = users()
        val refusing = object : UserRepository by users {
            override suspend fun setBlocked(userId: String, blocked: Boolean): AppResult<Unit, DataError.Network> =
                AppResult.Error(DataError.Network.NoInternet)
        }
        val viewModel = BlockedPeopleViewModel(refusing)
        viewModel.onAction(Action.Open)
        advanceUntilIdle()

        viewModel.onAction(Action.Unblock(viewModel.uiState.value.people!!.first()))
        advanceUntilIdle()

        assertEquals(listOf(anna, jonas), viewModel.uiState.value.people?.map { it.id })
        assertNotNull(viewModel.uiState.value.message)
    }
}
