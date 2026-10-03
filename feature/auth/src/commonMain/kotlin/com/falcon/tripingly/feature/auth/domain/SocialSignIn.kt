package com.falcon.tripingly.feature.auth.domain

/**
 * The platform sign-in SDKs (Credential Manager on Android, GoogleSignIn and
 * AuthenticationServices on iOS). They only produce the provider's token; the
 * session repository exchanges it with the backend.
 */
interface SocialSignIn {
    /** Sign in with Apple is offered on iOS only. */
    val appleAvailable: Boolean

    suspend fun google(): SocialSignInResult

    suspend fun apple(): SocialSignInResult
}

sealed interface SocialSignInResult {
    data class Google(val idToken: String) : SocialSignInResult

    data class Apple(
        val identityToken: String,
        val authorizationCode: String,
        /** Apple sends the name only on the first sign-in. */
        val givenName: String?,
        val familyName: String?,
    ) : SocialSignInResult

    /** The user closed the sheet; show nothing. */
    data object Cancelled : SocialSignInResult

    /** This build has no client ID for the provider yet. */
    data object NotConfigured : SocialSignInResult

    data object Failed : SocialSignInResult
}

/** Until the provider keys and SDKs are added: every provider reports [SocialSignInResult.NotConfigured]. */
internal class UnconfiguredSocialSignIn(override val appleAvailable: Boolean) : SocialSignIn {
    override suspend fun google(): SocialSignInResult = SocialSignInResult.NotConfigured
    override suspend fun apple(): SocialSignInResult = SocialSignInResult.NotConfigured
}
