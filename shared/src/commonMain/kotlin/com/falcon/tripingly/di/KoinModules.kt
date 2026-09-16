package com.falcon.tripingly.di

import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.feature.map.data.repository.LocationRepository
import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect fun platformModule(): Module

val coreModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
}

val mapModule = module {
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    factoryOf(::GetCurrentLocationUseCase)
    viewModelOf(::MapViewModel)
}
