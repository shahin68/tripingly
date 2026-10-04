package com.falcon.tripingly.feature.auth

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.SocialSignInResult
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_developer_refused
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_not_configured
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.signin.SignInViewModel
import com.falcon.tripingly.feature.auth.presentation.signin.SignInViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private class FakeSocialSignIn(var google: SocialSignInResult) : SocialSignIn {
        override val appleAvailable = true
        override suspend fun google() = google
        override suspend fun apple() = SocialSignInResult.Cancelled
    }

    private val backend = FakeAccountBackend(today = { LocalDate(2026, 10, 3) })
    private val social = FakeSocialSignIn(SocialSignInResult.Google("id-token"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun googleSignIn_startsTheSession() = runTest {
        val viewModel = SignInViewModel(backend, social)

        viewModel.onAction(Action.OnGoogleClick)
        advanceUntilIdle()

        assertIs<SessionState.Onboarding>(backend.session.value)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun providerWithoutKeys_saysSo() = runTest {
        social.google = SocialSignInResult.NotConfigured
        val viewModel = SignInViewModel(backend, social)

        viewModel.onAction(Action.OnGoogleClick)
        advanceUntilIdle()

        assertEquals(UiMessage.Resource(Res.string.signin_error_not_configured), viewModel.uiState.value.error)
    }

    @Test
    fun cancelledSheet_showsNothing() = runTest {
        social.google = SocialSignInResult.Cancelled
        val viewModel = SignInViewModel(backend, social)

        viewModel.onAction(Action.OnGoogleClick)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun developerSignIn_needsASubject() = runTest {
        val viewModel = SignInViewModel(backend, social)

        viewModel.onAction(Action.OnDeveloperSignInClick)
        advanceUntilIdle()
        assertIs<SessionState.Restoring>(backend.session.value)

        viewModel.onAction(Action.OnDeveloperSubjectChange("tester-1"))
        viewModel.onAction(Action.OnDeveloperSignInClick)
        advanceUntilIdle()
        assertIs<SessionState.Onboarding>(backend.session.value)
    }

    @Test
    fun developerSignInRefused_pointsAtTheSecret() = runTest {
        val refusing = object : SessionRepository by backend {
            override suspend fun signInForDevelopment(subject: String, name: String?): AppResult<Unit, DataError.Network> =
                AppResult.Error(DataError.Network.Api(status = 404, code = "NOT_FOUND", message = "Not found"))
        }
        val viewModel = SignInViewModel(refusing, social)

        viewModel.onAction(Action.OnDeveloperSubjectChange("tester-1"))
        viewModel.onAction(Action.OnDeveloperSignInClick)
        advanceUntilIdle()

        assertEquals(UiMessage.Resource(Res.string.signin_error_developer_refused), viewModel.uiState.value.error)
    }
}
