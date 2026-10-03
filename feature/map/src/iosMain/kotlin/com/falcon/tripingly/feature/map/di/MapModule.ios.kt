package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.data.datasource.IosLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val mapPlatformModule: Module = module {
    singleOf(::IosLocationDataSource) bind LocationDataSource::class
}
