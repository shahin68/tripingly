package com.falcon.tripingly.feature.social

import com.falcon.tripingly.feature.social.presentation.people.PeopleSearchViewModel
import com.falcon.tripingly.feature.social.presentation.people.PeopleSearchViewModel.Action
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleSearchViewModelTest : SocialTestBase() {

    private fun viewModel() = PeopleSearchViewModel(users)

    @Test
    fun peopleSearch_asksOnceTypingPauses() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        listOf("a", "an", "ann").forEach {
            viewModel.onAction(Action.QueryChanged(it))
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("ann"), users.queries)
        assertEquals("anna", viewModel.uiState.value.results.single().username)
    }

    @Test
    fun blockingSomeone_dropsTheirRow_atOnce() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(Action.QueryChanged("an"))
        advanceUntilIdle()
        val anna = viewModel.uiState.value.results.single()

        users.setBlocked(anna.id, blocked = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
    }
}
