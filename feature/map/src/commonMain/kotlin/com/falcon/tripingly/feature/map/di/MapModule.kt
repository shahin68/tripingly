package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel
import com.falcon.tripingly.feature.map.presentation.photos.StopPhotosViewModel
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
            photoRepository = get(),
            socialRepository = get(),
            getCurrentLocationUseCase = get(),
            dispatchers = get(),
        )
    }
    viewModel { params -> StopPhotosViewModel(markerId = params.get(), photoRepository = get()) }
    viewModel { params -> StopCommentsViewModel(markerId = params.get(), socialRepository = get()) }
}
