package com.falcon.tripingly.core.storage.di

import org.koin.core.module.Module
import org.koin.dsl.module

val storageModule = module {
    includes(storagePlatformModule)
}

/** Binds the platform's [com.falcon.tripingly.core.storage.SecureStore]. */
internal expect val storagePlatformModule: Module
