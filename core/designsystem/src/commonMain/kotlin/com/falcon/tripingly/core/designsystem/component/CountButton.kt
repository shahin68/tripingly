package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * An icon with its count to tap, with no container around it (e.g. a stop's comments on its card's photo): in the
 * content colour and as big as a [HotButton], so it reads as a button beside a quieter, smaller [CountLabel]. No padding
 * at the start, so the icon lines up with the text above it. [isNew] puts the red dot on the icon's top end, without a
 * ring (a photo behind it has no one colour to cut through).
 */
@Composable
fun CountButton(
    icon: ImageVector,
    contentDescription: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
) {
    Row(
        modifier = modifier
            .clickable(interactionSource = null, indication = ripple(bounded = false), role = Role.Button, onClick = onClick)
            .height(LikeButtonHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(CountLabelSize.Large.iconSize))
            if (isNew) NewIndicator(Modifier.align(Alignment.TopEnd).offset(x = NewDotOffset, y = -NewDotOffset), strokeWidth = 0.dp)
        }
        // Always shown, 0 too: every count reads the same, tappable or not.
        Text(text = compactCount(count), style = MaterialTheme.typography.labelLarge)
    }
}

private val NewDotOffset = 3.dp

@Composable
private fun CountButtonPreviewContent() {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountButton(Icons.AutoMirrored.Outlined.Comment, "Comments", 12, onClick = {})
        CountButton(Icons.AutoMirrored.Outlined.Comment, "Comments", 0, onClick = {}, isNew = true)
        CountButton(Icons.AutoMirrored.Outlined.Comment, "Comments", 12_480, onClick = {})
    }
}

@Preview
@Composable
private fun CountButtonPreview() {
    TripinglyTheme { CountButtonPreviewContent() }
}

@Preview
@Composable
private fun CountButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { CountButtonPreviewContent() }
}
