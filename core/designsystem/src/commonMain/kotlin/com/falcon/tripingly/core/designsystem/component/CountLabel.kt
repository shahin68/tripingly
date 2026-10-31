package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * A small icon with a count, e.g. hot, likes, comments or photos on a card. All counts share one quiet color
 * (hot included) and big numbers are shortened: 1.2k, 12k, 1.2M.
 */
@Composable
fun CountLabel(
    icon: ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: CountLabelSize = CountLabelSize.Small,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isLarge = size == CountLabelSize.Large
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (isLarge) LargeCountIconSize else CountIconSize))
        Text(text = compactCount(count), color = color, style = if (isLarge) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
    }
}

/** A count as people read it at a glance: 999, 1k, 1.2k, 12k, 120k, 1.2M. */
fun compactCount(count: Int): String {
    fun short(value: Int, unit: Int, suffix: String): String {
        val tenths = value / (unit / 10)
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (whole >= 10 || fraction == 0) "$whole$suffix" else "$whole.$fraction$suffix"
    }
    return when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> short(count, 1_000, "k")
        else -> short(count, 1_000_000, "M")
    }
}

/** Small for counts among other details; Large as big as a [LikeButton] or [HotButton], where it stands for one (e.g. my own trip's likes). */
enum class CountLabelSize { Small, Large }

private val CountIconSize = 16.dp
private val LargeCountIconSize = 20.dp

@Composable
private fun CountLabelPreviewContent() {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        CountLabel(Icons.Outlined.LocalFireDepartment, 1_204, color = muted)
        CountLabel(Icons.Outlined.FavoriteBorder, 87, color = muted)
        CountLabel(Icons.Outlined.ChatBubbleOutline, 12_480, color = muted)
        CountLabel(Icons.Outlined.PhotoCamera, 1_250_000, color = muted)
        CountLabel(Icons.Outlined.FavoriteBorder, 3, color = muted, size = CountLabelSize.Large)
    }
}

@Preview
@Composable
private fun CountLabelPreview() {
    TripinglyTheme { CountLabelPreviewContent() }
}

@Preview
@Composable
private fun CountLabelDarkPreview() {
    TripinglyTheme(darkTheme = true) { CountLabelPreviewContent() }
}
