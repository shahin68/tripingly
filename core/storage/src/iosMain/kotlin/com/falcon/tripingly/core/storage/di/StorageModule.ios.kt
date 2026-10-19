package com.falcon.tripingly.core.storage.di

import com.falcon.tripingly.core.storage.KeychainSecureStore
import com.falcon.tripingly.core.storage.SecureStore
import com.falcon.tripingly.core.storage.UI_STATE_FILE
import com.falcon.tripingly.core.storage.createUiStateStore
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val storagePlatformModule: Module = module {
    single<SecureStore> { KeychainSecureStore() }
    single { createUiStateStore("${documentDirectory()}/$UI_STATE_FILE") }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String = requireNotNull(
    NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path,
)
