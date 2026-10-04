package com.falcon.tripingly.feature.auth.platform

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.SocialSignInResult
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Google sign-in through Credential Manager: the "Sign in with Google" sheet
 * returns an ID token whose audience is the backend's Web client ID.
 */
internal class CredentialManagerSignIn(
    private val currentActivity: CurrentActivity,
    /** Null until the Google Cloud client IDs exist. */
    private val webClientId: String?,
) : SocialSignIn {

    override val appleAvailable = false

    override suspend fun google(): SocialSignInResult {
        val clientId = webClientId ?: return SocialSignInResult.NotConfigured
        val activity = currentActivity.activity ?: return SocialSignInResult.Failed
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                SocialSignInResult.Google(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                SocialSignInResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            SocialSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SocialSignInResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            SocialSignInResult.Failed
        }
    }

    // Sign in with Apple on Android is an open question (09-open-questions.md).
    override suspend fun apple(): SocialSignInResult = SocialSignInResult.NotConfigured
}
