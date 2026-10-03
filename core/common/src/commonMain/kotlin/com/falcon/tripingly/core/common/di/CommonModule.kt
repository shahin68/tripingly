package com.falcon.tripingly.core.common.di

import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

val commonModule = module {
    includes(commonPlatformModule)
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
}

internal expect val commonPlatformModule: Module
