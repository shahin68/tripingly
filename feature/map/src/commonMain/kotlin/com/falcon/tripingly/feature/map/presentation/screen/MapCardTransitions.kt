package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.falcon.tripingly.core.designsystem.theme.spacing

/*
 * How the map's pin cards come and go. Each shows what it last showed while it leaves and only updates while it
 * stays, so no screen code keeps old values around for the exit.
 */

/**
 * A card over the camera's middle, where the tapped pin is: it grows out of the pin, just above it, and shrinks back
 * into it when it closes. A card for another pin replaces it; the same pin's card only updates.
 */
@Composable
internal fun <T : Any> PinCard(
    card: T?,
    key: (T) -> Any,
    pinClearance: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = card,
            contentKey = { it?.let(key) },
            transitionSpec = {
                (scaleIn(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeIn()) togetherWith
                    (scaleOut(transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut())
            },
            // [modifier] comes after the lift, so it sees where the card really is (a touch on it mustn't close it).
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - pinClearance.roundToPx())
                    }
                }
                .then(modifier),
        ) { shown ->
            if (shown != null) content(shown)
        }
    }
}
