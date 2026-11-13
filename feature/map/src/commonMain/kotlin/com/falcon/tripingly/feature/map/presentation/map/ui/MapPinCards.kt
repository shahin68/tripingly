package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.component.stopPinRadius
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.PlaceCardState
import com.falcon.tripingly.feature.map.presentation.map.StopCardState

/**
 * The tapped place's and stop's cards over the part of the map nothing covers (its middle is where the camera puts a
 * tapped pin, [pinBelowMiddle] lower in landscape). Each reports where it is, so a touch on it doesn't close it.
 */
@Composable
internal fun MapPinCards(
    placeCard: PlaceCardState?,
    stopCard: StopCardState?,
    pinBelowMiddle: Dp,
    onAction: (MapAction) -> Unit,
    onPlaceCardBounds: (Rect) -> Unit,
    onStopCardBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        PinCard(
            card = placeCard,
            key = { it.place.id to it.place.location },
            pinClearance = SelectedPinSize / 2 + MaterialTheme.spacing.small,
            pinBelowMiddle = pinBelowMiddle,
            modifier = Modifier.onGloballyPositioned { onPlaceCardBounds(it.unclippedBounds()) },
        ) { card ->
            PlaceCard(
                place = card.place,
                details = card.details,
                address = card.address,
                add = card.add,
                addedBy = card.addedBy,
                onAdd = { onAction(MapAction.PlaceCard.Add) },
                onOpenImage = { index -> onAction(MapAction.PlaceCard.OpenImage(index)) },
                onOpenPhotos = { onAction(MapAction.PlaceCard.OpenPhotos) },
                onOpenDetails = { onAction(MapAction.PlaceCard.OpenDetails) },
                routes = card.routes,
                onGoHere = { onAction(MapAction.PinRoute.GoHere) },
                onRoute = { onAction(MapAction.PinRoute.Route) },
            )
        }
        PinCard(
            card = stopCard,
            key = { it.stop.id },
            pinClearance = stopPinRadius(hasCover = stopCard?.stop?.coverPhotoId != null) + MaterialTheme.spacing.small,
            pinBelowMiddle = pinBelowMiddle,
            modifier = Modifier.onGloballyPositioned { onStopCardBounds(it.unclippedBounds()) },
        ) { card ->
            StopCard(
                card = card,
                onToggleHot = { onAction(MapAction.StopCard.ToggleHot(card.stop.id)) },
                onOpenComments = { onAction(MapAction.StopCard.OpenComments) },
                onOpenPhotos = { onAction(MapAction.StopCard.OpenPhotos) },
                onOpenDetails = { onAction(MapAction.StopCard.OpenDetails) },
                onDelete = { onAction(MapAction.Plan.RemoveStop(card.stop.id)) },
                onAdd = { onAction(MapAction.StopCard.Add) },
                onGoHere = { onAction(MapAction.PinRoute.GoHere) },
                onRoute = { onAction(MapAction.PinRoute.Route) },
            )
        }
    }
}

/**
 * Where a pin card really is. Not boundsInRoot: that cuts off what lies outside its parents, and a card is lifted
 * above the pin, out of its own layout, so touches on its top half counted as touches on the map and closed it.
 */
private fun LayoutCoordinates.unclippedBounds() = Rect(positionInRoot(), size.toSize())
