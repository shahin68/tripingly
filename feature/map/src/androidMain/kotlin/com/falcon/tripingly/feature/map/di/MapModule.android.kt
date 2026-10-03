package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.data.datasource.AndroidLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val mapPlatformModule: Module = module {
    single { AndroidLocationDataSource(get()) } bind LocationDataSource::class
}
