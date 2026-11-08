package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassDefaults
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.theme.MapColors
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.extendedColors
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
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/*
 * The trip card grows out of the title pill in the same window, no Dialog and no shared bounds (both broke on iOS).
 * One transition drives everything: the card's shape (from the pill's rectangle to the whole card, corners kept
 * round), the header's back and ⋮ pushed out, and the three rows sliding down into place. The card is laid out once at
 * its full size, so every frame only draws again, the same on Android and iOS. Design: the "Final" board of the trip
 * card canvas (Shahin, 2026-11-08).
 */

/** The card's growth: 0 is the title pill, 1 the open card. It slows down towards the end both ways. */
@Composable
internal fun Transition<Boolean>.animateTripCard(): State<Float> = animateFloat(
    transitionSpec = { tween(if (false isTransitioningTo true) OPEN_MILLIS else CLOSE_MILLIS, easing = Decelerate) },
    label = "trip card",
) { open -> if (open) 1f else 0f }

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
    val tint = GlassDefaults.opaqueTint
    val edge = MaterialTheme.extendedColors.glassEdge
    val pillAlpha by transition.animateFloat(
        transitionSpec = { if (false isTransitioningTo true) tween(PILL_FADE_OUT_MILLIS, easing = LinearEasing) else tween(PILL_FADE_IN_MILLIS, PILL_FADE_IN_DELAY_MILLIS, LinearEasing) },
        label = "pill",
    ) { open -> if (open) 0f else 1f }
    val pillSize = with(LocalDensity.current) { pill.width.toDp() to pill.height.toDp() }
    Box(
        modifier = modifier
            .graphicsLayer {
                shape = GrowingShape(pill, progress())
                clip = true
                shadowElevation = GlassDefaults.ShadowElevation.toPx()
                ambientShadowColor = GlassDefaults.Shadow
                spotShadowColor = GlassDefaults.Shadow
            }
            .drawBehind {
                val outline = GrowingShape(pill, progress()).createOutline(size, layoutDirection, this)
                drawOutline(outline, tint)
                drawOutline(outline, edge, style = Stroke(GlassDefaults.Edge.toPx()))
            }
            // Taps on the card stay on it: only a tap outside closes it.
            .clickable(interactionSource = null, indication = null) {},
    ) {
        // Drawn by hand rather than with GlassSurface, so it sets the glass's content color itself.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CardInset),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                OwnerRow(header, onOwnerClick, onClose, transition.rowMotion(0))
                DetailTiles(header.card, transition.rowMotion(1))
                if (header.card.memberCount > 0) MembersStrip(header.card, onMembersClick, transition.rowMotion(2))
            }
            // The pill's own face and name, fading as the card grows over them and back as it shrinks into the pill.
            TripTitleContent(
                header = header,
                modifier = Modifier
                    .offset { IntOffset(pill.left.roundToInt(), pill.top.roundToInt()) }
                    .size(pillSize.first, pillSize.second)
                    .graphicsLayer { alpha = pillAlpha },
            )
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

/**
 * A row of the card: opening, it fades in and slides down from above (each row from further up, so they land one after
 * another); closing, it only fades out, quickly, and goes back up once it's gone.
 */
@Composable
private fun Transition<Boolean>.rowMotion(index: Int): Modifier {
    val alpha by animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(ROW_FADE_IN_MILLIS, ROW_FADE_IN_DELAY_MILLIS + index * ROW_STAGGER_MILLIS, LinearEasing)
            } else {
                tween(ROW_FADE_OUT_MILLIS, easing = LinearEasing)
            }
        },
        label = "row $index",
    ) { open -> if (open) 1f else 0f }
    val lift by animateFloat(
        transitionSpec = { if (false isTransitioningTo true) tween(ROW_SLIDE_MILLIS, easing = SlideIn) else snap(CLOSE_MILLIS) },
        label = "row $index lift",
    ) { open -> if (open) 0f else 1f }
    val distance = RowSlide * (index + 1)
    return Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = -lift * distance.toPx()
    }
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
            val onCorner = NewDotSize / 2 - StripRadius * (1 - CORNER_CURVE_POINT)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = onCorner, y = -onCorner)
                    .size(NewDotSize)
                    .background(MapColors.NewActivity, CircleShape),
            )
        }
    }
}

/**
 * The card's outline at [progress]: the pill's rectangle at 0, the whole card at 1, every edge moving at once; the
 * corners stay as round as the pill's.
 */
private class GrowingShape(private val pill: Rect, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(
            left = lerp(pill.left, 0f, progress),
            top = lerp(pill.top, 0f, progress),
            right = lerp(pill.right, size.width, progress),
            bottom = lerp(pill.bottom, size.height, progress),
        )
        val radius = with(density) { CardRadius.toPx() }.coerceAtMost(rect.height / 2)
        return Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
    }
}

/** Material's emphasized decelerate: fast at first, easing into place. */
private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Softer than the card's, so the rows' slide is still seen once the card is nearly open. */
private val SlideIn = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val OPEN_MILLIS = 400
private const val CLOSE_MILLIS = 320
private const val PILL_FADE_OUT_MILLIS = 100
private const val PILL_FADE_IN_MILLIS = 150

/** The pill's face and name come back only once the rows are gone, so nothing shows through them. */
private const val PILL_FADE_IN_DELAY_MILLIS = 170
private const val ROW_FADE_IN_MILLIS = 260
private const val ROW_FADE_IN_DELAY_MILLIS = 60
private const val ROW_STAGGER_MILLIS = 30
private const val ROW_FADE_OUT_MILLIS = 90
private const val ROW_SLIDE_MILLIS = 460
private const val SCRIM_ALPHA = 0.24f

/** How far above its place each row starts, times its position (24, 48, 72 dp). */
private val RowSlide = 24.dp

/** The pill's corners, kept by the card. */
private val CardRadius = GlassButtonDefaults.HeaderSize / 2

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
private val NewDotSize = 10.dp

/** How far round a corner's curve its middle point lies from the corner's edges: cos 45°. */
private const val CORNER_CURVE_POINT = 0.7071f

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
