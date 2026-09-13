package com.falcon.tripingly.feature.map.presentation.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.falcon.tripingly.core.coroutines.DefaultCoroutineDispatchers
import com.falcon.tripingly.feature.map.data.datasource.AndroidLocationDataSource
import com.falcon.tripingly.feature.map.data.repository.LocationRepositoryImpl
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import com.falcon.tripingly.feature.map.presentation.viewmodel.MapViewModel

@Composable
actual fun rememberMapViewModel(): MapViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel {
        val dataSource = AndroidLocationDataSource(context)
        val dispatchers = DefaultCoroutineDispatchers()
        val repository = LocationRepositoryImpl(dataSource, dispatchers)
        val useCase = GetCurrentLocationUseCase(repository)
        MapViewModel(useCase, dispatchers)
    }
}
