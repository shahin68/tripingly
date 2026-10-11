package com.falcon.tripingly.core.data.push

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.core.network.AppLanguage
import com.falcon.tripingly.core.network.model.MarkReadDto
import com.falcon.tripingly.core.network.model.RegisterDeviceDto
import com.falcon.tripingly.core.network.push.PushApi
import com.falcon.tripingly.core.storage.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps this device registered for push (`PUT /me/devices/{token}`). The server is asked only when the
 * account, the token or the app's language changed since the last registration, which is remembered on
 * the device. Sign-out unregisters the device before the session ends.
 */
internal class PushRegistration(
    /** Null where push isn't set up. */
    private val messaging: PushMessaging?,
    private val api: PushApi,
    private val store: SecureStore,
    private val config: ApiConfig,
    private val language: AppLanguage,
    /** The signed-in user's id. */
    private val currentUserId: () -> String?,
    private val scope: CoroutineScope,
) : PushNotifications {

    private val lock = Mutex()
    private var isFollowingTokens = false

    override fun start() {
        val messaging = messaging ?: return
        scope.launch { messaging.token()?.let { register(it) } }
        if (!isFollowingTokens) {
            isFollowingTokens = true
            scope.launch { messaging.newTokens.collect { register(it) } }
        }
    }

    override suspend fun opened(notificationId: String) {
        api.markRead(MarkReadDto(ids = listOf(notificationId)))
    }

    private suspend fun register(token: String) = lock.withLock {
        val userId = currentUserId() ?: return@withLock
        val locale = language.tag()
        val registration = "$userId $token $locale"
        if (store.get(KEY) == registration) return@withLock
        val platform = if (config.platform == "ios") RegisterDeviceDto.Platform.IOS else RegisterDeviceDto.Platform.ANDROID
        if (api.registerDevice(token, RegisterDeviceDto(platform, locale)) is AppResult.Success) store.put(KEY, registration)
    }

    /** Before sign-out, while the session still works: this device gets no more pushes for the account. */
    suspend fun unregister() = lock.withLock {
        if (store.get(KEY) == null) return@withLock
        // Best effort: offline, the token stays with the account until someone else registers it.
        messaging?.token()?.let { api.removeDevice(it) }
        store.remove(KEY)
    }

    /** The next account registers again. */
    suspend fun clear() = lock.withLock { store.remove(KEY) }

    private companion object {
        const val KEY = "push.registration"
    }
}
