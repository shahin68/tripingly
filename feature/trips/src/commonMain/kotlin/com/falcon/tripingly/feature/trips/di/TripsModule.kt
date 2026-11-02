package com.falcon.tripingly.feature.trips.di

import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.feature.trips.presentation.members.TripMembersViewModel
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tripsModule = module {
    viewModel {
        HomeViewModel(
            tripRepository = get(),
            placeRepository = get(),
            userRepository = get(),
            sessionRepository = get(),
            today = DateUtils::today,
        )
    }
    viewModel { params ->
        TripMembersViewModel(
            tripId = params.get(),
            tripRepository = get(),
            inviteRepository = get(),
            userRepository = get(),
        )
    }
}
