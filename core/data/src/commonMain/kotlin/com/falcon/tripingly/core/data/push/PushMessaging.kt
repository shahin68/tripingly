package com.falcon.tripingly.core.data.push

import kotlinx.coroutines.flow.Flow

/**
 * The platform's push service: Firebase Cloud Messaging on Android. The app binds it in Koin where push
 * is set up; without a binding (iOS until APNs, or a build without the Firebase values) there is no push.
 */
interface PushMessaging {
    /** This device's push token, or null when there is none (no Google Play services, offline at first start). */
    suspend fun token(): String?

    /** A new token whenever the platform replaces the old one. */
    val newTokens: Flow<String>
}
