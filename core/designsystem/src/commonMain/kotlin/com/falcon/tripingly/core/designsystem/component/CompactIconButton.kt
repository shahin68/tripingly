package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/**
 * A round icon button as tall as [LikeButton] (32 dp), for a card row that must stay as tall as its text, e.g. a
 * card's ⋮ beside its title or at the end of a person row, so cards with and without it are the same height.
 */
@Composable
fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(LikeButtonHeight)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(CompactIconSize).skeleton(CircleShape))
    }
}

private val CompactIconSize = 20.dp

/** Space between a [CompactIconButton]'s edge and its glyph: a row ending with one pads its end this much less. */
val CompactIconInset = (LikeButtonHeight - CompactIconSize) / 2

@Composable
private fun CompactIconButtonPreviewContent() {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        LikeButton(liked = false, count = 3, onToggle = {})
        CompactIconButton(Icons.Default.MoreVert, contentDescription = null, onClick = {})
    }
}

@Preview
@Composable
private fun CompactIconButtonPreview() {
    TripinglyTheme { CompactIconButtonPreviewContent() }
}

@Preview
@Composable
private fun CompactIconButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { CompactIconButtonPreviewContent() }
}
