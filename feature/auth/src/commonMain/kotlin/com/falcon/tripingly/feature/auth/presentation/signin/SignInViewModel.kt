package com.falcon.tripingly.feature.auth.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.SocialSignInResult
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_account_suspended
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_developer_refused
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_failed
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_not_configured
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SignInViewModel(
    private val sessionRepository: SessionRepository,
    private val socialSignIn: SocialSignIn,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = false,
        val error: UiMessage? = null,
        val appleAvailable: Boolean = false,
        val developerSignInAvailable: Boolean = false,
        /** Any stable text: the same subject signs in to the same test account. */
        val developerSubject: String = "",
    )

    sealed interface Action {
        data object OnGoogleClick : Action
        data object OnAppleClick : Action
        data class OnDeveloperSubjectChange(val subject: String) : Action
        data object OnDeveloperSignInClick : Action
        data object OnDismissError : Action
    }

    private val _uiState = MutableStateFlow(
        State(
            appleAvailable = socialSignIn.appleAvailable,
            developerSignInAvailable = sessionRepository.developerSignInAvailable,
        ),
    )
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    fun onAction(action: Action) {
        when (action) {
            Action.OnGoogleClick -> signIn { exchange(socialSignIn.google()) }
            Action.OnAppleClick -> signIn { exchange(socialSignIn.apple()) }
            is Action.OnDeveloperSubjectChange -> _uiState.update { it.copy(developerSubject = action.subject) }
            Action.OnDeveloperSignInClick -> {
                val subject = _uiState.value.developerSubject.trim()
                if (subject.isNotEmpty()) {
                    signIn { sessionRepository.signInForDevelopment(subject, name = null).toDeveloperOutcome() }
                }
            }
            Action.OnDismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    /** Success needs no handling here: the session changes and the gate moves on. */
    private fun signIn(block: suspend () -> UiMessage?) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val error = block()
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }

    private suspend fun exchange(result: SocialSignInResult): UiMessage? = when (result) {
        is SocialSignInResult.Google -> sessionRepository.signInWithGoogle(result.idToken).toOutcome()
        is SocialSignInResult.Apple -> sessionRepository.signInWithApple(
            identityToken = result.identityToken,
            authorizationCode = result.authorizationCode,
            givenName = result.givenName,
            familyName = result.familyName,
        ).toOutcome()
        SocialSignInResult.Cancelled -> null
        SocialSignInResult.NotConfigured -> UiMessage.Resource(Res.string.signin_error_not_configured)
        SocialSignInResult.Failed -> UiMessage.Resource(Res.string.signin_error_failed)
    }

    private fun AppResult<Unit, DataError.Network>.toOutcome(): UiMessage? = when (this) {
        is AppResult.Success -> null
        is AppResult.Error -> {
            val error = error
            when {
                error is DataError.Network.Api && error.code == ACCOUNT_SUSPENDED ->
                    UiMessage.Resource(Res.string.signin_error_account_suspended)
                // The provider token was rejected (expired, wrong audience).
                error is DataError.Network.Unauthorized -> UiMessage.Resource(Res.string.signin_error_failed)
                else -> error.toUiMessage()
            }
        }
    }

    // The backend answers 404 when developer sign-in is off or the secret doesn't match.
    private fun AppResult<Unit, DataError.Network>.toDeveloperOutcome(): UiMessage? =
        if (this is AppResult.Error && error.let { it is DataError.Network.Api && it.status == 404 }) {
            UiMessage.Resource(Res.string.signin_error_developer_refused)
        } else {
            toOutcome()
        }

    private companion object {
        const val ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED"
    }
}
