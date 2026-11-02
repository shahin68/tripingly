package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * A round glass button floating over content: back over the map, the location button. [blur] off over the native
 * map, which can't be blurred (see [GlassSurface]).
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    size: Dp = GlassButtonDefaults.Size,
    content: @Composable () -> Unit,
) {
    GlassSurface(modifier = modifier.size(size), shape = CircleShape, onClick = onClick, blur = blur) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Glass buttons joined in one pill with thin dividers between them, side by side: search and ⋮ over the map. */
@Composable
fun GlassButtonRow(
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    height: Dp = GlassButtonDefaults.Size,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.height(height), shape = CircleShape, blur = blur) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** Glass buttons joined in one pill, stacked: fit-the-day over the location button on the map. */
@Composable
fun GlassButtonColumn(
    modifier: Modifier = Modifier,
    blur: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.width(GlassButtonDefaults.Size), shape = CircleShape, blur = blur) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.extraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** The thin line between two buttons of a [GlassButtonRow]. */
@Composable
fun GlassButtonDivider() {
    VerticalDivider(Modifier.height(GlassButtonDefaults.DividerLength), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The thin line between two buttons of a [GlassButtonColumn]. */
@Composable
fun GlassButtonColumnDivider() {
    HorizontalDivider(Modifier.width(GlassButtonDefaults.DividerLength), color = MaterialTheme.colorScheme.outlineVariant)
}

object GlassButtonDefaults {
    /** Every floating glass control is this tall (or wide): the header pills, the map buttons. */
    val Size = 48.dp

    /** A screen header's pills over the map (back, title, search and ⋮): one height, room for a two-line title. */
    val HeaderSize = 56.dp
    internal val DividerLength = 20.dp
}

@Composable
private fun GlassIconButtonPreviewContent() {
    Row(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        GlassButtonRow {
            IconButton(onClick = {}) { Icon(Icons.Outlined.Search, contentDescription = null) }
            GlassButtonDivider()
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = null) }
        }
        GlassButtonColumn {
            IconButton(onClick = {}) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null) }
            GlassButtonColumnDivider()
            IconButton(onClick = {}) { Icon(Icons.Outlined.NearMe, contentDescription = null) }
        }
    }
}

@Preview
@Composable
private fun GlassIconButtonPreview() {
    TripinglyTheme { GlassIconButtonPreviewContent() }
}

@Preview
@Composable
private fun GlassIconButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { GlassIconButtonPreviewContent() }
}
