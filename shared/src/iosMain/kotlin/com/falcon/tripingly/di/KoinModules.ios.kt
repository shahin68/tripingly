package com.falcon.tripingly.di

import com.falcon.tripingly.feature.map.data.datasource.IosLocationDataSource
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    singleOf(::IosLocationDataSource) bind LocationDataSource::class
}
