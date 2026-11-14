package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.feature.map.presentation.comments.StopCommentsViewModel
import com.falcon.tripingly.feature.map.presentation.map.MapViewModel
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mapModule = module {
    includes(mapPlatformModule)
    viewModel { params ->
        MapViewModel(
            tripId = params[0],
            stopId = params[1],
            showMembers = params[2],
            showComments = params[3],
            tripRepository = get(),
            markerRepository = get(),
            placeRepository = get(),
            routeRepository = get(),
            socialRepository = get(),
            appConfigRepository = get(),
            mapViews = get(),
            sessionRepository = get(),
            getCurrentLocationUseCase = get(),
            newActivityRepository = get(),
            photoRepository = get(),
            computation = get<CoroutineDispatchers>().default,
        )
    }
    viewModel { params -> PhotosViewModel(owner = params.get(), photoRepository = get(), sessionRepository = get()) }
    viewModel { params -> StopCommentsViewModel(markerId = params[0], people = params[1], socialRepository = get(), sessionRepository = get()) }
}

/**
 * The map's warm-up, a `SignedInStart`: once the signed-in app is idle, one native map is made and dropped, so the SDK
 * has loaded its code, renderer and style before the first trip opens and that map is made as fast as any later one.
 */
internal expect val mapPlatformModule: Module
