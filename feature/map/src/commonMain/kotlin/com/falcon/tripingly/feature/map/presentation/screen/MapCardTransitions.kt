package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * How the map's cards come and go. Each shows what it last showed while it leaves and only updates while it stays,
 * so no screen code keeps old values around for the exit.
 */

/** A bottom card that slides up from below. */
@Composable
internal fun <T : Any> SlidingCard(card: T?, key: (T) -> Any, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = card,
        contentKey = { it?.let(key) },
        // The size changes at once (the bottom cards' height is the map's padding); the column around animates it.
        transitionSpec = {
            ((fadeIn() + slideInVertically { it }) togetherWith (fadeOut() + slideOutVertically { it }))
                .using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        modifier = Modifier.fillMaxWidth(),
    ) { shown ->
        if (shown != null) content(shown)
    }
}

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
            modifier = modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .layout { measurable, constraints ->
                    val measured = measurable.measure(constraints)
                    layout(measured.width, measured.height) {
                        measured.place(0, -measured.height / 2 - pinClearance.roundToPx())
                    }
                },
        ) { shown ->
            if (shown != null) content(shown)
        }
    }
}
