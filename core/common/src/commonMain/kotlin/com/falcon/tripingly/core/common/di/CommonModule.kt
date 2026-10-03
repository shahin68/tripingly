package com.falcon.tripingly.core.common.di

import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.coroutines.DefaultCoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin qualifier of the app-wide [CoroutineScope] for work that outlives a screen. */
val ApplicationScope = named("applicationScope")

val commonModule = module {
    includes(commonPlatformModule)
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single<CoroutineScope>(ApplicationScope) { CoroutineScope(SupervisorJob() + get<CoroutineDispatchers>().default) }
}

internal expect val commonPlatformModule: Module
