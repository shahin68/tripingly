package com.falcon.tripingly.feature.auth

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthGateViewModelTest {

    private val today = LocalDate(2026, 11, 22)
    private val backend = FakeAccountBackend(today = { today })
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.onboard() {
        backend.signInForDevelopment("tester-1", name = "Jonas")
        backend.updateProfile(ProfileUpdate(displayName = "Jonas", username = "jonas", birthDate = LocalDate(2000, 1, 1)))
        acceptTerms()
    }

    private suspend fun TestScope.acceptTerms() {
        backend.accept(assertIs<AppResult.Success<List<LegalDocument>>>(backend.legalDocuments("en")).data)
        advanceUntilIdle()
    }

    @Test
    fun newTermsWhileInTheApp_askInASheet_andAcceptingClosesIt() = runTest(dispatcher) {
        val viewModel = AuthGateViewModel(backend)
        onboard()
        assertIs<SessionState.SignedIn>(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isReconsent)

        backend.newTerms()
        advanceUntilIdle()
        assertIs<SessionState.Onboarding>(viewModel.uiState.value.session)
        assertTrue(viewModel.uiState.value.isReconsent)

        acceptTerms()
        assertIs<SessionState.SignedIn>(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isReconsent)
    }

    @Test
    fun aNewAccount_onboardsOnItsPage_notInTheSheet() = runTest(dispatcher) {
        val viewModel = AuthGateViewModel(backend)
        advanceUntilIdle()
        backend.signInForDevelopment("tester-1", name = "Jonas")
        advanceUntilIdle()

        assertIs<SessionState.Onboarding>(viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isReconsent)
    }
}
