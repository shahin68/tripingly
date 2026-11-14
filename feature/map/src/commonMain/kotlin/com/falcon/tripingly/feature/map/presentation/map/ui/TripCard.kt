package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.NewIndicator
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.component.onTopEndCorner
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_close
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_by
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_dates
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_likes
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_members
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_new_requests
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_no_dates
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_private
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_public
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_saves
import com.falcon.tripingly.feature.map.generated.resources.map_trip_card_visibility
import com.falcon.tripingly.feature.map.presentation.map.HeaderState
import com.falcon.tripingly.feature.map.presentation.map.TripCardState
import com.falcon.tripingly.feature.map.presentation.map.preview.MapHeaderPreviewStates
import org.jetbrains.compose.resources.stringResource

/*
 * The trip card grows out of the title pill ([PillGrowthPanel]). Design: the "Final" board of the trip card canvas
 * (Shahin, 2026-11-08).
 */

/**
 * The trip card over the map, grown out of the title pill at [pill] (its bounds in the same coordinates as this card)
 * by [progress] of [transition]: the owner (whose face opens their profile, [onOwnerClick]) and the whole name, the
 * details as tiles, and the members strip, which opens the members sheet ([onMembersClick]) and has a red dot while
 * the owner hasn't seen new requests to join. [onClose]: the close button. It wraps its content; a long name scrolls
 * where the screen is short.
 */
@Composable
internal fun TripCard(
    header: HeaderState,
    transition: Transition<Boolean>,
    progress: () -> Float,
    pill: Rect,
    onClose: () -> Unit,
    onOwnerClick: () -> Unit,
    onMembersClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PillGrowthPanel(
        transition = transition,
        progress = progress,
        pill = pill,
        pillContent = { TripTitleContent(header = header) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CardInset),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            OwnerRow(header, onOwnerClick, onClose, transition.rowMotion(0))
            DetailTiles(header.card, transition.rowMotion(1))
            if (header.card.memberCount > 0) MembersStrip(header.card, onMembersClick, transition.rowMotion(2))
        }
    }
}

/** The dimmed map behind the open card, following [progress]; a tap on it closes the card ([onClose]). */
@Composable
internal fun TripCardScrim(progress: () -> Float, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress() }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = null, indication = null, onClick = onClose),
    )
}

/** The owner's face beside the whole name and "by Shahin", their tops in line, and the close button at the end. */
@Composable
private fun OwnerRow(header: HeaderState, onOwnerClick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val owner = header.owner
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TitleGap)) {
        if (owner != null) {
            val profile = stringResource(Res.string.map_owner_profile, owner.username)
            AvatarCircle(
                user = owner,
                size = AvatarSize.Medium,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onOwnerClick).semantics { contentDescription = profile },
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(header.tripName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            owner?.let {
                Text(stringResource(Res.string.map_trip_card_by, it.displayName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            modifier = Modifier
                .size(CloseSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.map_close), modifier = Modifier.size(16.dp))
        }
    }
}

/** Dates, likes, saves and who sees the trip, each on a small tile; Dates gets what the others leave. */
@Composable
private fun DetailTiles(card: TripCardState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        DetailTile(stringResource(Res.string.map_trip_card_dates), Modifier.weight(1f)) {
            TileValue(card.dates ?: stringResource(Res.string.map_trip_card_no_dates))
        }
        DetailTile(stringResource(Res.string.map_trip_card_likes)) { TileValue(compactCount(card.likeCount), Icons.Outlined.FavoriteBorder) }
        DetailTile(stringResource(Res.string.map_trip_card_saves)) { TileValue(compactCount(card.saveCount), Icons.Outlined.BookmarkBorder) }
        DetailTile(stringResource(Res.string.map_trip_card_visibility)) {
            TileValue(
                text = stringResource(if (card.isPublic) Res.string.map_trip_card_public else Res.string.map_trip_card_private),
                icon = if (card.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DetailTile(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), TileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        value()
    }
}

@Composable
private fun TileValue(text: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Up to three faces (the owner first), "and 2 more" (or "Only Shahin" on a one-person trip) and "Members ›"; the whole
 * strip opens the members sheet. A red dot on its top end corner, like the day tabs': new requests to join.
 */
@Composable
private fun MembersStrip(card: TripCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val newRequests = stringResource(Res.string.map_trip_card_new_requests)
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StripHeight)
                .clip(StripShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { if (card.hasNewJoinRequests) contentDescription = newRequests }
                .padding(start = 6.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(-FaceOverlap)) {
                card.faces.forEach { face ->
                    AvatarCircle(
                        user = face,
                        size = FaceSize,
                        modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
            }
            Text(
                card.membersNote?.asString().orEmpty(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.map_trip_card_members), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
        if (card.hasNewJoinRequests) {
            // Centred on the rounded corner's curve, as on the day tabs.
            NewIndicator(Modifier.align(Alignment.TopEnd).onTopEndCorner(StripRadius), strokeColor = GlassDefaults.opaqueTint)
        }
    }
}

private const val SCRIM_ALPHA = 0.24f

/** The same inset for the owner's face and the close button, and every row. */
private val CardInset = 10.dp
private val RowGap = 12.dp
private val TitleGap = 12.dp
private val CloseSize = 36.dp
private val TileGap = 6.dp
private val TileShape = RoundedCornerShape(16.dp)
private val StripRadius = 22.dp
private val StripShape = RoundedCornerShape(StripRadius)
private val StripHeight = 56.dp
private val FaceSize = 44.dp
private val FaceOverlap = 12.dp


@Composable
private fun TripCardPreviewContent(header: HeaderState) {
    val transition = rememberTransition(remember { MutableTransitionState(true) })
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp)) {
        TripCard(header = header, transition = transition, progress = { 1f }, pill = Rect.Zero, onClose = {}, onOwnerClick = {}, onMembersClick = {})
    }
}

@Preview
@Composable
private fun TripCardPreview() {
    TripinglyTheme { TripCardPreviewContent(MapHeaderPreviewStates().values.first().header) }
}

@Preview
@Composable
private fun TripCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { TripCardPreviewContent(MapHeaderPreviewStates().values.elementAt(1).header) }
}
