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
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.domain.model.MapMarker
import com.falcon.tripingly.feature.map.generated.resources.*
import com.falcon.tripingly.feature.map.generated.resources.Res
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A stop tapped on the map, in a card that floats above its pin like the place card: its cover, name and
 * photo count, with its likes and comments. Tapping it opens the stop's photos; owners and editors also get
 * Add photos and the camera, and others ([canReport]) can report the stop.
 */
@Composable
fun StopCard(
    marker: MapMarker,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    canReport: Boolean,
    onReport: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                marker.coverPhotoId?.let { coverId ->
                    PhotoImage(
                        photoId = coverId,
                        url = marker.coverThumbUrl,
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
                        text = marker.title,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(liked = marker.likedByMe, count = marker.likeCount, onToggle = onToggleLike)
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
