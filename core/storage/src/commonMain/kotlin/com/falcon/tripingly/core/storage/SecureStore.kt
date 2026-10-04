package com.falcon.tripingly.core.storage

/**
 * Small secrets (tokens) encrypted at rest: the iOS Keychain, or values encrypted
 * with an Android Keystore key. Values never leave the device in backups in
 * readable form, and nothing here is ever logged.
 */
interface SecureStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}
