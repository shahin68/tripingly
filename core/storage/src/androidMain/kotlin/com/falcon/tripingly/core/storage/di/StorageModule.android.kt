package com.falcon.tripingly.core.storage.di

import android.content.Context
import com.falcon.tripingly.core.storage.AndroidSecureStore
import com.falcon.tripingly.core.storage.SecureStore
import com.falcon.tripingly.core.storage.UI_STATE_FILE
import com.falcon.tripingly.core.storage.createUiStateStore
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val storagePlatformModule: Module = module {
    single<SecureStore> { AndroidSecureStore(get()) }
    single { createUiStateStore(get<Context>().filesDir.resolve(UI_STATE_FILE).absolutePath) }
}
