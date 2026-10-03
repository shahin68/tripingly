package com.falcon.tripingly.feature.auth

import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Action
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Step
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.UsernameStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val today = LocalDate(2026, 10, 3)
    private val backend = FakeAccountBackend(today = { today })
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.signedInViewModel(): OnboardingViewModel {
        backend.signInForDevelopment("tester-1", name = "Jonas")
        val viewModel = OnboardingViewModel(backend, backend, today = { today }, languageTag = { "de-AT" })
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun newAccount_startsOnTheProfileWithTheProviderName() = runTest(dispatcher) {
        val viewModel = signedInViewModel()

        assertEquals(Step.PROFILE, viewModel.uiState.value.step)
        assertEquals("Jonas", viewModel.uiState.value.displayName)
    }

    @Test
    fun username_isCheckedAfterTypingStops() = runTest(dispatcher) {
        val viewModel = signedInViewModel()

        viewModel.onAction(Action.OnUsernameChange("Jonas.K"))
        runCurrent()
        assertEquals("jonas.k", viewModel.uiState.value.username)
        assertEquals(UsernameStatus.CHECKING, viewModel.uiState.value.usernameStatus)

        advanceTimeBy(401)
        assertEquals(UsernameStatus.AVAILABLE, viewModel.uiState.value.usernameStatus)

        viewModel.onAction(Action.OnUsernameChange("taken"))
        advanceUntilIdle()
        assertEquals(UsernameStatus.TAKEN, viewModel.uiState.value.usernameStatus)

        viewModel.onAction(Action.OnUsernameChange("a..b"))
        assertEquals(UsernameStatus.INVALID, viewModel.uiState.value.usernameStatus)
    }

    @Test
    fun underAgeBirthDate_warnsAndTheServerEndsTheSession() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.onAction(Action.OnUsernameChange("jonas.k"))
        advanceUntilIdle()

        viewModel.onAction(Action.OnBirthDatePicked(LocalDate(2011, 1, 1)))
        assertTrue(viewModel.uiState.value.isUnderAge)

        viewModel.onAction(Action.OnSaveProfileClick)
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut(SignOutReason.UNDER_AGE), backend.session.value)
    }

    @Test
    fun profileThenConsent_completesOnboarding() = runTest(dispatcher) {
        val viewModel = signedInViewModel()
        viewModel.onAction(Action.OnUsernameChange("jonas.k"))
        viewModel.onAction(Action.OnBirthDatePicked(LocalDate(1995, 4, 12)))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isUnderAge)

        viewModel.onAction(Action.OnSaveProfileClick)
        advanceUntilIdle()

        val consent = viewModel.uiState.value
        assertEquals(Step.CONSENT, consent.step)
        assertEquals(2, consent.documents.size)
        assertFalse(consent.canAccept)

        consent.documents.forEach { viewModel.onAction(Action.OnDocumentAcceptedChange(it.document, true)) }
        viewModel.onAction(Action.OnAcceptClick)
        advanceUntilIdle()

        assertIs<SessionState.SignedIn>(backend.session.value)
    }
}
