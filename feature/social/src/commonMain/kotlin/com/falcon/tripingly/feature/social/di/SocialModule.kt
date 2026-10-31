package com.falcon.tripingly.feature.social.di

import com.falcon.tripingly.feature.social.presentation.explore.ExploreViewModel
import com.falcon.tripingly.feature.social.presentation.nearby.NearbyViewModel
import com.falcon.tripingly.feature.social.presentation.people.PeopleSearchViewModel
import com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel
import com.falcon.tripingly.feature.social.presentation.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val socialModule = module {
    viewModelOf(::ExploreViewModel)
    viewModelOf(::NearbyViewModel)
    viewModelOf(::PeopleSearchViewModel)
    viewModel { params ->
        ProfileViewModel(
            username = params.getOrNull(),
            userRepository = get(),
            socialRepository = get(),
            tripRepository = get(),
            sessionRepository = get(),
        )
    }
    viewModel { params -> PeopleViewModel(list = params.get(), userRepository = get()) }
}
