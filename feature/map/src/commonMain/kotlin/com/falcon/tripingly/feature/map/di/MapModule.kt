package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    viewModel { params ->
        MapViewModel(
            tripId = params.get(),
            tripRepository = get(),
            markerRepository = get(),
            placeRepository = get(),
            routeRepository = get(),
            photoRepository = get(),
            socialRepository = get(),
            appConfigRepository = get(),
            sessionRepository = get(),
            getCurrentLocationUseCase = get(),
            dispatchers = get(),
        )
    }
    viewModel { params -> PhotosViewModel(owner = params.get(), photoRepository = get(), sessionRepository = get()) }
    viewModel { params -> StopCommentsViewModel(markerId = params.get(), socialRepository = get()) }
}
