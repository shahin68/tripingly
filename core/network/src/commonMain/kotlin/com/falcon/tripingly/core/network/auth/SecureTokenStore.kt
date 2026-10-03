package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.storage.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tokens in the [SecureStore] (Keychain / Keystore), kept in memory after the
 * first read because every request asks for them.
 */
class SecureTokenStore(private val secureStore: SecureStore) : TokenStore {
    private val mutex = Mutex()
    private var loaded = false
    private var tokens: AuthTokens? = null

    override suspend fun get(): AuthTokens? = mutex.withLock {
        if (!loaded) {
            val access = secureStore.get(ACCESS_TOKEN)
            val refresh = secureStore.get(REFRESH_TOKEN)
            tokens = if (access != null && refresh != null) AuthTokens(access, refresh) else null
            loaded = true
        }
        tokens
    }

    override suspend fun save(tokens: AuthTokens) = mutex.withLock {
        secureStore.put(ACCESS_TOKEN, tokens.accessToken)
        secureStore.put(REFRESH_TOKEN, tokens.refreshToken)
        this.tokens = tokens
        loaded = true
    }

    override suspend fun clear() = mutex.withLock {
        secureStore.remove(ACCESS_TOKEN)
        secureStore.remove(REFRESH_TOKEN)
        tokens = null
        loaded = true
    }

    private companion object {
        const val ACCESS_TOKEN = "auth.accessToken"
        const val REFRESH_TOKEN = "auth.refreshToken"
    }
}
