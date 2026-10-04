package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import kotlinx.coroutines.flow.StateFlow

/** Sign-in, sign-out and who is signed in. The app shows a screen per [session] state. */
interface SessionRepository {
    val session: StateFlow<SessionState>

    /**
     * At app start: stored tokens → `GET /me`. Without tokens, or when the server
     * refuses them, the session is signed out. Any other failure (offline) leaves
     * it [SessionState.Restoring] and returns the error, so the app can offer a retry.
     */
    suspend fun restore(): AppResult<Unit, DataError.Network>

    suspend fun signInWithGoogle(idToken: String): AppResult<Unit, DataError.Network>

    suspend fun signInWithApple(
        identityToken: String,
        authorizationCode: String,
        givenName: String?,
        familyName: String?,
    ): AppResult<Unit, DataError.Network>

    /** The backend's developer sign-in (local and staging only). */
    suspend fun signInForDevelopment(subject: String, name: String?): AppResult<Unit, DataError.Network>

    /** Whether this build can use [signInForDevelopment]. */
    val developerSignInAvailable: Boolean

    /** Re-reads `GET /me`, e.g. after an onboarding step. */
    suspend fun refreshAccount(): AppResult<Account, DataError.Network>

    /** Revokes the session on the server (best effort) and forgets the tokens. */
    suspend fun signOut(reason: SignOutReason? = null)
}
