package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.ButtonSize
import com.falcon.tripingly.core.designsystem.component.CommonsCredit
import com.falcon.tripingly.core.designsystem.component.CountLabel
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.ImageCredit
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_camera_take_photo
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content
import com.falcon.tripingly.feature.map.generated.resources.map_stop_add_photos
import com.falcon.tripingly.feature.map.generated.resources.map_stop_collapse
import com.falcon.tripingly.feature.map.generated.resources.map_stop_comment
import com.falcon.tripingly.feature.map.generated.resources.map_stop_comment_count
import com.falcon.tripingly.feature.map.generated.resources.map_stop_expand
import com.falcon.tripingly.feature.map.generated.resources.map_stop_more
import com.falcon.tripingly.feature.map.generated.resources.map_stop_no_photos
import com.falcon.tripingly.feature.map.generated.resources.map_stop_number
import com.falcon.tripingly.feature.map.generated.resources.map_stop_photo_count
import com.falcon.tripingly.feature.map.generated.resources.map_stop_report
import com.falcon.tripingly.feature.map.presentation.component.MapMarker
import com.falcon.tripingly.feature.map.presentation.map.preview.previewPlaceImages
import com.falcon.tripingly.feature.map.presentation.map.preview.previewStop
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A stop tapped on the map, floating above its pin: a slim pill (its image, name and counts) that expands into a
 * photo banner with the actions: hot, comments, and for owners and editors the camera and Add photos. ⋮ reports
 * the stop ([canReport], someone else's trip) or removes my things from it ([onRemoveMyContent]). Tapping the
 * pill or the banner opens the stop's photos. The image is the picked cover, else its place's (a Commons image
 * with its [coverCredit]).
 */
@Composable
fun StopCard(
    marker: MapMarker,
    /** The cover's credit, when it is a Wikimedia Commons image. */
    coverCredit: ImageCredit?,
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
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by rememberSaveable(marker.id) { mutableStateOf(initiallyExpanded) }
    val menu = listOfNotNull(
        onRemoveMyContent?.let { DropdownAction(stringResource(Res.string.map_remove_my_content), icon = Icons.Outlined.DeleteSweep, onClick = it) },
        DropdownAction(stringResource(Res.string.map_stop_report), isDestructive = true, icon = Icons.Outlined.Flag, onClick = onReport).takeIf { canReport },
    )
    GlassSurface(modifier = modifier.widthIn(max = MapCardMaxWidth), shape = MaterialTheme.shapes.extraLarge, onClick = onOpen, blur = false) {
        AnimatedContent(targetState = isExpanded) { expanded ->
            if (expanded) {
                Column(Modifier.padding(MaterialTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    PhotoBanner(
                        title = marker.title.asString(),
                        subtitle = countsLine(marker),
                        image = { StopImage(marker, Modifier.fillMaxSize()) },
                    ) {
                        BannerChip(stringResource(Res.string.map_stop_number, marker.orderNumber))
                        Box(Modifier.weight(1f))
                        if (menu.isNotEmpty()) MenuAnchor(menu) { open -> BannerButton(open, Icons.Default.MoreVert, stringResource(Res.string.map_stop_more)) }
                        BannerButton({ isExpanded = false }, Icons.Outlined.ExpandLess, stringResource(Res.string.map_stop_collapse))
                    }
                    coverCredit?.let { credit -> CommonsCredit(credit = credit, cropped = true, maxLines = 2, modifier = Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall)) }
                    StopActions(marker, canEdit, onAddPhotos, onTakePhoto, onToggleHot, onOpenComments)
                }
            } else {
                StopPill(marker, canEdit, menu, onAddPhotos, onExpand = { isExpanded = true })
            }
        }
    }
}

@Composable
private fun StopPill(marker: MapMarker, canEdit: Boolean, menu: List<DropdownAction>, onAddPhotos: () -> Unit, onExpand: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = PillHeight).padding(start = MaterialTheme.spacing.small, end = MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
    ) {
        StopImage(marker, Modifier.size(PillImageSize).clip(CircleShape))
        Column(Modifier.weight(1f, fill = false)) {
            Text(marker.title.asString(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
                val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                Text(stringResource(Res.string.map_stop_number, marker.orderNumber), style = MaterialTheme.typography.labelMedium, color = quiet)
                if (marker.photoCount > 0) CountLabel(Icons.Outlined.PhotoLibrary, marker.photoCount, color = quiet)
                if (marker.commentCount > 0) CountLabel(Icons.AutoMirrored.Outlined.Comment, marker.commentCount, color = quiet)
                if (marker.hotCount > 0) CountLabel(Icons.Outlined.LocalFireDepartment, marker.hotCount, color = quiet)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canEdit) {
                FilledIconButton(onClick = onAddPhotos) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(Res.string.map_stop_add_photos))
                }
            }
            if (menu.isNotEmpty()) MenuAnchor(menu) { open -> CardMenuIcon(open, stringResource(Res.string.map_stop_more)) }
            IconButton(onClick = onExpand) {
                Icon(Icons.Outlined.ExpandMore, contentDescription = stringResource(Res.string.map_stop_expand))
            }
        }
    }
}

@Composable
private fun StopActions(
    marker: MapMarker,
    canEdit: Boolean,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onToggleHot: () -> Unit,
    onOpenComments: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        if (marker.canMarkHot) {
            HotButton(
                hot = marker.hotByMe,
                count = marker.hotCount,
                onToggle = onToggleHot,
                modifier = Modifier.heightIn(min = ActionHeight).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            )
        }
        CountPill(Icons.AutoMirrored.Outlined.Comment, marker.commentCount, stringResource(Res.string.map_stop_comment), onOpenComments)
        if (canEdit) {
            ActionIconButton(onTakePhoto, Icons.Outlined.PhotoCamera, stringResource(Res.string.map_camera_take_photo))
            PrimaryButton(
                text = stringResource(Res.string.map_stop_add_photos),
                onClick = onAddPhotos,
                size = ButtonSize.Small,
                icon = Icons.Outlined.AddPhotoAlternate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The stop's image: its cover, or an empty frame while it has none. */
@Composable
private fun StopImage(marker: MapMarker, modifier: Modifier) {
    val coverId = marker.coverPhotoId
    if (coverId != null) {
        PhotoImage(photoId = coverId, url = marker.coverThumbUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** "1 photo · 1k comments", or "No photos yet". */
@Composable
private fun countsLine(marker: MapMarker): String = listOfNotNull(
    if (marker.photoCount > 0) pluralStringResource(Res.plurals.map_stop_photo_count, marker.photoCount, marker.photoCount) else stringResource(Res.string.map_stop_no_photos),
    marker.commentCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.map_stop_comment_count, it, it) },
).joinToString(" · ")

/** A ⋮ that opens [actions] in the app's menu. */
@Composable
internal fun MenuAnchor(actions: List<DropdownAction>, button: @Composable (open: () -> Unit) -> Unit) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        button { isOpen = true }
        AppDropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }, actions = actions)
    }
}

private val PillHeight = 64.dp
private val PillImageSize = 44.dp

/** An editor's stop, a viewer's (reportable), someone else's stop on a trip I edit, and one with a long name and no counts. */
internal class StopCardPreviewStates : PreviewParameterProvider<StopCardPreview> {
    override val values = sequenceOf(
        StopCardPreview(previewStop, canEdit = true, canReport = false, canRemoveMyContent = false),
        StopCardPreview(previewStop.copy(canDelete = false), canEdit = false, canReport = true, canRemoveMyContent = false),
        StopCardPreview(previewStop.copy(canDelete = false), canEdit = true, canReport = false, canRemoveMyContent = true, expanded = true),
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
            expanded = true,
        ),
    )
}

internal data class StopCardPreview(
    val stop: MapMarker,
    val canEdit: Boolean,
    val canReport: Boolean,
    val canRemoveMyContent: Boolean,
    val expanded: Boolean = false,
)

@Composable
private fun StopCardPreviewContent(card: StopCardPreview, credit: ImageCredit?) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(MaterialTheme.spacing.medium)) {
        StopCard(
            marker = card.stop,
            coverCredit = credit,
            canEdit = card.canEdit,
            onOpen = {},
            onAddPhotos = {},
            onTakePhoto = {},
            onToggleHot = {},
            onOpenComments = {},
            canReport = card.canReport,
            onReport = {},
            onRemoveMyContent = if (card.canRemoveMyContent) ({}) else null,
            initiallyExpanded = card.expanded,
        )
    }
}

@Preview
@Composable
private fun StopCardPreview(@PreviewParameter(StopCardPreviewStates::class) card: StopCardPreview) {
    TripinglyTheme { StopCardPreviewContent(card, previewPlaceImages.first().credit) }
}

@Preview
@Composable
private fun StopCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Column {
            StopCardPreviewContent(StopCardPreview(previewStop, canEdit = true, canReport = false, canRemoveMyContent = false), credit = null)
            StopCardPreviewContent(StopCardPreview(previewStop, canEdit = true, canReport = false, canRemoveMyContent = false, expanded = true), credit = null)
        }
    }
}
