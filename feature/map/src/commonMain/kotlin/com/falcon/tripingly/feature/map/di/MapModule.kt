package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel
import com.falcon.tripingly.feature.map.presentation.map.MapViewModel
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    viewModel { params ->
        MapViewModel(
            tripId = params[0],
            stopId = params[1],
            showMembers = params[2],
            tripRepository = get(),
            markerRepository = get(),
            placeRepository = get(),
            routeRepository = get(),
            photoRepository = get(),
            socialRepository = get(),
            appConfigRepository = get(),
            mapViews = get(),
            sessionRepository = get(),
            getCurrentLocationUseCase = get(),
            newActivityRepository = get(),
        )
    }
    viewModel { params -> PhotosViewModel(owner = params.get(), photoRepository = get(), sessionRepository = get()) }
    viewModel { params -> StopCommentsViewModel(markerId = params[0], people = params[1], socialRepository = get(), sessionRepository = get()) }
}
