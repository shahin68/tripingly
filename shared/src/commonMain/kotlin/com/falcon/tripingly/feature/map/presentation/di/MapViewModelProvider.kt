package com.falcon.tripingly.feature.map.presentation.di

import androidx.compose.runtime.Composable
import com.falcon.tripingly.feature.map.presentation.screen.MapViewModel

@Composable
expect fun rememberMapViewModel(): MapViewModel
