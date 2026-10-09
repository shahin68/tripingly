package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A place on the map: a place liked on Tripinly gets a heart pin, an OpenStreetMap place a small dot.
 * Drawn into a bitmap by the map, so it takes no clicks itself.
 */
@Composable
fun PlacePin(isTripinly: Boolean, modifier: Modifier = Modifier) {
    if (isTripinly) {
        Box(
            modifier = modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(14.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

/** Several Tripinly places while zoomed out, with how many. */
@Composable
fun PlaceClusterPin(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onTertiary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
