package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.like
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The heart that floats on a corner, like [NewIndicator] does (the trip's heart on the title pill, Shahin 2026-11-19):
 * bigger than any other heart in the app, set apart from what's behind it by a ring in [ringColor] (the colour of what
 * it sits on). Tapping it on plays the like button's pop and dots. [enabled] false: it only shows (the copy riding the
 * trip card's corner).
 */
@Composable
fun HeartIndicator(
    liked: Boolean,
    onToggle: () -> Unit,
    ringColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var burst by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(HeartIndicatorDefaults.TouchSize)
            .toggleable(
                value = liked,
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { like ->
                    if (like) burst++
                    haptics.toggle(like)
                    onToggle()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BurstIcon(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(Res.string.like),
            tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            burst = burst,
            backdrop = Backdrop(Icons.Filled.Favorite, ringColor, HeartIndicatorDefaults.Ring),
            modifier = Modifier.size(HeartIndicatorDefaults.Size),
        )
    }
}

object HeartIndicatorDefaults {
    /** The heart's icon box: the only heart this big. */
    val Size = 28.dp

    /** Its tap area, the heart centred in it. */
    val TouchSize = 40.dp

    /** How far the ring reaches past the icon box on every side. */
    val Ring = 4.dp
}

@Composable
private fun HeartIndicatorSamples() {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        HeartIndicator(liked = false, onToggle = {}, ringColor = MaterialTheme.colorScheme.surface)
        HeartIndicator(liked = true, onToggle = {}, ringColor = MaterialTheme.colorScheme.surface)
    }
}

@Preview
@Composable
private fun HeartIndicatorPreview() = TripinglyTheme { HeartIndicatorSamples() }

@Preview
@Composable
private fun HeartIndicatorDarkPreview() = TripinglyTheme(darkTheme = true) { HeartIndicatorSamples() }
