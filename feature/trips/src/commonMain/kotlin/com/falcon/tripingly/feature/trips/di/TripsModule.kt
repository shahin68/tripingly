package com.falcon.tripingly.feature.trips.di

import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val tripsModule = module {
    viewModelOf(::HomeViewModel)
    viewModel { params ->
        TripMembersViewModel(
            tripId = params.get(),
            tripRepository = get(),
            inviteRepository = get(),
        )
    }
}
