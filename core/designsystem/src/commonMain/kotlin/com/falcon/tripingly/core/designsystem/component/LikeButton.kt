package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.like
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background

/**
 * A heart to tap with the like count; filled while I like it, and liking it pops the heart with a burst of dots. No
 * padding at the start, so the heart lines up with the text above it. Darker than a read-only [CountLabel] heart, so
 * it reads as a button.
 */
@Composable
fun LikeButton(
    liked: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var burst by remember { mutableIntStateOf(0) }
    Row(
        modifier = modifier
            .toggleable(
                value = liked,
                interactionSource = null,
                indication = ripple(bounded = false),
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { like ->
                    if (like) burst++
                    onToggle()
                },
            )
            .height(LikeButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BurstIcon(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(Res.string.like),
            tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            burst = burst,
            modifier = Modifier.size(20.dp).skeleton(CircleShape),
        )
        if (count > 0) {
            Text(text = compactCount(count), style = MaterialTheme.typography.labelLarge, modifier = Modifier.skeletonText())
        }
    }
}

/**
 * A like count I can't give (e.g. on my own trip): a filled grey heart, so it reads neither as a button (outline)
 * nor as liked (red). As big as a [LikeButton] unless [size] says otherwise.
 */
@Composable
fun LikeCount(count: Int, modifier: Modifier = Modifier, size: CountLabelSize = CountLabelSize.Large) {
    CountLabel(icon = Icons.Filled.Favorite, count = count, color = MaterialTheme.colorScheme.onSurfaceVariant, size = size, modifier = modifier.skeletonText())
}

/** The like button's height, so things beside it (e.g. an avatar on an Explore card) can match it. */
val LikeButtonHeight = 32.dp

@Preview
@Composable
private fun LikeButtonPreview() {
    TripinglyTheme {
        Row {
            LikeButton(liked = true, count = 12, onToggle = {})
            LikeButton(liked = false, count = 0, onToggle = {})
            LikeCount(count = 3)
        }
    }
}

@Preview
@Composable
private fun LikeButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
            LikeButton(liked = true, count = 1_204, onToggle = {})
            LikeButton(liked = false, count = 3, onToggle = {})
        }
    }
}
