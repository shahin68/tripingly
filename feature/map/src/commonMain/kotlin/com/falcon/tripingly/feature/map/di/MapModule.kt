package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.GetTripByIdUseCase
import com.falcon.tripingly.feature.map.domain.usecase.SaveMarkerUseCase
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
    factoryOf(::GetMarkersForDayUseCase)
    factoryOf(::SaveMarkerUseCase)
    factoryOf(::DeleteMarkerUseCase)
    factoryOf(::DeleteMarkersForDayUseCase)
    factoryOf(::GetTripByIdUseCase)
    viewModel { params ->
        MapViewModel(
            tripId = params.get(),
            getCurrentLocationUseCase = get(),
            getMarkersForDayUseCase = get(),
            saveMarkerUseCase = get(),
            deleteMarkerUseCase = get(),
            deleteMarkersForDayUseCase = get(),
            getTripByIdUseCase = get(),
            dispatchers = get(),
        )
    }
}

internal expect val mapPlatformModule: Module
