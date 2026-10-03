package com.falcon.tripingly.core.storage.di

import com.falcon.tripingly.core.storage.AndroidSecureStore
import com.falcon.tripingly.core.storage.SecureStore
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val storagePlatformModule: Module = module {
    single<SecureStore> { AndroidSecureStore(get()) }
}
