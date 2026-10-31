package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_stop_count
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import org.jetbrains.compose.resources.pluralStringResource

/** The trip's name, and its dates, stop count and (someone else's trip) owner under it. A tap opens [TripTitleCard]. */
@Composable
internal fun TripTitle(header: HeaderState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.height(GlassButtonDefaults.HeaderSize).clip(CircleShape).clickable(onClick = onClick), shape = CircleShape, blur = false) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (header.isLoading) {
                // Placeholder lengths only: drawn as the title's shimmering bars, never as text.
                Skeleton(Modifier.weight(1f)) { TitleLines("A trip's name", "1 Jan – 7 Jan · 6 stops") }
            } else {
                TitleLines(header.tripName, subtitle(header), Modifier.weight(1f))
            }
            if (header.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * The whole trip name and its details, for names too long for the title pill: a card across the header that the
 * header's buttons sit on, the name below them in full.
 */
@Composable
internal fun TripTitleCard(header: HeaderState, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, blur = false) {
        Column(
            modifier = Modifier.padding(
                start = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall,
                end = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall,
                top = GlassButtonDefaults.HeaderSize + MaterialTheme.spacing.small,
                bottom = MaterialTheme.spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            Text(header.tripName, style = MaterialTheme.typography.titleMedium)
            Text(subtitle(header), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun subtitle(header: HeaderState): String = listOfNotNull(
    header.dates,
    pluralStringResource(Res.plurals.map_stop_count, header.stopCount, header.stopCount),
    header.ownerLabel?.asString(),
).joinToString(" · ")

@Composable
private fun TitleLines(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.skeletonText(),
        )
    }
}
