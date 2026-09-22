package com.falcon.tripingly.di

import com.falcon.tripingly.feature.map.domain.usecase.GetMarkersForDayUseCase
import com.falcon.tripingly.feature.map.domain.usecase.SaveMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkerUseCase
import com.falcon.tripingly.feature.map.domain.usecase.DeleteMarkersForDayUseCase
import com.falcon.tripingly.feature.home.domain.usecase.GetTripByIdUseCase
import com.falcon.tripingly.feature.home.domain.usecase.CreateTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.DeleteTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.UpdateTripNameUseCase
import com.falcon.tripingly.feature.home.domain.usecase.UpdateTripDatesUseCase
import com.falcon.tripingly.feature.home.domain.usecase.GetAllTripsUseCase
import com.falcon.tripingly.feature.home.data.repository.TripRepositoryImpl
import com.falcon.tripingly.feature.home.domain.repository.TripRepository
import com.falcon.tripingly.feature.home.presentation.screen.HomeViewModel
import com.falcon.tripingly.core.data.local.TripinglyDatabase
import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

expect fun platformModule(): Module

val coreModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single { get<TripinglyDatabase>().tripDao() }
    single { get<TripinglyDatabase>().markerDao() }
}

val homeModule = module {
    singleOf(::TripRepositoryImpl) bind TripRepository::class
    factoryOf(::GetAllTripsUseCase)
    factoryOf(::CreateTripUseCase)
    factoryOf(::DeleteTripUseCase)
    factoryOf(::UpdateTripNameUseCase)
    factoryOf(::UpdateTripDatesUseCase)
    factoryOf(::GetTripByIdUseCase)
    viewModelOf(::HomeViewModel)
}

val mapModule = module {
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    factoryOf(::GetCurrentLocationUseCase)
    factoryOf(::GetMarkersForDayUseCase)
    factoryOf(::SaveMarkerUseCase)
    factoryOf(::DeleteMarkerUseCase)
    factoryOf(::DeleteMarkersForDayUseCase)
    viewModel { params ->
        MapViewModel(
            tripId = params.get(),
            getCurrentLocationUseCase = get(),
            getMarkersForDayUseCase = get(),
            saveMarkerUseCase = get(),
            deleteMarkerUseCase = get(),
            deleteMarkersForDayUseCase = get(),
            getTripByIdUseCase = get(),
            dispatchers = get()
        )
    }
}
