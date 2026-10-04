package com.falcon.tripingly.core.network

import com.falcon.tripingly.core.network.auth.AuthTokens
import com.falcon.tripingly.core.network.auth.SecureTokenStore
import com.falcon.tripingly.core.storage.SecureStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureTokenStoreTest {

    private class MapSecureStore : SecureStore {
        val values = mutableMapOf<String, String>()
        var reads = 0
        override suspend fun get(key: String) = values[key].also { reads++ }
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    @Test
    fun tokens_surviveANewStoreOverTheSameStorage() = runTest {
        val storage = MapSecureStore()
        SecureTokenStore(storage).save(AuthTokens("access-1", "refresh-1"))

        assertEquals(AuthTokens("access-1", "refresh-1"), SecureTokenStore(storage).get())
    }

    @Test
    fun storage_isReadOnce() = runTest {
        val storage = MapSecureStore()
        val store = SecureTokenStore(storage)

        repeat(3) { store.get() }

        assertEquals(2, storage.reads)
    }

    @Test
    fun clear_removesBothTokens() = runTest {
        val storage = MapSecureStore()
        val store = SecureTokenStore(storage)
        store.save(AuthTokens("access-1", "refresh-1"))

        store.clear()

        assertNull(SecureTokenStore(storage).get())
        assertTrue(storage.values.isEmpty())
    }
}
