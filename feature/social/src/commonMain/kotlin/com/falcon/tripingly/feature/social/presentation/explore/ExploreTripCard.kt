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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.common.util.DateUtils
import com.falcon.tripingly.core.designsystem.component.AppDropdownMenu
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.CountLabel
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.LikeButton
import com.falcon.tripingly.core.designsystem.component.LikeButtonHeight
import com.falcon.tripingly.core.designsystem.component.PhotoImage
import com.falcon.tripingly.core.designsystem.component.PhotoSize
import com.falcon.tripingly.core.designsystem.component.skeleton
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.label
import com.falcon.tripingly.core.ui.tripWhen
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_more
import com.falcon.tripingly.feature.social.presentation.component.previewTrip
import com.falcon.tripingly.feature.social.presentation.component.publicTripActions
import org.jetbrains.compose.resources.stringResource

/**
 * A public trip in Explore's grid: its cover filling the card, the owner (avatar and @username, opens their profile)
 * and the likes on top, the name and when it is ("In a week", "This winter") over the bottom. ⋮ or a long press opens
 * the trip's menu (save, ask to join, report); trips of my own have no menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExploreTripCard(
    trip: PublicTrip,
    onOpen: () -> Unit,
    onOpenOwner: () -> Unit,
    onLike: () -> Unit,
    onToggleSave: () -> Unit,
    onAskToJoin: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMine = trip.trip.role != TripRole.VIEWER
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ExploreCardHeight)
            // A photo card: while loading, the whole card is one shimmering block.
            .skeleton(MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
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
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(CardInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnerChip(trip.trip.owner, onOpenOwner, Modifier.weight(1f, fill = false).padding(end = MaterialTheme.spacing.small))
            GlassSurface(shape = CircleShape, shadowElevation = 0.dp, blur = false) {
                if (isMine) {
                    // My own trip: its likes are something to read, not to give.
                    CountLabel(
                        icon = Icons.Outlined.FavoriteBorder,
                        count = trip.likeCount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(LikeButtonHeight).padding(horizontal = MaterialTheme.spacing.small),
                    )
                } else {
                    LikeButton(liked = trip.likedByMe, count = trip.likeCount, onToggle = onLike)
                }
            }
        }
        CardFooter(
            trip = trip,
            modifier = Modifier.align(Alignment.BottomStart),
            menu = if (isMine) {
                null
            } else {
                {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.social_more), tint = Color.White)
                        }
                        AppDropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            actions = publicTripActions(trip, onToggleSave, onAskToJoin, onReport),
                        )
                    }
                }
            },
        )
    }
}

/** The owner's avatar, as tall as the like button (just the avatar: the card stays calm); it opens their profile. */
@Composable
private fun OwnerChip(owner: UserSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AvatarCircle(
        owner,
        size = LikeButtonHeight,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "@${owner.username}", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "@${owner.username}" },
    )
}

/** The name and when the trip is over a dark fade, so they read on any photo; [menu] (the ⋮) at the end. */
@Composable
private fun CardFooter(trip: PublicTrip, modifier: Modifier = Modifier, menu: (@Composable () -> Unit)?) {
    val whenLabel = tripWhen(trip.trip.startDate, trip.trip.endDate, remember { DateUtils.today() })?.label()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_ALPHA))))
            .padding(start = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall, top = MaterialTheme.spacing.large),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f).padding(bottom = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall)) {
            Text(trip.trip.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (whenLabel != null) {
                Text(
                    text = whenLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = MUTED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (menu != null) menu() else Box(Modifier.width(MaterialTheme.spacing.small))
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
        ExploreTripCard(previewTrip, {}, {}, {}, {}, {}, {}, Modifier.weight(1f))
        ExploreTripCard(previewTrip.copy(likeCount = 12_400, likedByMe = false), {}, {}, {}, {}, {}, {}, Modifier.weight(1f))
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
