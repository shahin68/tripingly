package com.falcon.tripingly.core.model.account

/** Where the app is in the sign-in flow; the app shows a screen per state. */
sealed interface SessionState {
    /** Reading stored tokens and `GET /me` at start. */
    data object Restoring : SessionState

    data class SignedOut(val reason: SignOutReason? = null) : SessionState

    /** Signed in, but the profile or required consents are missing. */
    data class Onboarding(val account: Account) : SessionState

    data class SignedIn(val account: Account) : SessionState
}

enum class SignOutReason {
    /** The server refused the session (refresh token reused or revoked). */
    SESSION_EXPIRED,
    ACCOUNT_SUSPENDED,
    /** The birth date was under 16; the server deleted the account. */
    UNDER_AGE,
}
