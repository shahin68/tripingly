package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.network.NetworkJson
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
import com.falcon.tripingly.core.network.model.MeDto
import com.falcon.tripingly.core.storage.SecureStore
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SessionRepositoryImpl(
    private val authApi: AuthApi,
    private val accountApi: AccountApi,
    private val tokenStore: TokenStore,
    /** The last account the server sent, next to the tokens: the app opens from it (birth date included, so not in Room). */
    private val secureStore: SecureStore,
    sessionEvents: SessionEvents,
    /** Null when this build has no developer sign-in (production). */
    private val developerSignIn: DeveloperSignIn?,
    /** Everything else the app stores for the user; resolved lazily because platform modules bind some. */
    private val cleaners: () -> List<LocalDataCleaner>,
    /** Stops push to this device; runs on sign-out while the session still works. */
    private val unregisterPush: suspend () -> Unit,
    private val scope: CoroutineScope,
) : SessionRepository {

    private val state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val session: StateFlow<SessionState> = state.asStateFlow()

    override val developerSignInAvailable: Boolean = developerSignIn != null

    init {
        scope.launch {
            // The refresh already cleared the tokens; the rest of the local data and the screen follow.
            sessionEvents.sessionEnded.collect { reason ->
                endSession(
                    when (reason) {
                        SessionEndReason.REFRESH_REFUSED -> SignOutReason.SESSION_EXPIRED
                        SessionEndReason.ACCOUNT_SUSPENDED -> SignOutReason.ACCOUNT_SUSPENDED
                    },
                )
            }
        }
        // The server refused a Pro feature: Pro has ended there, so it ends here too, without asking again.
        scope.launch { sessionEvents.proEnded.collect { updatePro(emptySet(), proExpiresAt = null) } }
    }

    override suspend fun restore(): AppResult<Unit, DataError.Network> {
        if (tokenStore.get() == null) {
            // No session, so nothing local may be left over (e.g. the app died mid sign-out).
            endSession(reason = null)
            return AppResult.Success(Unit)
        }
        // The account saved last time opens the app at once, offline too; the server's answer follows in the background
        // (Shahin, 2026-11-22): signed in, the live connection asks for it as it connects (AccountSync); still
        // onboarding, there's no connection, so it's asked here.
        savedAccount()?.let { saved ->
            state.value = saved.toAccount().toSessionState()
            if (state.value is SessionState.Onboarding) scope.launch { refreshAccount() }
            return AppResult.Success(Unit)
        }
        state.value = SessionState.Restoring
        return when (val result = refreshAccount()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> {
                if (result.error is DataError.Network.Unauthorized) {
                    val current = state.value
                    endSession((current as? SessionState.SignedOut)?.reason)
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
        accountApi.me().also { result ->
            if (result is AppResult.Success) onAccountChanged(result.data)
        }.map { it.toAccount() }

    override fun updatePro(entitlements: Set<String>, proExpiresAt: Instant?) {
        state.update { current ->
            when (current) {
                is SessionState.SignedIn -> current.copy(account = current.account.copy(entitlements = entitlements, proExpiresAt = proExpiresAt))
                is SessionState.Onboarding -> current.copy(account = current.account.copy(entitlements = entitlements, proExpiresAt = proExpiresAt))
                else -> current
            }
        }
    }

    override suspend fun syncPro(): AppResult<Unit, DataError.Network> =
        accountApi.syncPro().map { updatePro(it.entitlements.toSet(), it.proExpiresAt) }

    override suspend fun signOut(reason: SignOutReason?) {
        tokenStore.get()?.let { tokens ->
            unregisterPush()
            // Best effort: the server revokes the session; offline, it simply expires.
            authApi.logout(LogoutDto(refreshToken = tokens.refreshToken))
        }
        endSession(reason)
    }

    /**
     * Every account the server sends: saved for the next start (as the server's JSON), then shown. An answer that
     * arrives after a sign-out is dropped, so it can't bring the session back.
     */
    internal suspend fun onAccountChanged(me: MeDto) {
        if (tokenStore.get() == null) return
        secureStore.put(ACCOUNT, NetworkJson.encodeToString(MeDto.serializer(), me))
        state.value = me.toAccount().toSessionState()
    }

    /** The account saved by [onAccountChanged]; null before the first one or when it can't be read (an older shape). */
    private suspend fun savedAccount(): MeDto? = secureStore.get(ACCOUNT)?.let {
        // A SerializationException is an IllegalArgumentException.
        try {
            NetworkJson.decodeFromString(MeDto.serializer(), it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal suspend fun forget(reason: SignOutReason) {
        endSession(reason)
    }

    /** The one way out of a session: tokens, every other local trace of the user, then the screen. */
    private suspend fun endSession(reason: SignOutReason?) {
        tokenStore.clear()
        secureStore.remove(ACCOUNT)
        cleaners().clearAll()
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
                endSession(reason = null)
                account
            }
        }
    }
}

private const val ACCOUNT = "account.me"

/** The secret staging expects in `X-Dev-Auth-Secret` (blank locally). */
class DeveloperSignIn(val secret: String?)
