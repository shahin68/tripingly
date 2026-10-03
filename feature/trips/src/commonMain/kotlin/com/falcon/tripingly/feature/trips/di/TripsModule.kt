package com.falcon.tripingly.feature.trips.di

import com.falcon.tripingly.feature.trips.domain.usecase.CreateTripUseCase
import com.falcon.tripingly.feature.trips.domain.usecase.DeleteTripUseCase
import com.falcon.tripingly.feature.trips.domain.usecase.GetAllTripsUseCase
import com.falcon.tripingly.feature.trips.domain.usecase.UpdateTripDatesUseCase
import com.falcon.tripingly.feature.trips.domain.usecase.UpdateTripNameUseCase
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val tripsModule = module {
    factoryOf(::GetAllTripsUseCase)
    factoryOf(::CreateTripUseCase)
    factoryOf(::DeleteTripUseCase)
    factoryOf(::UpdateTripNameUseCase)
    factoryOf(::UpdateTripDatesUseCase)
    viewModelOf(::HomeViewModel)
}
