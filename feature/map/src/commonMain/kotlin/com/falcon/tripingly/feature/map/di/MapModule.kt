package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val mapModule = module {
    includes(mapPlatformModule)
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    factoryOf(::GetCurrentLocationUseCase)
    viewModel { params ->
        MapViewModel(
            tripId = params.get(),
            tripRepository = get(),
            markerRepository = get(),
            placeRepository = get(),
            getCurrentLocationUseCase = get(),
            dispatchers = get(),
        )
    }
}

internal expect val mapPlatformModule: Module
