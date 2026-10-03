package com.falcon.tripingly.core.common.di

import com.falcon.tripingly.core.common.util.AndroidShareManager
import com.falcon.tripingly.core.common.util.ShareManager
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val commonPlatformModule: Module = module {
    single<ShareManager> { AndroidShareManager(get()) }
}
