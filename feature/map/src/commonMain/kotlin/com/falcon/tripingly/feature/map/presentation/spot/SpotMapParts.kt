package com.falcon.tripingly.feature.map.presentation.spot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AvatarCircle
import com.falcon.tripingly.core.designsystem.component.AvatarSize
import com.falcon.tripingly.core.designsystem.component.GlassButtonDefaults
import com.falcon.tripingly.core.designsystem.component.GlassIconButton
import com.falcon.tripingly.core.designsystem.component.GlassSurface
import com.falcon.tripingly.core.designsystem.component.HotButton
import com.falcon.tripingly.core.designsystem.component.PinMark
import com.falcon.tripingly.core.designsystem.component.Skeleton
import com.falcon.tripingly.core.designsystem.component.compactCount
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.label
import com.falcon.tripingly.core.designsystem.component.skeletonText
import com.falcon.tripingly.core.designsystem.theme.extendedColors
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_added_by_you
import com.falcon.tripingly.feature.map.generated.resources.map_back
import com.falcon.tripingly.feature.map.generated.resources.map_owner_profile
import com.falcon.tripingly.feature.map.generated.resources.map_place_added_by
import com.falcon.tripingly.feature.map.generated.resources.map_spot_card_flames
import com.falcon.tripingly.feature.map.generated.resources.map_spot_card_in_trips
import com.falcon.tripingly.feature.map.generated.resources.map_spot_card_type
import com.falcon.tripingly.feature.map.generated.resources.map_spot_from_osm
import com.falcon.tripingly.feature.map.presentation.map.AddedBy
import com.falcon.tripingly.feature.map.presentation.map.PinAdd
import com.falcon.tripingly.feature.map.presentation.map.ui.CardInset
import com.falcon.tripingly.feature.map.presentation.map.ui.CardTitleRow
import com.falcon.tripingly.feature.map.presentation.map.ui.DetailTile
import com.falcon.tripingly.feature.map.presentation.map.ui.PillGrowthPanel
import com.falcon.tripingly.feature.map.presentation.map.ui.PinCardButtons
import com.falcon.tripingly.feature.map.presentation.map.ui.PlaceFacts
import com.falcon.tripingly.feature.map.presentation.map.ui.CardRowGap
import com.falcon.tripingly.feature.map.presentation.map.ui.TileGap
import com.falcon.tripingly.feature.map.presentation.map.ui.TileValue
import com.falcon.tripingly.feature.map.presentation.map.ui.distanceText
import com.falcon.tripingly.feature.map.presentation.map.ui.label
import com.falcon.tripingly.feature.map.presentation.map.ui.placeButtons
import com.falcon.tripingly.feature.map.presentation.map.ui.rowMotion
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.trip.UserSummary

/*
 * The spot map's own parts (Shahin, 2026-11-23), made of the trip map's: its header (the trip map's back button and
 * title pill, with the spot's flame at the end), the card grown out of the pill (the trip card's growth and rows), and
 * the bottom bar (the pin card's buttons).
 */

/**
 * One row of glass pills of the header's height: back, the spot's pill (the owner's face or the kind's mark, its name,
 * "Attraction · 350 m"), which opens its card ([onTitleClick]; [onTitlePlaced] gives the pill's bounds inside the
 * header's padding, where the card grows out of it; the pill hides meanwhile, [isTitleHidden]), and its flame. As the
 * card grows ([cardProgress], 0 to 1) it pushes back out to the start and the flame out to the end, as on the trip map.
 * [onBackPlaced] gives the back button's bounds on the screen: a touch there leaves the map with any card as it is.
 */
@Composable
internal fun SpotHeader(
    spot: SpotState?,
    onAction: (SpotMapAction) -> Unit,
    modifier: Modifier = Modifier,
    isTitleHidden: Boolean = false,
    onTitleClick: () -> Unit = {},
    onTitlePlaced: (Rect) -> Unit = {},
    onBackPlaced: (Rect) -> Unit = {},
    cardProgress: () -> Float = { 0f },
) {
    // How far past the header's padding the card pushes the buttons: off the screen, or out of the left half.
    val pushedOut = MaterialTheme.spacing.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Only the sides: what the card pushes out goes no further, the buttons' shadows still show.
            .drawWithContent { clipRect(top = -size.height, bottom = size.height * 2) { this@drawWithContent.drawContent() } }
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .height(GlassButtonDefaults.HeaderSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            onClick = { onAction(SpotMapAction.Back) },
            blur = false,
            size = GlassButtonDefaults.HeaderSize,
            modifier = Modifier
                .graphicsLayer { translationX = -cardProgress() * (size.width + pushedOut.toPx()) }
                .onGloballyPositioned { onBackPlaced(Rect(it.positionInRoot(), it.size.toSize())) },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.map_back))
        }
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        GlassSurface(
            modifier = Modifier
                .weight(1f)
                .height(GlassButtonDefaults.HeaderSize)
                .graphicsLayer { alpha = if (isTitleHidden) 0f else 1f }
                .onGloballyPositioned { onTitlePlaced(it.boundsInParent()) }
                .clip(CircleShape)
                .clickable(enabled = spot != null, onClick = onTitleClick),
            shape = CircleShape,
            blur = false,
        ) {
            SpotTitleContent(spot, onOwnerClick = { onAction(SpotMapAction.OpenOwner) })
        }
        // The flame comes once the spot is known, growing in place out of its right middle while the spot's pill gives it
        // room, the gap too (Shahin, 2026-11-24); known as the map opens, it is just there.
        var shownSpot by remember { mutableStateOf(spot) }
        spot?.let { shownSpot = it }
        AnimatedVisibility(
            visible = spot != null,
            enter = expandHorizontally(FlameMotion, expandFrom = Alignment.End, clip = false) +
                scaleIn(FlameScale, transformOrigin = EndMiddle) + fadeIn(FlameScale),
            exit = shrinkHorizontally(FlameMotion, shrinkTowards = Alignment.End, clip = false) +
                scaleOut(FlameScale, transformOrigin = EndMiddle) + fadeOut(FlameScale),
        ) {
            val flameSpot = shownSpot ?: return@AnimatedVisibility
            GlassSurface(
                modifier = Modifier
                    .padding(start = MaterialTheme.spacing.small)
                    .height(GlassButtonDefaults.HeaderSize)
                    .graphicsLayer { translationX = cardProgress() * (size.width + pushedOut.toPx()) },
                shape = CircleShape,
                blur = false,
            ) {
                HotButton(
                    hot = flameSpot.isHot,
                    count = flameSpot.hotCount,
                    onToggle = { onAction(SpotMapAction.ToggleHot) },
                    modifier = Modifier.fillMaxHeight(),
                    tint = if (flameSpot.isHot) MaterialTheme.extendedColors.attention else MaterialTheme.colorScheme.onSurface,
                    contentPadding = PaddingValues(horizontal = HotPillInset),
                )
            }
        }
    }
}

/**
 * What the spot's pill holds, without the pill: also drawn by the card where it grows out of the pill, there without
 * [onOwnerClick] (null: the face isn't a button). Shimmers while the spot loads.
 */
@Composable
internal fun SpotTitleContent(spot: SpotState?, modifier: Modifier = Modifier, onOwnerClick: (() -> Unit)? = null) {
    // The face sits as far in from the pill's edge as from its top, like the buttons' icons.
    val inset = (GlassButtonDefaults.HeaderSize - AvatarSize.Row) / 2
    Row(
        modifier = modifier.fillMaxHeight().padding(start = inset, end = MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spot == null) {
            // Placeholder lengths only: drawn as shimmering shapes, never as text.
            Skeleton(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(name = "A", photo = null, size = AvatarSize.Row)
                    TitleLines("A spot's name", "Attraction · 350 m")
                }
            }
        } else {
            SpotFace(spot, AvatarSize.Row, onOwnerClick)
            TitleLines(spot.name, spot.subtitle(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TitleLines(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.skeletonText())
    }
}

/** "Attraction · 350 m", or only its kind until where I am is known. */
@Composable
private fun SpotState.subtitle(): String = listOfNotNull(category.label(), distanceMeters?.let { distanceText(it).asString() }).joinToString(" · ")

/** The owner's face, which opens their profile ([onOwnerClick]), or the kind's mark for a spot from OpenStreetMap. */
@Composable
private fun SpotFace(spot: SpotState, size: Dp, onOwnerClick: (() -> Unit)?) {
    val owner = spot.owner
    if (owner != null) {
        val profile = stringResource(Res.string.map_owner_profile, owner.username)
        AvatarCircle(
            user = owner,
            size = size,
            modifier = if (onOwnerClick != null) Modifier.clip(CircleShape).clickable(onClick = onOwnerClick).semantics { contentDescription = profile } else Modifier,
        )
    } else {
        PinMark(spot.category, isStop = false, modifier = Modifier.size(size))
    }
}

/**
 * The spot's card over the map, grown out of the pill at [pill] by [progress] of [transition], as the trip card is: its
 * face and whole name with who added it (or "From OpenStreetMap"), the address, and its kind, flames and the trips it
 * is on as tiles. [onClose]: the close button.
 */
@Composable
internal fun SpotCard(
    spot: SpotState,
    transition: Transition<Boolean>,
    progress: () -> Float,
    pill: Rect,
    onClose: () -> Unit,
    onOwnerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PillGrowthPanel(
        transition = transition,
        progress = progress,
        pill = pill,
        pillContent = { SpotTitleContent(spot) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(CardInset),
            verticalArrangement = Arrangement.spacedBy(CardRowGap),
        ) {
            val owner = spot.owner
            CardTitleRow(
                title = spot.name,
                byline = when {
                    spot.isOwnerMe -> stringResource(Res.string.map_added_by_you)
                    owner != null -> stringResource(Res.string.map_place_added_by, owner.username)
                    spot.isFromOsm -> stringResource(Res.string.map_spot_from_osm)
                    else -> null
                },
                onClose = onClose,
                modifier = transition.rowMotion(0),
            ) {
                SpotFace(spot, AvatarSize.Medium, onOwnerClick)
            }
            var row = 1
            spot.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = transition.rowMotion(row++))
            }
            Row(modifier = transition.rowMotion(row).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                DetailTile(stringResource(Res.string.map_spot_card_type), Modifier.weight(1f)) { TileValue(spot.category.label(), spot.category.icon()) }
                DetailTile(stringResource(Res.string.map_spot_card_flames)) { TileValue(compactCount(spot.hotCount), Icons.Outlined.LocalFireDepartment) }
                DetailTile(stringResource(Res.string.map_spot_card_in_trips)) { TileValue(compactCount(spot.tripCount), Icons.Outlined.Luggage) }
            }
        }
    }
}

/**
 * The bottom bar, in the day tabs' place: the spot's buttons as on a pin card, its photos, Go here and Around on the
 * left and the green + onto one of my trips on the right; under them what its details sheet would say (who made it,
 * its opening hours, its website), so there is no ⓘ (Shahin, 2026-11-24).
 */
@Composable
internal fun SpotBar(spot: SpotState, onAction: (SpotMapAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        PinCardButtons(
            group = placeButtons(
                hasPhotos = true,
                onOpenPhotos = { onAction(SpotMapAction.OpenPhotos) },
                routes = spot.routes,
                onGoHere = { onAction(SpotMapAction.GoHere) },
                onRoute = null,
                onAround = { onAction(SpotMapAction.Around) },
            ),
            onDetails = null,
            onDelete = null,
            onAdd = { onAction(SpotMapAction.Add) },
            addLabel = PinAdd.ToMyTrip.label(),
            modifier = Modifier.padding(MaterialTheme.spacing.small),
        )
        Column(Modifier.padding(horizontal = MaterialTheme.spacing.medium)) {
            PlaceFacts(
                addedBy = spot.owner?.let { AddedBy(it, isMe = spot.isOwnerMe) },
                openingHours = spot.openingHours,
                website = spot.website,
                onOpenProfile = { onAction(SpotMapAction.OpenOwner) },
            )
        }
    }
}

/** The flame growing in: its room and itself together, the app's emphasized 300 ms. */
private val FlameMotion = tween<IntSize>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
private val FlameScale = tween<Float>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
private val EndMiddle = TransformOrigin(1f, 0.5f)

/** The flame pill's sides: its flame as far in as the back button's arrow. */
private val HotPillInset = 18.dp

private val PreviewOsmSpot = SpotState(
    id = "p-1",
    name = "Café Central",
    category = PlaceCategory("cafe"),
    owner = null,
    isOwnerMe = false,
    isFromOsm = true,
    address = "Herrengasse 14, Wien",
    distanceMeters = 650,
    isHot = true,
    hotCount = 42,
    tripCount = 7,
    openingHours = "Mo-Sa 08:00-21:00",
    website = "cafecentral.wien",
)

private val PreviewUserSpot = PreviewOsmSpot.copy(
    id = "p-2",
    name = "Our picnic corner on the Danube Island",
    category = PlaceCategory("park"),
    owner = UserSummary(id = "u2", username = "anna", displayName = "Anna Kovács"),
    isFromOsm = false,
    address = null,
    distanceMeters = null,
    isHot = false,
    hotCount = 3,
    tripCount = 1,
    openingHours = null,
    website = null,
)

/** The header for a place from OpenStreetMap, someone's own spot and while loading; the card; the bottom bar. */
@Composable
private fun SpotPartsPreviewContent() {
    val transition = rememberTransition(remember { MutableTransitionState(true) })
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        SpotHeader(spot = PreviewOsmSpot, onAction = {})
        SpotHeader(spot = PreviewUserSpot, onAction = {})
        SpotHeader(spot = null, onAction = {})
        SpotCard(
            spot = PreviewUserSpot,
            transition = transition,
            progress = { 1f },
            pill = Rect.Zero,
            onClose = {},
            onOwnerClick = {},
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
        )
        SpotBar(PreviewOsmSpot, onAction = {})
    }
}

@Preview(widthDp = 390)
@Composable
private fun SpotPartsPreview() {
    TripinglyTheme { SpotPartsPreviewContent() }
}

@Preview(widthDp = 390)
@Composable
private fun SpotPartsDarkPreview() {
    TripinglyTheme(darkTheme = true) { SpotPartsPreviewContent() }
}
