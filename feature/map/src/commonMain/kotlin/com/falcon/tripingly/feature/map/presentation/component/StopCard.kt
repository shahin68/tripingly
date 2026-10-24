package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.CommonsCredit
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.PlaceImage
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.ui.UiText

/**
 * A stop tapped on the map, in a card that floats above its pin like the place card: its cover (or, without
 * one, its place's first image, with its credit), name and photo count, with its likes and comments. Tapping
 * it opens the stop's photos; owners and editors also get Add photos and the camera, and others
 * ([canReport]) can report the stop.
 */
@Composable
fun StopCard(
    marker: MapMarker,
    /** The stop's place's first image, shown while the stop has no cover of its own. */
    placeImage: PlaceImage?,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleHot: () -> Unit,
    onOpenComments: () -> Unit,
    canReport: Boolean,
    onReport: () -> Unit,
    /** Someone else's stop on a trip I edit: I can't delete it, only my photos, comments and likes on it. */
    onRemoveMyContent: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            val fallback = placeImage?.takeIf { marker.coverPhotoId == null }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val coverId = marker.coverPhotoId ?: fallback?.id
                if (coverId != null) {
                    PhotoImage(
                        photoId = coverId,
                        url = if (fallback != null) fallback.thumbUrl else marker.coverThumbUrl,
                        size = PhotoSize.Thumb,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = MaterialTheme.spacing.medium)
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = marker.title.asString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (marker.photoCount > 0) {
                            pluralStringResource(Res.plurals.map_stop_photo_count, marker.photoCount, marker.photoCount)
                        } else {
                            stringResource(Res.string.map_stop_no_photos)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            fallback?.credit?.let { credit -> CommonsCredit(credit = credit, cropped = true, maxLines = 2) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marker.canMarkHot) HotButton(hot = marker.hotByMe, count = marker.hotCount, onToggle = onToggleHot)
                TextButton(onClick = onOpenComments) {
                    Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (marker.commentCount > 0) {
                            pluralStringResource(Res.plurals.map_stop_comment_count, marker.commentCount, marker.commentCount)
                        } else {
                            stringResource(Res.string.map_stop_comment)
                        },
                        modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onRemoveMyContent != null) {
                    IconButton(onClick = onRemoveMyContent) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(Res.string.map_remove_my_content))
                    }
                }
                if (canReport) {
                    IconButton(onClick = onReport) {
                        Icon(Icons.Outlined.Flag, contentDescription = stringResource(Res.string.map_stop_report))
                    }
                }
            }
            if (canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onAddPhotos, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.map_stop_add_photos), modifier = Modifier.padding(start = MaterialTheme.spacing.small))
                    }
                    FilledTonalIconButton(onClick = onTakePhoto) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(Res.string.map_camera_take_photo))
                    }
                }
            }
        }
    }
}

/** An editor's stop, a viewer's (reportable), someone else's stop on a trip I edit, and one with a long name and no counts. */
internal class StopCardPreviewStates : PreviewParameterProvider<StopCardPreview> {
    override val values = sequenceOf(
        StopCardPreview(previewStop, canEdit = true, canReport = false, canRemoveMyContent = false),
        StopCardPreview(previewStop.copy(canDelete = false, isByTripOwner = true), canEdit = false, canReport = true, canRemoveMyContent = false),
        StopCardPreview(previewStop.copy(canDelete = false), canEdit = true, canReport = false, canRemoveMyContent = true),
        StopCardPreview(
            previewStop.copy(
                title = UiText.Server("Musée national des Arts asiatiques – Guimet, the long name of a museum"),
                orderNumber = 12,
                photoCount = 0,
                hotCount = 0,
                hotByMe = false,
                commentCount = 0,
            ),
            canEdit = true,
            canReport = false,
            canRemoveMyContent = false,
        ),
    )
}

internal data class StopCardPreview(val stop: MapMarker, val canEdit: Boolean, val canReport: Boolean, val canRemoveMyContent: Boolean)

@Preview
@Composable
private fun StopCardPreview(@PreviewParameter(StopCardPreviewStates::class) card: StopCardPreview) {
    TripinglyTheme {
        StopCard(
            marker = card.stop,
            placeImage = previewPlaceImages.first(),
            canEdit = card.canEdit,
            onOpen = {},
            onAddPhotos = {},
            onTakePhoto = {},
            onToggleHot = {},
            onOpenComments = {},
            canReport = card.canReport,
            onReport = {},
            onRemoveMyContent = if (card.canRemoveMyContent) ({}) else null,
        )
    }
}

@Preview
@Composable
private fun StopCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        StopCard(
            marker = previewStop,
            placeImage = null,
            canEdit = true,
            onOpen = {},
            onAddPhotos = {},
            onTakePhoto = {},
            onToggleHot = {},
            onOpenComments = {},
            canReport = false,
            onReport = {},
            onRemoveMyContent = null,
        )
    }
}
