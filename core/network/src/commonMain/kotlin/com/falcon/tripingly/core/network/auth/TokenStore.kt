package com.falcon.tripingly.core.network.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AuthTokens(val accessToken: String, val refreshToken: String)

/**
 * Holds the signed-in user's tokens. Stage 3 replaces [InMemoryTokenStore] with
 * Keychain / Keystore-backed storage; tokens are never logged.
 */
interface TokenStore {
    suspend fun get(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private var tokens: AuthTokens? = null

    override suspend fun get(): AuthTokens? = mutex.withLock { tokens }
    override suspend fun save(tokens: AuthTokens) = mutex.withLock { this.tokens = tokens }
    override suspend fun clear() = mutex.withLock { tokens = null }
}
