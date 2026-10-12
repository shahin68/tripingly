package com.falcon.tripingly.core.location.di

import com.falcon.tripingly.core.location.GetCurrentLocationUseCase
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.LocationRepositoryImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val locationModule = module {
    includes(locationPlatformModule)
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    factoryOf(::GetCurrentLocationUseCase)
}

/** Binds the platform's [com.falcon.tripingly.core.location.LocationDataSource]. */
internal expect val locationPlatformModule: Module
