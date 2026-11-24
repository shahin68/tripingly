package com.falcon.tripingly.feature.map.presentation.map.ui

import com.falcon.tripingly.feature.map.presentation.component.AlongTheWayPinColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_along_add
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.map.presentation.map.preview.previewAlongTheWay
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlace

/**
 * A place in a row of places (along the way, or around a pin): its photo or kind, its name over [subtitle]; + adds it
 * to day [dayNumber] (none for viewers). The place open on the map ([isSelected]) has a purple edge, the found places'
 * colour (Shahin, 2026-11-17).
 */
@Composable
internal fun NearPlaceCard(place: MapPlace, subtitle: String, dayNumber: Int?, isSelected: Boolean, onOpen: (MapPlace) -> Unit, onAdd: (MapPlace) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = { onOpen(place) },
        shape = MaterialTheme.shapes.medium,
        // The sheet's own glass colour, so the places stand out of the shaded card in both themes.
        color = GlassDefaults.opaqueTint,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(SelectedEdge, AlongTheWayPinColor).takeIf { isSelected },
        modifier = modifier.widthIn(max = AlongCardMaxWidth),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, end = MaterialTheme.spacing.small),
        ) {
            AlongTheWayImage(place)
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small).weight(1f, fill = false)) {
                Text(place.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (dayNumber != null) {
                FilledIconButton(onClick = { onAdd(place) }, modifier = Modifier.size(AddButtonSize)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.map_along_add, dayNumber), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * The place's photo, rounded, when it has one; its kind's icon otherwise (Shahin, 2026-11-17). The photo is as tall as
 * the card's text, so cards with and without one stay the same height.
 */
@Composable
private fun AlongTheWayImage(place: MapPlace) {
    val thumb = place.coverThumbUrl
    if (thumb != null) {
        PhotoImage(
            photoId = place.coverImageId ?: place.id,
            url = thumb,
            size = PhotoSize.Thumb,
            contentDescription = null,
            modifier = Modifier.size(AlongImageSize).clip(MaterialTheme.shapes.small),
        )
    } else {
        Icon(place.category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

private val AlongCardMaxWidth = 240.dp
private val AddButtonSize = 36.dp
private val SelectedEdge = 2.dp
private val AlongImageSize = 32.dp

/** A row of places as along the way shows them: one open on the map, one a viewer sees (no +), one with a long name. */
@Composable
private fun NearPlaceCardPreviewContent() {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        NearPlaceCard(previewPlace, subtitle = "120 m off · 15 min", dayNumber = 2, isSelected = true, onOpen = {}, onAdd = {})
        NearPlaceCard(previewAlongTheWay[0].place, subtitle = "350 m", dayNumber = null, isSelected = false, onOpen = {}, onAdd = {})
        NearPlaceCard(previewAlongTheWay[1].place, subtitle = "1.4 km off · 1 h 20 min", dayNumber = 2, isSelected = false, onOpen = {}, onAdd = {})
    }
}

@Preview(widthDp = 720)
@Composable
private fun NearPlaceCardPreview() {
    TripinglyTheme { NearPlaceCardPreviewContent() }
}

@Preview(widthDp = 720)
@Composable
private fun NearPlaceCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { NearPlaceCardPreviewContent() }
}
