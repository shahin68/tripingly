package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.HeartIndicator
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import org.jetbrains.compose.resources.stringResource

/**
 * The trip's owner as an avatar, which opens their profile ([onOwnerClick]), and the trip's name on one line,
 * centred. A tap on the rest opens the trip card with the whole name ([onClick]).
 */
@Composable
internal fun TripTitle(
    header: HeaderState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onOwnerClick: () -> Unit = {},
) {
    GlassSurface(
        modifier = modifier.height(GlassButtonDefaults.HeaderSize).clip(CircleShape).clickable(enabled = !header.isLoading, onClick = onClick),
        shape = CircleShape,
        blur = false,
    ) {
        TripTitleContent(header, onOwnerClick = onOwnerClick)
    }
}

/**
 * What the title pill holds, without the pill: also drawn by the trip card where it grows out of the pill, there
 * without [onOwnerClick] (null: the avatar isn't a button).
 */
@Composable
internal fun TripTitleContent(header: HeaderState, modifier: Modifier = Modifier, onOwnerClick: (() -> Unit)? = null) {
    // The avatar sits as far in from the pill's edge as from its top, like the buttons' icons.
    val inset = (GlassButtonDefaults.HeaderSize - AvatarSize.Row) / 2
    Row(
        modifier = modifier.fillMaxHeight().padding(start = inset, end = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val owner = header.owner
        if (header.isLoading || owner == null) {
            // Placeholder lengths only: drawn as shimmering shapes, never as text.
            Skeleton(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(name = "A", photo = null, size = AvatarSize.Row)
                    TitleLine("A trip's name")
                }
            }
        } else {
            val profile = stringResource(Res.string.map_owner_profile, owner.username)
            AvatarCircle(
                user = owner,
                size = AvatarSize.Row,
                modifier = if (onOwnerClick != null) {
                    Modifier.clip(CircleShape).clickable(onClick = onOwnerClick).semantics { contentDescription = profile }
                } else {
                    Modifier
                },
            )
            TitleLine(header.tripName, Modifier.weight(1f))
        }
        if (header.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun TitleLine(name: String, modifier: Modifier = Modifier) {
    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.skeletonText())
}

/**
 * The heart on someone else's trip, floating on the title pill's bottom-end corner (Shahin, 2026-11-19): set it at
 * that corner of the pill ([Alignment.BottomEnd]) and it hangs a little below and past it, as he placed it on the
 * canvas. Ringed in the pill's own glass. [enabled] false: the copy riding the trip card's corner, which only shows.
 */
@Composable
internal fun TripHeart(liked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    HeartIndicator(
        liked = liked,
        onToggle = onToggle,
        ringColor = GlassDefaults.opaqueTint,
        enabled = enabled,
        modifier = modifier.offset(x = TripHeartPastEnd, y = TripHeartBelow),
    )
}

/** How far the heart's tap box reaches past the pill's end and below its bottom; the heart itself ends 4 dp inside the end. */
internal val TripHeartPastEnd = 2.dp
internal val TripHeartBelow = 15.dp
