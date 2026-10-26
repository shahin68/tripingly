package com.falcon.tripingly.feature.social.presentation.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Map
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_report
import com.falcon.tripingly.feature.social.generated.resources.social_save_trip
import com.falcon.tripingly.feature.social.generated.resources.social_saved_trip
import com.falcon.tripingly.feature.social.generated.resources.social_trip_stop_count
import com.falcon.tripingly.feature.social.presentation.component.previewTrip
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A public trip in Explore's grid: its cover filling the card, the owner's avatar (their profile) and the likes on
 * top, the name and "@owner · 6 stops" over the bottom. ⋮ or a long press saves it to My Trips or reports it;
 * trips of my own have nothing to save or report.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExploreTripCard(
    trip: PublicTrip,
    onOpen: () -> Unit,
    onOpenOwner: () -> Unit,
    onLike: () -> Unit,
    onToggleSave: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMine = trip.trip.role != TripRole.VIEWER
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ExploreCardHeight)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onOpen, onLongClick = { if (!isMine) menuOpen = true }),
    ) {
        val coverId = trip.trip.coverPhotoId
        val coverUrl = trip.trip.coverThumbUrl
        if (coverId != null && coverUrl != null) {
            PhotoImage(photoId = coverId, url = coverUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Outlined.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(PlaceholderIcon),
            )
        }
        AvatarCircle(
            name = trip.trip.owner.displayName,
            size = AvatarSize.Small,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CardInset)
                .clip(CircleShape)
                .clickable(onClick = onOpenOwner),
        )
        GlassSurface(modifier = Modifier.align(Alignment.TopEnd).padding(CardInset), shape = CircleShape, shadowElevation = 0.dp, blur = false) {
            LikeButton(liked = trip.likedByMe, count = trip.likeCount, onToggle = onLike)
        }
        CardFooter(trip, isMine, onMore = { menuOpen = true }, modifier = Modifier.align(Alignment.BottomStart))
        if (!isMine) {
            AppDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.align(Alignment.BottomEnd),
                actions = listOf(
                    DropdownAction(
                        label = stringResource(if (trip.trip.savedByMe) Res.string.social_saved_trip else Res.string.social_save_trip),
                        icon = if (trip.trip.savedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        selected = trip.trip.savedByMe,
                        onClick = onToggleSave,
                    ),
                    DropdownAction(stringResource(Res.string.social_report), isDestructive = true, icon = Icons.Outlined.Flag, onClick = onReport),
                ),
            )
        }
    }
}

/** The name and owner over a dark fade, so they read on any photo. */
@Composable
private fun CardFooter(trip: PublicTrip, isMine: Boolean, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))))
            .padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, top = MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f).padding(bottom = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall)) {
            Text(trip.trip.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = "@${trip.trip.owner.username} · " +
                    pluralStringResource(Res.plurals.social_trip_stop_count, trip.trip.markerCount, trip.trip.markerCount),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = MUTED_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isMine) {
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more), tint = Color.White)
            }
        } else {
            Box(Modifier.width(MaterialTheme.spacing.small))
        }
    }
}

internal val ExploreCardHeight = 220.dp
private val CardInset = 8.dp
private val PlaceholderIcon = 40.dp
private const val SCRIM_ALPHA = 0.6f
private const val MUTED_ALPHA = 0.8f

@Composable
private fun ExploreTripCardPreviewContent() {
    Row(
        Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
    ) {
        ExploreTripCard(previewTrip, {}, {}, {}, {}, {}, Modifier.weight(1f))
        ExploreTripCard(previewTrip.copy(likeCount = 12_400, likedByMe = false), {}, {}, {}, {}, {}, Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun ExploreTripCardPreview() {
    TripinglyTheme { ExploreTripCardPreviewContent() }
}

@Preview
@Composable
private fun ExploreTripCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { ExploreTripCardPreviewContent() }
}
