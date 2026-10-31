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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.like
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background

/** A heart to tap with the like count; filled while I like it. Darker than a read-only [CountLabel] heart, so it reads as a button. */
@Composable
fun LikeButton(
    liked: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .toggleable(value = liked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .height(LikeButtonHeight)
            .padding(horizontal = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(Res.string.like),
            tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp).skeleton(CircleShape),
        )
        if (count > 0) {
            Text(text = compactCount(count), style = MaterialTheme.typography.labelLarge, modifier = Modifier.skeletonText())
        }
    }
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
