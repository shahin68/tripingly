package com.falcon.tripingly.feature.map.presentation.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.feature.map.data.datasource.IosLocationDataSource
import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.viewmodel.MapViewModel

@Composable
actual fun rememberMapViewModel(): MapViewModel {
    return viewModel {
        val dataSource = IosLocationDataSource()
        val dispatchers = DefaultCoroutineDispatchers()
        val repository = LocationRepositoryImpl(dataSource, dispatchers)
        val useCase = GetCurrentLocationUseCase(repository)
        MapViewModel(useCase, dispatchers)
    }
}
