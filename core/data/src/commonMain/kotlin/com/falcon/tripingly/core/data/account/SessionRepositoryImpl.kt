package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.network.account.AccountApi
import com.falcon.tripingly.core.network.auth.AuthApi
import com.falcon.tripingly.core.network.auth.AuthTokens
import com.falcon.tripingly.core.network.auth.SessionEndReason
import com.falcon.tripingly.core.network.auth.SessionEvents
import com.falcon.tripingly.core.network.auth.TokenStore
import com.falcon.tripingly.core.network.model.AppleSignInDto
import com.falcon.tripingly.core.network.model.AuthTokensDto
import com.falcon.tripingly.core.network.model.DevSignInDto
import com.falcon.tripingly.core.network.model.GoogleSignInDto
import com.falcon.tripingly.core.network.model.LogoutDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SessionRepositoryImpl(
    private val authApi: AuthApi,
    private val accountApi: AccountApi,
    private val tokenStore: TokenStore,
    sessionEvents: SessionEvents,
    /** Null when this build has no developer sign-in (production). */
    private val developerSignIn: DeveloperSignIn?,
    scope: CoroutineScope,
) : SessionRepository {

    private val state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = state.asStateFlow()

    override val developerSignInAvailable: Boolean = developerSignIn != null

    init {
        scope.launch {
            // The refresh already cleared the tokens; only the screen has to follow.
            sessionEvents.sessionEnded.collect { reason ->
                state.value = SessionState.SignedOut(
                    when (reason) {
                        SessionEndReason.REFRESH_REFUSED -> SignOutReason.SESSION_EXPIRED
                        SessionEndReason.ACCOUNT_SUSPENDED -> SignOutReason.ACCOUNT_SUSPENDED
                    },
                )
            }
        }
    }

    override suspend fun restore(): AppResult<Unit, DataError.Network> {
        if (tokenStore.get() == null) {
            state.value = SessionState.SignedOut()
            return AppResult.Success(Unit)
        }
        state.value = SessionState.Restoring
        return when (val result = refreshAccount()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> {
                if (result.error is DataError.Network.Unauthorized) {
                    tokenStore.clear()
                    if (state.value !is SessionState.SignedOut) state.value = SessionState.SignedOut()
                }
                result
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String) =
        completeSignIn(authApi.signInWithGoogle(GoogleSignInDto(idToken = idToken)))

    override suspend fun signInWithApple(
        identityToken: String,
        authorizationCode: String,
        givenName: String?,
        familyName: String?,
    ) = completeSignIn(
        authApi.signInWithApple(
            AppleSignInDto(
                identityToken = identityToken,
                authorizationCode = authorizationCode,
                givenName = givenName,
                familyName = familyName,
            ),
        ),
    )

    override suspend fun signInForDevelopment(subject: String, name: String?): AppResult<Unit, DataError.Network> {
        val developer = checkNotNull(developerSignIn) { "Developer sign-in is not available in this build" }
        return completeSignIn(
            authApi.signInForDevelopment(DevSignInDto(subject = subject, name = name), developer.secret),
        )
    }

    override suspend fun refreshAccount(): AppResult<Account, DataError.Network> =
        accountApi.me().map { it.toAccount() }.also { result ->
            if (result is AppResult.Success) onAccountChanged(result.data)
        }

    override suspend fun signOut(reason: SignOutReason?) {
        tokenStore.get()?.let { tokens ->
            // Best effort: the server revokes the session; offline, it simply expires.
            authApi.logout(LogoutDto(refreshToken = tokens.refreshToken))
        }
        tokenStore.clear()
        state.value = SessionState.SignedOut(reason)
    }

    internal fun onAccountChanged(account: Account) {
        state.value = account.toSessionState()
    }

    internal suspend fun forget(reason: SignOutReason) {
        tokenStore.clear()
        state.value = SessionState.SignedOut(reason)
    }

    private suspend fun completeSignIn(
        result: AppResult<AuthTokensDto, DataError.Network>,
    ): AppResult<Unit, DataError.Network> {
        val tokens = when (result) {
            is AppResult.Success -> result.data
            is AppResult.Error -> return result
        }
        tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
        return when (val account = refreshAccount()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> {
                // Without the profile the app can't route; start over on the next attempt.
                tokenStore.clear()
                state.value = SessionState.SignedOut()
                account
            }
        }
    }
}

/** The secret staging expects in `X-Dev-Auth-Secret` (blank locally). */
class DeveloperSignIn(val secret: String?)
