package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.safeTopAndSides
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import org.jetbrains.compose.resources.stringResource

/** The trip couldn't load: the error with Retry, and the back button where the header has it. */
@Composable
internal fun MapLoadError(message: String, onAction: (MapAction) -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ErrorScreen(onRetry = { onAction(MapAction.Header.Retry) }, message = message, modifier = Modifier.safeDrawingPadding())
        GlassIconButton(
            onClick = { onAction(MapAction.Header.Back) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeTopAndSides).padding(MaterialTheme.spacing.medium),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
    }
}
