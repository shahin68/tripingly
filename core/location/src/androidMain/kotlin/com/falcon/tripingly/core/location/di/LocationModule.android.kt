package com.falcon.tripingly.core.location.di

import com.falcon.tripingly.core.location.AndroidLocationDataSource
import com.falcon.tripingly.core.location.LocationDataSource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val locationPlatformModule: Module = module {
    single { AndroidLocationDataSource(get()) } bind LocationDataSource::class
}
