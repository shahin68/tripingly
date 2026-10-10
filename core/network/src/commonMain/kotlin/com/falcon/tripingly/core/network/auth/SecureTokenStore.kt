package com.falcon.tripingly.core.network.auth

import com.falcon.tripingly.core.storage.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tokens in the [SecureStore] (Keychain / Keystore), kept in memory after the
 * first read because every request asks for them. Both tokens are one value, so
 * a process killed mid-save can't keep a new access token next to a refresh token
 * the server already rotated (sending that one again signs the user out).
 */
class SecureTokenStore(private val secureStore: SecureStore) : TokenStore {
    private val mutex = Mutex()
    private var loaded = false
    private var tokens: AuthTokens? = null

    override suspend fun get(): AuthTokens? = mutex.withLock {
        if (!loaded) {
            tokens = secureStore.get(TOKENS)?.split(SEPARATOR)?.let { parts ->
                if (parts.size == 2) AuthTokens(parts[0], parts[1]) else null
            }
            loaded = true
        }
        tokens
    }

    override suspend fun save(tokens: AuthTokens) = mutex.withLock {
        secureStore.put(TOKENS, tokens.accessToken + SEPARATOR + tokens.refreshToken)
        this.tokens = tokens
        loaded = true
    }

    override suspend fun clear() = mutex.withLock {
        secureStore.remove(TOKENS)
        tokens = null
        loaded = true
    }

    private companion object {
        const val TOKENS = "auth.tokens"

        // Neither a JWT nor a base64url refresh token contains a space.
        const val SEPARATOR = " "
    }
}
