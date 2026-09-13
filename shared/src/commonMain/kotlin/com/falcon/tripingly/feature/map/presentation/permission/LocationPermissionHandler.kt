package com.falcon.tripingly.feature.map.presentation.permission

import androidx.compose.runtime.Composable

/**
 * Multiplatform launcher that initiates the platform-specific location permission request flow.
 * Returns a lambda which, when invoked, triggers the permission dialog.
 */
@Composable
expect fun rememberLocationPermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit
