package com.falcon.tripingly.push

import android.content.Context
import com.falcon.tripingly.BuildConfig
import com.falcon.tripingly.core.data.push.PushMessaging
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Firebase Cloud Messaging. Tokens come from Firebase; new ones from [TripinglyMessagingService]. */
internal class FcmPushMessaging : PushMessaging {
    private val tokens = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val newTokens: Flow<String> = tokens

    override suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // No Google Play services, or Firebase couldn't be reached: no push for now.
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    fun onNewToken(token: String) {
        tokens.tryEmit(token)
    }

    companion object {
        /**
         * Starts Firebase with the values from `local.properties` (google-services.json isn't in the repo).
         * False when they're missing: the build has no push.
         */
        fun initialize(context: Context): Boolean {
            val values = listOf(BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY)
            if (values.any { it.isBlank() }) return false
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .build(),
                )
            }
            return true
        }
    }
}
