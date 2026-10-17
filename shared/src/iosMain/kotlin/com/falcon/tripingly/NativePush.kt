package com.falcon.tripingly

import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.deeplink.DeepLink
import com.falcon.tripingly.deeplink.DeepLinks
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Firebase Cloud Messaging on iOS, implemented in Swift (`iosApp/NativePush.swift`) and handed to
 * [MainViewController]. Firebase has no Kotlin binding.
 */
interface NativePush {
    /** Starts Firebase and asks APNs for this device's address. False when a value is missing: the build has no push. */
    fun start(projectId: String, senderId: String, appId: String, apiKey: String): Boolean

    /** Calls back with the FCM token, or null while there's none (APNs hasn't answered, or the app can't get pushes). */
    fun token(completion: (String?) -> Unit)

    /** Gets new tokens and tapped pushes, including one tapped before the app was ready. */
    fun setListener(listener: NativePushListener)
}

interface NativePushListener {
    fun onNewToken(token: String)

    /** A push was tapped; [deepLink] and [notificationId] come from its data. */
    fun onOpened(deepLink: String, notificationId: String?)
}

/** Tokens come from Firebase, a tapped push goes to [DeepLinks], like `MainActivity` does on Android. */
internal class IosPushMessaging(private val native: NativePush, private val deepLinks: DeepLinks) : PushMessaging, NativePushListener {
    private val tokens = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val newTokens: Flow<String> = tokens

    override suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        native.token { token -> if (continuation.isActive) continuation.resume(token) }
    }

    override fun onNewToken(token: String) {
        tokens.tryEmit(token)
    }

    override fun onOpened(deepLink: String, notificationId: String?) {
        deepLinks.open(DeepLink.parse(deepLink, notificationId))
    }
}
