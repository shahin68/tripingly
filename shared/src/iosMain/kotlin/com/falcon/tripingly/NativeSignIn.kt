package com.falcon.tripingly

import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.SocialSignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The iOS sign-in SDKs, implemented in Swift (`iosApp/NativeSignIn.swift`) and
 * handed to [MainViewController]. Callbacks carry only the provider's token.
 */
interface NativeSignIn {
    /** False until the GoogleSignIn package and the iOS client ID are added. */
    val googleConfigured: Boolean

    /** Calls back with the Google ID token; `(null, false)` when the user cancelled. */
    fun google(completion: (idToken: String?, failed: Boolean) -> Unit)

    /** Calls back with Apple's identity token and authorization code; all null and `failed = false` when cancelled. */
    fun apple(
        completion: (identityToken: String?, authorizationCode: String?, givenName: String?, familyName: String?, failed: Boolean) -> Unit,
    )

    /** Forgets the signed-in Google user kept by the SDK (Keychain). Apple keeps no app-side state. */
    fun signOut()
}

internal class NativeSocialSignIn(private val native: NativeSignIn) : SocialSignIn {
    override val appleAvailable = true

    override suspend fun google(): SocialSignInResult {
        if (!native.googleConfigured) return SocialSignInResult.NotConfigured
        return onMain { resume ->
            native.google { idToken, failed ->
                resume(
                    when {
                        idToken != null -> SocialSignInResult.Google(idToken)
                        failed -> SocialSignInResult.Failed
                        else -> SocialSignInResult.Cancelled
                    },
                )
            }
        }
    }

    override suspend fun apple(): SocialSignInResult = onMain { resume ->
        native.apple { identityToken, authorizationCode, givenName, familyName, failed ->
            resume(
                when {
                    identityToken != null && authorizationCode != null ->
                        SocialSignInResult.Apple(identityToken, authorizationCode, givenName, familyName)
                    failed -> SocialSignInResult.Failed
                    else -> SocialSignInResult.Cancelled
                },
            )
        }
    }

    /** The SDKs present UI, so they start on the main thread; each calls back once. */
    private suspend fun onMain(start: ((SocialSignInResult) -> Unit) -> Unit): SocialSignInResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                start { result -> if (continuation.isActive) continuation.resume(result) }
            }
        }
}
