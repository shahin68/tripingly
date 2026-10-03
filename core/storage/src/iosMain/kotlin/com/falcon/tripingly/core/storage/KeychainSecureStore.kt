package com.falcon.tripingly.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Generic-password items in the Keychain, readable after the first unlock (so a
 * background refresh works) and never synced to other devices. Keychain items
 * survive uninstalling the app, so a fresh install starts by deleting them.
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainSecureStore(private val service: String = SERVICE) : SecureStore {

    private val mutex = Mutex()
    private var checkedFreshInstall = false

    override suspend fun get(key: String): String? = locked {
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = withQuery(
                key,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            ) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
    }

    override suspend fun put(key: String, value: String) = locked {
        withQuery(key) { SecItemDelete(it) }
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding)
        val cfData = CFBridgingRetain(data)
        try {
            withQuery(
                key,
                kSecValueData to cfData,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ) { SecItemAdd(it, null) }
        } finally {
            if (cfData != null) CFRelease(cfData)
        }
        Unit
    }

    override suspend fun remove(key: String) = locked {
        withQuery(key) { SecItemDelete(it) }
        Unit
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!checkedFreshInstall) {
                deleteLeftoversFromAnEarlierInstall()
                checkedFreshInstall = true
            }
            block()
        }
    }

    private fun deleteLeftoversFromAnEarlierInstall() {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.boolForKey(INSTALLED_FLAG)) return
        withQuery(account = null) { SecItemDelete(it) }
        defaults.setBool(true, forKey = INSTALLED_FLAG)
    }

    /** Builds the item query (class, service, account plus [extra]) and releases it after [block]. */
    private fun <T> withQuery(
        account: String?,
        vararg extra: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFMutableDictionaryRef?) -> T,
    ): T {
        val query = CFDictionaryCreateMutable(null, 0.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val owned = listOfNotNull(CFBridgingRetain(service), account?.let { CFBridgingRetain(it) })
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, owned[0])
            if (account != null) CFDictionaryAddValue(query, kSecAttrAccount, owned[1])
            extra.forEach { (k, v) -> CFDictionaryAddValue(query, k, v) }
            return block(query)
        } finally {
            owned.forEach { CFRelease(it) }
            CFRelease(query)
        }
    }

    private companion object {
        const val SERVICE = "com.falcon.tripingly.secure"
        const val INSTALLED_FLAG = "tripinly.secureStore.installed"
    }
}
