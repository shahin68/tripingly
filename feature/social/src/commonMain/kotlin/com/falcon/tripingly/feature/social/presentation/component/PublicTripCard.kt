package com.falcon.tripingly.feature.social.presentation.component

import androidx.compose.foundation.shape.CircleShape
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.component.skeleton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.CountLabel
import com.falcon.tripingly.core.designsystem.component.DropdownAction
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.LikeButtonHeight
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.timeAgo
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_ask_to_join
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.generated.resources.social_report
import com.falcon.tripingly.feature.social.generated.resources.social_save_trip
import com.falcon.tripingly.feature.social.generated.resources.social_saved_trip
import com.falcon.tripingly.feature.social.generated.resources.social_trip_created
import com.falcon.tripingly.feature.social.generated.resources.social_trip_private
import com.falcon.tripingly.feature.social.generated.resources.social_trip_save_count
import com.falcon.tripingly.feature.social.generated.resources.social_trip_stop_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A public trip on a profile: its cover, name, dates and counts, and its likes. ⋮ or a long press saves it to My
 * Trips (read-only) or reports it; trips of my own have no ⋮ and only show their like count, quieter than a heart to
 * tap. The same card for both, as tall either way.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PublicTripCard(
    trip: PublicTrip,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onToggleSave: () -> Unit,
    onAskToJoin: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMine = trip.trip.role != TripRole.VIEWER
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Keeps the ripple inside the card's rounded corners.
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onOpen, onLongClick = { if (!isMine) menuOpen = true }),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // Symmetric: the cover sits 8 dp from the top, bottom and start. The end column is as tall as the cover: ⋮ at the
        // top (someone else's trips only), the heart at the bottom, so every card is as tall, mine or not.
        val inset = MaterialTheme.spacing.small
        Row(
            modifier = Modifier.padding(start = inset, top = inset, bottom = inset),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TripCover(trip)
            Column(Modifier.weight(1f)) {
                Text(trip.trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
                DateUtils.formatRange(trip.trip.startDate, trip.trip.endDate)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.skeletonText())
                }
                DetailLine(
                    buildList {
                        add(pluralStringResource(Res.plurals.social_trip_stop_count, trip.trip.markerCount, trip.trip.markerCount))
                        if (trip.saveCount > 0) add(pluralStringResource(Res.plurals.social_trip_save_count, trip.saveCount, trip.saveCount))
                        if (trip.trip.visibility == TripVisibility.PRIVATE) add(stringResource(Res.string.social_trip_private))
                    }.joinToString(" · "),
                )
                DetailLine(stringResource(Res.string.social_trip_created, timeAgo(trip.trip.createdAt)))
            }
            Column(Modifier.height(CoverSize), horizontalAlignment = Alignment.End) {
                if (!isMine) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more), modifier = Modifier.skeleton(CircleShape))
                        }
                        AppDropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            actions = publicTripActions(trip, onToggleSave, onAskToJoin, onReport),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (isMine) {
                    // My own trip: its likes are something to read, not to give; quieter than a heart I can tap.
                    CountLabel(
                        icon = Icons.Outlined.FavoriteBorder,
                        count = trip.likeCount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(LikeButtonHeight).padding(horizontal = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall).skeletonText(),
                    )
                } else {
                    LikeButton(liked = trip.likedByMe, count = trip.likeCount, onToggle = onLike, modifier = Modifier.padding(end = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall))
                }
            }
        }
    }
}

/** Someone else's public trip's ⋮ menu, on every card that shows one: save, ask to join, report. */
@Composable
internal fun publicTripActions(trip: PublicTrip, onToggleSave: () -> Unit, onAskToJoin: () -> Unit, onReport: () -> Unit): List<DropdownAction> = listOf(
    DropdownAction(
        label = stringResource(if (trip.trip.savedByMe) Res.string.social_saved_trip else Res.string.social_save_trip),
        icon = if (trip.trip.savedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
        selected = trip.trip.savedByMe,
        onClick = onToggleSave,
    ),
    DropdownAction(stringResource(Res.string.social_ask_to_join), icon = Icons.Outlined.PersonAdd, onClick = onAskToJoin),
    DropdownAction(stringResource(Res.string.social_report), isDestructive = true, icon = Icons.Outlined.Flag, onClick = onReport),
)

/** One small detail line under the dates: counts, or when the trip was created. */
@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.skeletonText(),
    )
}

@Composable
private fun TripCover(trip: PublicTrip) {
    val cover = Modifier.size(CoverSize).skeleton(MaterialTheme.shapes.medium).clip(MaterialTheme.shapes.medium)
    val coverId = trip.trip.coverPhotoId
    val coverUrl = trip.trip.coverThumbUrl
    if (coverId != null && coverUrl != null) {
        PhotoImage(photoId = coverId, url = coverUrl, size = PhotoSize.Thumb, contentDescription = null, modifier = cover)
    } else {
        Box(cover.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** As tall as ⋮ (48 dp) above the heart (32 dp). */
private val CoverSize = 80.dp

@Preview
@Composable
private fun PublicTripCardPreview(@PreviewParameter(PublicTripCardPreviewStates::class) trip: PublicTrip) {
    TripinglyTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
            PublicTripCard(trip = trip, onOpen = {}, onLike = {}, onToggleSave = {}, onAskToJoin = {}, onReport = {})
        }
    }
}

@Preview
@Composable
private fun PublicTripCardDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(MaterialTheme.spacing.medium)) {
            PublicTripCard(trip = previewTrip, onOpen = {}, onLike = {}, onToggleSave = {}, onAskToJoin = {}, onReport = {})
        }
    }
}
