package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/*
 * What the stop and place cards share: the photo banner with the name over a dark fade, the small glass chips and
 * buttons on it, and the count pills in the card's action row.
 */

/**
 * A photo [image] filling a rounded banner, with [top] chips and buttons over it and the [title] and [subtitle] in
 * white over a dark fade at the bottom.
 */
@Composable
internal fun PhotoBanner(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    image: @Composable BoxScope.() -> Unit,
    top: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(BannerHeight).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant)) {
        image()
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            content = top,
        )
        Column(Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A light chip on a photo banner ("Stop 3", "🔥 2.3k"). */
@Composable
internal fun BannerChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = CircleShape, color = BannerControlColor, contentColor = Color.Black) {
        Row(
            modifier = Modifier.heightIn(min = BannerControlSize).padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A light round button on a photo banner: ⋮, collapse. */
@Composable
internal fun BannerButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(BannerControlSize), shape = CircleShape, color = BannerControlColor, contentColor = Color.Black) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp)) }
    }
}

/** A count in the card's action row ("💬 1k"), tappable; the icon alone while there's none. */
@Composable
internal fun CountPill(icon: ImageVector, count: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.heightIn(min = ActionHeight).padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
            if (count > 0) Text(compactCount(count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A round icon button in the card's action row, the same height as the [CountPill]s. */
@Composable
internal fun ActionIconButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(ActionHeight), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp)) }
    }
}

/** The ⋮ of a card that isn't over a photo. */
@Composable
internal fun CardMenuIcon(onClick: () -> Unit, contentDescription: String) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        IconButton(onClick = onClick) { Icon(Icons.Default.MoreVert, contentDescription = contentDescription) }
    }
}

/** The cards' widest, so they don't stretch across a tablet or a landscape phone. */
internal val MapCardMaxWidth = 360.dp
internal val ActionHeight = 40.dp
private val BannerHeight = 132.dp
private val BannerControlSize = 32.dp
private val BannerControlColor = Color.White.copy(alpha = 0.85f)

@Composable
private fun MapCardPartsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        PhotoBanner(title = "Harrachgasse", subtitle = "1 photo · 1k comments", image = {}) {
            BannerChip("Stop 3")
            BannerChip("2.3k", icon = Icons.Outlined.LocalFireDepartment)
            Box(Modifier.weight(1f))
            BannerButton(onClick = {}, icon = Icons.Default.MoreVert, contentDescription = "More")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            CountPill(Icons.Outlined.LocalFireDepartment, 1_200, "Hot", {})
            CountPill(Icons.AutoMirrored.Outlined.Comment, 0, "Comment", {})
            ActionIconButton({}, Icons.AutoMirrored.Outlined.Comment, "Comment")
        }
    }
}

@Preview
@Composable
private fun MapCardPartsPreview() {
    TripinglyTheme { MapCardPartsPreviewContent() }
}

@Preview
@Composable
private fun MapCardPartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapCardPartsPreviewContent() }
}
